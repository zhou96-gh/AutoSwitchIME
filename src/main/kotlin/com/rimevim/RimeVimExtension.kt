package com.rimevim

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
import com.rimevim.caret.CaretColorManager
import com.rimevim.ime.ImeStateDetector
import com.rimevim.ime.RimeController
import com.rimevim.settings.RimeVimSettings
import com.rimevim.util.RimeVimLogger
import java.util.regex.Pattern

/**
 * IdeaVim 扩展：监听 Vim 模式变化，自动切换输入法并更新光标颜色
 *
 * 模式切换规则：
 * - Normal/Visual/Select/OP_PENDING → 强制英文（ASCII）
 * - Command-line (:) → 不变（保持当前 IME 状态）
 * - Insert/Replace → 评估正则规则：中文规则→中文, 大写规则→大写, 默认→英文
 */
class RimeVimExtension : VimExtension, ModeChangeListener {

    private val rimeController: RimeController by lazy {
        ApplicationManager.getApplication().getService(RimeController::class.java)
    }

    override fun getName(): String = "rimevim-ime"

    override fun init() {
        val settings = RimeVimSettings.instance
        RimeVimLogger.info("init() called, enabled=${settings.enabled}, log=[E:${settings.logError} W:${settings.logWarn} I:${settings.logInfo} D:${settings.logDebug}]")
        RimeVimLogger.info("WeaselServer path: ${rimeController.resolvePath() ?: "(not found)"}")

        if (!settings.enabled) {
            RimeVimLogger.info("RimeVim IME is disabled")
            return
        }

        RimeVimLogger.info("RimeVim IME extension initialized")

        // 初始化：检测当前 IME 状态并更新所有编辑器的光标颜色
        initializeImeState()

        // 注册模式变化监听器
        injector.listenersNotifier.modeChangeListeners.add(this)
        RimeVimLogger.info("ModeChangeListener registered")
    }

    /**
     * 初始化 IME 状态：检测当前输入法状态并更新所有编辑器的光标颜色
     */
    private fun initializeImeState() {
        try {
            val state = ImeStateDetector.getCurrentState()
            RimeVimLogger.info("Initializing IME state: ascii=${state.isAsciiMode}, caps=${state.isCapsLock}")

            val editors = EditorFactory.getInstance().allEditors
            for (editor in editors) {
                if (!editor.isDisposed) {
                    CaretColorManager.updateCaretColor(editor, state.isAsciiMode, state.isCapsLock)
                    val fileName = FileDocumentManager.getInstance().getFile(editor.document)?.name ?: "(unnamed)"
                    RimeVimLogger.debug("Initialized caret color for editor: $fileName")
                }
            }

            if (editors.isEmpty()) {
                RimeVimLogger.debug("No editors available during initialization, will initialize on editor creation")
            } else {
                RimeVimLogger.info("Initialized caret color for ${editors.size} editor(s)")
            }
        } catch (e: Exception) {
            RimeVimLogger.warn("Failed to initialize IME state", e)
        }
    }

