package com.rimevim.listener

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.rimevim.ImeAction
import com.rimevim.caret.CaretColorManager
import com.rimevim.ime.ImeStateDetector
import com.rimevim.ime.RimeController
import com.rimevim.settings.RimeVimSettings
import com.rimevim.util.RimeVimLogger
import com.rimevim.util.VimModeChecker
import java.util.regex.Pattern

/**
 * 编辑器工厂监听器：处理编辑器创建、文件切换、鼠标进入事件
 * 负责在这些时机先检测 IdeaVim 模式，再根据模式执行对应初始化
 */
class VimModeListener : EditorFactoryListener {

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        val project = editor.project ?: return

        // 监听文件编辑器变化
        project.messageBus.connect().subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    RimeVimLogger.info("VimModeListener: file selection changed")
                    updateEditorState(editor)
                }
            }
        )

        // 监听编辑器鼠标事件（用于焦点变化）
        editor.addEditorMouseListener(object : EditorMouseListener {
            override fun mouseEntered(event: EditorMouseEvent) {
                RimeVimLogger.info("VimModeListener: mouse entered editor")
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
     * - 所有输入法切换通过 RimeController 执行，其内部已更新光标颜色
     * - 此处仅负责根据模式决定调用哪个方法，不重复更新光标颜色
     */
    private fun updateEditorState(editor: Editor) {
        if (!RimeVimSettings.instance.enabled) return

        ApplicationManager.getApplication().invokeLater {
            if (editor.isDisposed) return@invokeLater

            // 编辑器未聚焦时不执行自动切换
            if (!editor.contentComponent.hasFocus()) {
                RimeVimLogger.debug("VimModeListener: editor not focused, skipping IME switch")
                return@invokeLater
            }

            val rimeController = ApplicationManager.getApplication().getService(RimeController::class.java)
                ?: run {
                    RimeVimLogger.warn("RimeController not available, skipping IME switch")
                    return@invokeLater
                }

            // 检测 Rime 是否正在输入（显示候选词窗口）
            val isComposing = ImeStateDetector.isComposing()
            if (isComposing) {
                RimeVimLogger.debug("Rime is composing, skipping IME switch")
            }

            // 统一逻辑：仅检查是否处于 Normal 模式，其他默认 Insert 模式
            if (VimModeChecker.isInNormalMode()) {
                // Normal/Visual/Select 模式：强制切换英文
                RimeVimLogger.debug("VimModeListener (Normal mode): forcing ASCII (English)")
                if (!isComposing) {
                    rimeController.setAsciiMode(true)
                }
            } else {
                // Insert 模式（或无 IdeaVim）：执行正则规则评估
                val (before, after) = getLineContextText(editor)
                val settings = RimeVimSettings.instance
                val action = evaluateInsertModeRules(before, after, settings)

                if (!isComposing) {
                    when (action) {
                        ImeAction.CHINESE -> {
                            RimeVimLogger.info("VimModeListener (Insert mode): Chinese mode")
                            rimeController.setAsciiMode(false)
                        }
                        ImeAction.CAPS -> {
                            RimeVimLogger.info("VimModeListener (Insert mode): Caps mode")
                            rimeController.setCapsMode()
                        }
                        ImeAction.ENGLISH -> {
                            RimeVimLogger.info("VimModeListener (Insert mode): English mode")
                            rimeController.setAsciiMode(true)
                        }
                        ImeAction.UNCHANGED -> {
                            RimeVimLogger.debug("VimModeListener (Insert mode): IME unchanged")
                        }
                    }
                } else {
                    RimeVimLogger.debug("VimModeListener (Insert mode): skipping due to composing")
                }
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
            RimeVimLogger.warn("Failed to get line context text", e)
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
}
