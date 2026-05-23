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
import com.auto_switch_ime.ime.AutoSwitchIMEController
import com.auto_switch_ime.settings.AutoSwitchIMESettings
import com.auto_switch_ime.util.AutoSwitchIMELogger
import java.util.regex.Pattern

/**
 * IdeaVim 扩展：监听 Vim 模式变化，自动切换输入法并更新光标颜色
 *
 * 模式切换规则：
 * - Normal/Visual/Select/OP_PENDING → 强制英文（ASCII）
 * - Command-line (:) → 不变（保持当前 IME 状态）
 * - Insert/Replace → 评估正则规则：中文规则→中文, 大写规则→大写, 默认→英文
 */
class AutoSwitchIMEExtension : VimExtension, ModeChangeListener {

    // Regex pattern cache – avoid Pattern.compile() on every mode change
    private val regexCache = HashMap<String, Pattern>(4)

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

            // 编辑器未聚焦时不执行自动切换
            if (!ijEditor.contentComponent.hasFocus()) {
                AutoSwitchIMELogger.debug("AutoSwitchIMEExtension: editor not focused, skipping IME switch")
                return@invokeLater
            }

            // 检测 Rime 是否正在输入（显示候选词窗口）
            val isComposing = ImeStateDetector.isComposing(controller.stateWatcher)
            if (isComposing) {
                AutoSwitchIMELogger.info("Rime is composing, skipping IME switch but updating caret color")
                // 正在输入时不切换输入法，但仍根据目标状态更新光标颜色
            }

            // 根据模式确定目标状态
            var targetAscii = true
            var targetCaps = false
            var targetKnown = true  // 是否已知目标状态

            when (currentMode) {
                // Normal/Visual/Select/OP_PENDING → 强制英文
                is Mode.NORMAL, is Mode.VISUAL, is Mode.SELECT, is Mode.OP_PENDING -> {
                    AutoSwitchIMELogger.info("Normal mode → forcing ASCII (English)")
                    if (!isComposing) {
                        controller.setAsciiMode(true)
                    }
                    targetAscii = true
                    targetCaps = false
                }

                // Command-line (:) → 不变，使用当前检测状态
                is Mode.CMD_LINE -> {
                    AutoSwitchIMELogger.debug("Command-line mode → IME unchanged")
                    targetKnown = false
                }

                // Insert/Replace → 评估正则规则
                is Mode.INSERT, is Mode.REPLACE -> {
                    val (before, after) = getLineContextText(ijEditor)
                    AutoSwitchIMELogger.info("Insert context: before='$before', after='$after'")
                    val settings = AutoSwitchIMESettings.instance
                    val action = evaluateInsertModeRules(before, after, settings)

                    // 正在 composing 时，只跳过切到英文的动作（避免干扰候选词窗口）
                    // 切到中文/大写不会造成干扰（中文大概率已是中文，大写不影响输入法）
                    if (isComposing && action == ImeAction.ENGLISH) {
                        AutoSwitchIMELogger.info("Insert mode → composing, skip switch to English")
                    } else {
                        when (action) {
                            ImeAction.CHINESE -> {
                                AutoSwitchIMELogger.info("Insert mode → Chinese (regex matched)")
                                controller.setAsciiMode(false)
                                targetAscii = false
                                targetCaps = false
                            }
                            ImeAction.CAPS -> {
                                AutoSwitchIMELogger.info("Insert mode → Caps (regex matched)")
                                controller.setCapsMode()
                                targetAscii = false
                                targetCaps = true
                            }
                            ImeAction.ENGLISH -> {
                                AutoSwitchIMELogger.info("Insert mode → English (default)")
                                controller.setAsciiMode(true)
                                targetAscii = true
                                targetCaps = false
                            }
                            ImeAction.UNCHANGED -> {
                                AutoSwitchIMELogger.debug("Insert mode → IME unchanged")
                                targetKnown = false
                            }
                        }
                    }
                }
            }

            // 更新光标颜色：使用已知的目标状态，避免异步读取旧状态
            if (targetKnown) {
                AutoSwitchIMELogger.info("Caret color: ascii=$targetAscii, caps=$targetCaps (target state)")
                CaretColorManager.updateCaretColor(ijEditor, targetAscii, targetCaps)
            } else {
                val state = ImeStateDetector.getCurrentState(controller.stateWatcher, controller.getTrackedState())
                AutoSwitchIMELogger.info("Caret color: ascii=${state.isAsciiMode}, caps=${state.isCapsLock} (detected)")
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
        // 1. 检查中文规则：前后任一匹配
        val chineseBeforeMatch = matchesRegex(settings.insertModeChineseBeforeRegex, before)
        val chineseAfterMatch = matchesRegex(settings.insertModeChineseAfterRegex, after)
        if (chineseBeforeMatch || chineseAfterMatch) {
            AutoSwitchIMELogger.debug("Chinese regex matched (before='$before' or after='$after')")
            return ImeAction.CHINESE
        }

        // 2. 检查大写规则：前后任一匹配
        val capsBeforeMatch = matchesRegex(settings.insertModeCapsBeforeRegex, before)
        val capsAfterMatch = matchesRegex(settings.insertModeCapsAfterRegex, after)
        if (capsBeforeMatch || capsAfterMatch) {
            AutoSwitchIMELogger.debug("Caps regex matched (before='$before' or after='$after')")
            return ImeAction.CAPS
        }

        // 默认：英文模式
        AutoSwitchIMELogger.debug("No regex matched before/after, defaulting to English")
        return ImeAction.ENGLISH
    }

    /**
     * 检查正则是否匹配（空规则视为匹配），复用已编译的 Pattern 避免重复编译
     */
    private fun matchesRegex(pattern: String, text: String): Boolean {
        if (pattern.isBlank()) return true
        return try {
            val compiled = regexCache.getOrPut(pattern) { Pattern.compile(pattern) }
            compiled.matcher(text).find()
        } catch (e: Exception) {
            AutoSwitchIMELogger.warn("Invalid regex: $pattern", e)
            false
        }
    }

    override fun dispose() {
        injector.listenersNotifier.modeChangeListeners.remove(this)
        AutoSwitchIMELogger.debug("AutoSwitchIME IME extension disposed")
    }
}
