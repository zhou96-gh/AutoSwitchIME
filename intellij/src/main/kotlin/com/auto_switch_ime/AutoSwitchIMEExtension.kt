package com.auto_switch_ime

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.maddyhome.idea.vim.api.VimEditor
import com.maddyhome.idea.vim.api.injector
import com.maddyhome.idea.vim.common.ModeChangeListener
import com.maddyhome.idea.vim.extension.VimExtension
import com.maddyhome.idea.vim.newapi.ij
import com.maddyhome.idea.vim.state.mode.Mode
import com.auto_switch_ime.caret.CaretColorManager
import com.auto_switch_ime.core.ImeAction
import com.auto_switch_ime.core.ime.ImeStateDetector
import com.auto_switch_ime.core.rules.RuleEvaluator
import com.auto_switch_ime.ime.AutoSwitchIMEController
import com.auto_switch_ime.settings.AutoSwitchIMESettings
import com.auto_switch_ime.util.ActionDeduplicator
import com.auto_switch_ime.util.AutoSwitchIMELogger

/**
 * IdeaVim 扩展：监听 Vim 模式变化，自动切换输入法并更新光标颜色
 *
 * 模式切换规则：
 * - Normal/Visual/Select/OP_PENDING → 强制英文（ASCII）
 * - Command-line (:) → 不变（保持当前 IME 状态）
 * - Insert/Replace → 评估正则规则：中文规则→中文, 大写规则→大写, 默认→英文
 */
class AutoSwitchIMEExtension : VimExtension, ModeChangeListener {

    private val controller: AutoSwitchIMEController by lazy {
        ApplicationManager.getApplication().getService(AutoSwitchIMEController::class.java)
    }

    override fun getName(): String = "auto_switch_ime"

    override fun init() {
        val settings = AutoSwitchIMESettings.instance
        AutoSwitchIMELogger.info("init() called, enabled=${settings.enabled}, log=[E:${settings.logError} W:${settings.logWarn} I:${settings.logInfo} D:${settings.logDebug}]")
        AutoSwitchIMELogger.info("WeaselServer path: ${controller.resolvePath() ?: "(not found)"}")

        if (!settings.enabled) {
            AutoSwitchIMELogger.info("AutoSwitchIME IME is disabled")
            return
        }

        AutoSwitchIMELogger.info("AutoSwitchIME IME extension initialized")

        // 初始化：检测当前 IME 状态并更新所有编辑器的光标颜色
        initializeImeState()

        // 注册模式变化监听器
        injector.listenersNotifier.modeChangeListeners.add(this)
        AutoSwitchIMELogger.info("ModeChangeListener registered")
    }

    /**
     * 初始化 IME 状态：检测当前输入法状态并更新所有编辑器的光标颜色
     */
    private fun initializeImeState() {
        try {
            val state = ImeStateDetector.getCurrentState(controller.stateWatcher, controller.getTrackedState())
            AutoSwitchIMELogger.info("Initializing IME state: ascii=${state.isAsciiMode}, caps=${state.isCapsLock}")

            val editors = EditorFactory.getInstance().allEditors
            for (editor in editors) {
                if (!editor.isDisposed) {
                    CaretColorManager.updateCaretColor(editor, state.isAsciiMode, state.isCapsLock)
                    val fileName = FileDocumentManager.getInstance().getFile(editor.document)?.name ?: "(unnamed)"
                    AutoSwitchIMELogger.debug("Initialized caret color for editor: $fileName")
                }
            }

            if (editors.isEmpty()) {
                AutoSwitchIMELogger.debug("No editors available during initialization, will initialize on editor creation")
            } else {
                AutoSwitchIMELogger.info("Initialized caret color for ${editors.size} editor(s)")
            }
        } catch (e: Exception) {
            AutoSwitchIMELogger.warn("Failed to initialize IME state", e)
        }
    }

