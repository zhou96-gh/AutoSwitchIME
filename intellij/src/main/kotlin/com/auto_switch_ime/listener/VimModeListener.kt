package com.auto_switch_ime.listener

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.auto_switch_ime.core.ImeAction
import com.auto_switch_ime.caret.CaretColorManager
import com.auto_switch_ime.core.ime.ImeStateDetector
import com.auto_switch_ime.ime.AutoSwitchIMEController
import com.auto_switch_ime.settings.AutoSwitchIMESettings
import com.auto_switch_ime.util.AutoSwitchIMELogger
import com.auto_switch_ime.util.VimModeChecker
import java.util.regex.Pattern

/**
 * 编辑器工厂监听器：处理编辑器创建、文件切换、鼠标进入事件
 * 负责在这些时机先检测 IdeaVim 模式，再根据模式执行对应初始化
 */
class VimModeListener : EditorFactoryListener {

    // Regex pattern cache – avoid Pattern.compile() on every call
    private val regexCache = HashMap<String, Pattern>(4)

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        val project = editor.project ?: return

        // 监听文件编辑器变化
        project.messageBus.connect().subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    AutoSwitchIMELogger.info("VimModeListener: file selection changed")
                    updateEditorState(editor)
                }
            }
        )

        // 监听编辑器鼠标事件（用于焦点变化）
        editor.addEditorMouseListener(object : EditorMouseListener {
            override fun mouseEntered(event: EditorMouseEvent) {
                AutoSwitchIMELogger.info("VimModeListener: mouse entered editor")
                updateEditorState(editor)
            }
        })

        // 初始化状态
        updateEditorState(editor)
    }

    /**
     * 更新编辑器状态
     * 有 IdeaVim 时：先检测当前模式，再根据模式执行对应初始化
     * 无 IdeaVim 时：按 Insert 模式处理，使用正则规则切换输入法
     *
     * 注意：
     * - Rime 正在输入（显示候选词窗口）时跳过输入法切换
     * - IME 切换后同步更新光标颜色
     */
    private fun updateEditorState(editor: Editor) {
        if (!AutoSwitchIMESettings.instance.enabled) return

        ApplicationManager.getApplication().invokeLater {
            if (editor.isDisposed) return@invokeLater

            // 编辑器未聚焦时不执行自动切换
            if (!editor.contentComponent.hasFocus()) {
                AutoSwitchIMELogger.debug("VimModeListener: editor not focused, skipping IME switch")
                return@invokeLater
            }

            val controller = ApplicationManager.getApplication().getService(AutoSwitchIMEController::class.java)
                ?: run {
                    AutoSwitchIMELogger.warn("AutoSwitchIMEController not available, skipping IME switch")
                    return@invokeLater
                }

            // 检测 Rime 是否正在输入（显示候选词窗口）
            val isComposing = ImeStateDetector.isComposing(controller.stateWatcher)
            if (isComposing) {
                AutoSwitchIMELogger.debug("Rime is composing, skipping IME switch")
            }

            // 目标状态（用于切换 IME 后更新光标颜色）
            var targetAscii = true
            var targetCaps = false
            var targetKnown = true

            // 统一逻辑：仅检查是否处于 Normal 模式，其他默认 Insert 模式
            if (VimModeChecker.isInNormalMode()) {
                // Normal/Visual/Select 模式：强制切换英文
                AutoSwitchIMELogger.debug("VimModeListener (Normal mode): forcing ASCII (English)")
                if (!isComposing) {
                    controller.setAsciiMode(true)
                }
                targetAscii = true
                targetCaps = false
            } else {
                // Insert 模式（或无 IdeaVim）：执行正则规则评估
                val (before, after) = getLineContextText(editor)
                val settings = AutoSwitchIMESettings.instance
                val action = evaluateInsertModeRules(before, after, settings)

                // 正在 composing 时，只跳过切到英文的动作（避免干扰候选词窗口）
                // 切到中文/大写不会造成干扰（中文大概率已是中文，大写不影响输入法）
                if (isComposing && action == ImeAction.ENGLISH) {
                    AutoSwitchIMELogger.debug("VimModeListener (Insert mode): composing, skip switch to English")
                } else {
                    when (action) {
                        ImeAction.CHINESE -> {
                            AutoSwitchIMELogger.info("VimModeListener (Insert mode): Chinese mode")
                            controller.setAsciiMode(false)
                            targetAscii = false
                            targetCaps = false
                        }
                        ImeAction.CAPS -> {
                            AutoSwitchIMELogger.info("VimModeListener (Insert mode): Caps mode")
                            controller.setCapsMode()
                            targetAscii = false
                            targetCaps = true
                        }
                        ImeAction.ENGLISH -> {
                            AutoSwitchIMELogger.info("VimModeListener (Insert mode): English mode")
                            controller.setAsciiMode(true)
                            targetAscii = true
                            targetCaps = false
                        }
                        ImeAction.UNCHANGED -> {
                            AutoSwitchIMELogger.debug("VimModeListener (Insert mode): IME unchanged")
                            targetKnown = false
                        }
                    }
                }
            }

            // 同步更新光标颜色
            if (targetKnown) {
                CaretColorManager.updateCaretColor(editor, targetAscii, targetCaps)
            } else {
                val state = ImeStateDetector.getCurrentState(controller.stateWatcher, controller.getTrackedState())
                CaretColorManager.updateCaretColor(editor, state.isAsciiMode, state.isCapsLock)
            }
        }
    }

    /**
     * 获取光标所在行的上下文文本（不跨行）
     * @return Pair(光标前文本，光标后文本)
     */
    private fun getLineContextText(editor: Editor): Pair<String, String> {
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
            return Pair(before, after)
        } catch (e: Exception) {
            AutoSwitchIMELogger.warn("Failed to get line context text", e)
            return Pair("", "")
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
}