    override fun modeChanged(editor: VimEditor, oldMode: Mode) {
        if (!RimeVimSettings.instance.enabled) return

        val currentMode = editor.mode
        RimeVimLogger.info("modeChanged: $oldMode → $currentMode")

        ApplicationManager.getApplication().invokeLater {
            val ijEditor = editor.ij
            if (ijEditor.isDisposed) {
                RimeVimLogger.debug("Editor disposed, skipping")
                return@invokeLater
            }

            // 编辑器未聚焦时不执行自动切换
            if (!ijEditor.contentComponent.hasFocus()) {
                RimeVimLogger.debug("RimeVimExtension: editor not focused, skipping IME switch")
                return@invokeLater
            }

            // 检测 Rime 是否正在输入（显示候选词窗口）
            val isComposing = ImeStateDetector.isComposing()
            if (isComposing) {
                RimeVimLogger.info("Rime is composing, skipping IME switch but updating caret color")
                // 正在输入时不切换输入法，但仍根据目标状态更新光标颜色
            }

            // 根据模式确定目标状态
            var targetAscii = true
            var targetCaps = false
            var targetKnown = true  // 是否已知目标状态

            when (currentMode) {
                // Normal/Visual/Select/OP_PENDING → 强制英文
                is Mode.NORMAL, is Mode.VISUAL, is Mode.SELECT, is Mode.OP_PENDING -> {
                    RimeVimLogger.info("Normal mode → forcing ASCII (English)")
                    if (!isComposing) {
                        rimeController.setAsciiMode(true)
                    }
                    targetAscii = true
                    targetCaps = false
                }

                // Command-line (:) → 不变，使用当前检测状态
                is Mode.CMD_LINE -> {
                    RimeVimLogger.debug("Command-line mode → IME unchanged")
                    targetKnown = false
                }

                // Insert/Replace → 评估正则规则
                is Mode.INSERT, is Mode.REPLACE -> {
                    val (before, after) = getLineContextText(ijEditor)
                    RimeVimLogger.info("Insert context: before='$before', after='$after'")
                    val settings = RimeVimSettings.instance
                    val action = evaluateInsertModeRules(before, after, settings)
                    when (action) {
                        ImeAction.CHINESE -> {
                            RimeVimLogger.info("Insert mode → Chinese (regex matched)")
                            if (!isComposing) {
                                rimeController.setAsciiMode(false)
                            }
                            targetAscii = false
                            targetCaps = false
                        }
                        ImeAction.CAPS -> {
                            RimeVimLogger.info("Insert mode → Caps (regex matched)")
                            if (!isComposing) {
                                rimeController.setCapsMode()
                            }
                            targetAscii = false
                            targetCaps = true
                        }
                        ImeAction.ENGLISH -> {
                            RimeVimLogger.info("Insert mode → English (default)")
                            if (!isComposing) {
                                rimeController.setAsciiMode(true)
                            }
                            targetAscii = true
                            targetCaps = false
                        }
                        ImeAction.UNCHANGED -> {
                            RimeVimLogger.debug("Insert mode → IME unchanged")
                            targetKnown = false
                        }
                    }
                }
            }

            // 更新光标颜色：使用已知的目标状态，避免异步读取旧状态
            if (targetKnown) {
                RimeVimLogger.info("Caret color: ascii=$targetAscii, caps=$targetCaps (target state)")
                CaretColorManager.updateCaretColor(ijEditor, targetAscii, targetCaps)
            } else {
                val state = ImeStateDetector.getCurrentState()
                RimeVimLogger.info("Caret color: ascii=${state.isAsciiMode}, caps=${state.isCapsLock} (detected)")
                CaretColorManager.updateCaretColor(ijEditor, state.isAsciiMode, state.isCapsLock)
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
                RimeVimLogger.warn("Failed to get line context text", e)
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
        settings: RimeVimSettings
    ): ImeAction {
        // 1. 检查中文规则：前后任一匹配
        val chineseBeforeMatch = matchesRegex(settings.insertModeChineseBeforeRegex, before)
        val chineseAfterMatch = matchesRegex(settings.insertModeChineseAfterRegex, after)
        if (chineseBeforeMatch || chineseAfterMatch) {
            RimeVimLogger.debug("Chinese regex matched (before='$before' or after='$after')")
            return ImeAction.CHINESE
        }

        // 2. 检查大写规则：前后任一匹配
        val capsBeforeMatch = matchesRegex(settings.insertModeCapsBeforeRegex, before)
        val capsAfterMatch = matchesRegex(settings.insertModeCapsAfterRegex, after)
        if (capsBeforeMatch || capsAfterMatch) {
            RimeVimLogger.debug("Caps regex matched (before='$before' or after='$after')")
            return ImeAction.CAPS
        }

        // 默认：英文模式
        RimeVimLogger.debug("No regex matched before/after, defaulting to English")
        return ImeAction.ENGLISH
    }

    /**
     * 检查正则是否匹配（空规则视为匹配）
     */
    private fun matchesRegex(pattern: String, text: String): Boolean {
        if (pattern.isBlank()) return true
        return try {
            Pattern.compile(pattern).matcher(text).find()
        } catch (e: Exception) {
            RimeVimLogger.warn("Invalid regex: $pattern", e)
            false
        }
    }

    override fun dispose() {
        injector.listenersNotifier.modeChangeListeners.remove(this)
        RimeVimLogger.debug("RimeVim IME extension disposed")
    }
}