    override fun modeChanged(editor: VimEditor, oldMode: Mode) {
        if (!AutoSwitchIMESettings.instance.enabled) return

        val currentMode = editor.mode
        AutoSwitchIMELogger.info("modeChanged: $oldMode → $currentMode")

        ApplicationManager.getApplication().invokeLater {
            val ijEditor = editor.ij
            if (ijEditor.isDisposed) {
                AutoSwitchIMELogger.debug("Editor disposed, skipping")
                return@invokeLater
            }

            if (!ijEditor.contentComponent.hasFocus()) {
                AutoSwitchIMELogger.debug("AutoSwitchIMEExtension: editor not focused, skipping IME switch")
                return@invokeLater
            }

            if (!controller.getTrackedState().isAsciiMode) {
                val isComposing = ImeStateDetector.isComposing(controller.stateWatcher)
                if (isComposing) {
                    AutoSwitchIMELogger.info("AutoSwitchIMEExtension: Rime is composing, skipping IME switch")
                    val state = controller.getTrackedState()
                    CaretColorManager.updateCaretColor(ijEditor, state.isAsciiMode, state.isCapsLock)
                    return@invokeLater
                }
            }

            when (currentMode) {
                is Mode.NORMAL, is Mode.VISUAL, is Mode.SELECT, is Mode.OP_PENDING -> {
                    if (ActionDeduplicator.shouldSkip(ijEditor, ImeAction.ENGLISH)) {
                        AutoSwitchIMELogger.debug("AutoSwitchIMEExtension: duplicated English action skipped")
                        return@invokeLater
                    }
                    AutoSwitchIMELogger.info("Normal mode → forcing ASCII (English)")
                    controller.setAsciiMode(true)
                    CaretColorManager.updateCaretColor(ijEditor, true, false)
                }

                is Mode.CMD_LINE -> {
                    AutoSwitchIMELogger.debug("Command-line mode → IME unchanged")
                }

                is Mode.INSERT, is Mode.REPLACE -> {
                    val (before, after) = getLineContextText(ijEditor)
                    AutoSwitchIMELogger.info("Insert context: before='$before', after='$after'")
                    val settings = AutoSwitchIMESettings.instance
                    val action = evaluateInsertModeRules(before, after, settings)
                    if (ActionDeduplicator.shouldSkip(ijEditor, action)) {
                        AutoSwitchIMELogger.debug("AutoSwitchIMEExtension: duplicated $action action skipped")
                        return@invokeLater
                    }

                    when (action) {
                        ImeAction.CHINESE -> {
                            AutoSwitchIMELogger.info("Insert mode → Chinese (regex matched)")
                            controller.setAsciiMode(false)
                            CaretColorManager.updateCaretColor(ijEditor, false, false)
                        }
                        ImeAction.CAPS -> {
                            AutoSwitchIMELogger.info("Insert mode → Caps (regex matched)")
                            controller.setCapsMode()
                            CaretColorManager.updateCaretColor(ijEditor, true, true)
                        }
                        ImeAction.ENGLISH -> {
                            AutoSwitchIMELogger.info("Insert mode → English (default)")
                            controller.setAsciiMode(true)
                            CaretColorManager.updateCaretColor(ijEditor, true, false)
                        }
                        ImeAction.UNCHANGED -> {
                            AutoSwitchIMELogger.debug("Insert mode → IME unchanged")
                        }
                    }
                }
            }
        }
    }

    /**
     * 获取光标所在行的上下文文本（不跨行）
     * @return Pair(光标前文本，光标后文本)
     */
    private fun getLineContextText(editor: com.intellij.openapi.editor.Editor): Pair<String, String> {
        return runReadActionBlocking {
            try {
                val document = editor.document
                val caretOffset = editor.caretModel.primaryCaret.offset
                val lineNumber = document.getLineNumber(caretOffset)
                val lineStart = document.getLineStartOffset(lineNumber)
                val lineEnd = document.getLineEndOffset(lineNumber)

                val beforeStart = maxOf(lineStart, caretOffset - 5)
                val afterEnd = minOf(lineEnd, caretOffset + 5)
                val before = document.getText(com.intellij.openapi.util.TextRange(beforeStart, caretOffset))
                val after = document.getText(com.intellij.openapi.util.TextRange(caretOffset, afterEnd))
                Pair(before, after)
            } catch (e: Exception) {
                AutoSwitchIMELogger.warn("Failed to get line context text", e)
                Pair("", "")
            }
        }
    }

    /**
     * 根据配置的正则规则评估 Insert 模式下的输入法状态
     * - 中文规则：光标前或光标后任一匹配时切换为中文
     * - 大写规则：光标前或光标后任一匹配时切换为大写
     * 优先级：中文规则 → 大写规则 → 默认英文
     */
    private fun evaluateInsertModeRules(
        before: String,
        after: String,
        settings: AutoSwitchIMESettings
    ): ImeAction {
        return RuleEvaluator.evaluate(
            before = before,
            after = after,
            chineseBeforeRegex = settings.insertModeChineseBeforeRegex,
            chineseAfterRegex = settings.insertModeChineseAfterRegex,
            capsBeforeRegex = settings.insertModeCapsBeforeRegex,
            capsAfterRegex = settings.insertModeCapsAfterRegex
        )
    }

    override fun dispose() {
        injector.listenersNotifier.modeChangeListeners.remove(this)
        AutoSwitchIMELogger.debug("AutoSwitchIME IME extension disposed")
    }
}
