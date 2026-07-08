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
import com.auto_switch_ime.core.rules.RuleEvaluator
import com.auto_switch_ime.ime.AutoSwitchIMEController
import com.auto_switch_ime.settings.AutoSwitchIMESettings
import com.auto_switch_ime.util.ActionDeduplicator
import com.auto_switch_ime.util.AutoSwitchIMELogger
import com.auto_switch_ime.util.VimModeChecker

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

            if (!controller.getTrackedState().isAsciiMode) {
                val isComposing = ImeStateDetector.isComposing(controller.stateWatcher)
                if (isComposing) {
                    AutoSwitchIMELogger.debug("VimModeListener: Rime is composing, skipping IME switch")
                    val state = controller.getTrackedState()
                    CaretColorManager.updateCaretColor(editor, state.isAsciiMode, state.isCapsLock)
                    return@invokeLater
                }
            }

            if (VimModeChecker.isInNormalMode()) {
                if (ActionDeduplicator.shouldSkip(editor, ImeAction.ENGLISH)) {
                    AutoSwitchIMELogger.debug("VimModeListener: duplicated English action skipped")
                    return@invokeLater
                }
                AutoSwitchIMELogger.debug("VimModeListener (Normal mode): forcing ASCII (English)")
                controller.setAsciiMode(true)
                CaretColorManager.updateCaretColor(editor, true, false)
            } else {
                val (before, after) = getLineContextText(editor)
                val settings = AutoSwitchIMESettings.instance
                val action = evaluateInsertModeRules(before, after, settings)
                if (ActionDeduplicator.shouldSkip(editor, action)) {
                    AutoSwitchIMELogger.debug("VimModeListener: duplicated $action action skipped")
                    return@invokeLater
                }

                when (action) {
                    ImeAction.CHINESE -> {
                        AutoSwitchIMELogger.info("VimModeListener (Insert mode): Chinese mode")
                        controller.setAsciiMode(false)
                        CaretColorManager.updateCaretColor(editor, false, false)
                    }
                    ImeAction.CAPS -> {
                        AutoSwitchIMELogger.info("VimModeListener (Insert mode): Caps mode")
                        controller.setCapsMode()
                        CaretColorManager.updateCaretColor(editor, true, true)
                    }
                    ImeAction.ENGLISH -> {
                        AutoSwitchIMELogger.info("VimModeListener (Insert mode): English mode")
                        controller.setAsciiMode(true)
                        CaretColorManager.updateCaretColor(editor, true, false)
                    }
                    ImeAction.UNCHANGED -> {
                        AutoSwitchIMELogger.debug("VimModeListener (Insert mode): IME unchanged")
                    }
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
        return RuleEvaluator.evaluate(
            before = before,
            after = after,
            chineseBeforeRegex = settings.insertModeChineseBeforeRegex,
            chineseAfterRegex = settings.insertModeChineseAfterRegex,
            capsBeforeRegex = settings.insertModeCapsBeforeRegex,
            capsAfterRegex = settings.insertModeCapsAfterRegex
        )
    }
}
