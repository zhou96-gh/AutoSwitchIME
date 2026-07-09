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
import com.auto_switch_ime.util.ActionDeduplicator
import com.auto_switch_ime.util.AutoSwitchIMELogger
import com.auto_switch_ime.util.InsertModeDecision
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

            val isNormalLikeMode = VimModeChecker.isNormalLikeMode(editor)

            if (!isNormalLikeMode && !controller.getTrackedState().isAsciiMode) {
                val isComposing = ImeStateDetector.isComposing(controller.stateWatcher)
                if (isComposing) {
                    AutoSwitchIMELogger.debug("VimModeListener: Rime is composing, skipping IME switch")
                    val state = controller.getTrackedState()
                    CaretColorManager.updateCaretColor(editor, state.isAsciiMode, state.isCapsLock)
                    return@invokeLater
                }
            }

            if (isNormalLikeMode) {
                if (ActionDeduplicator.shouldSkip(editor, ImeAction.ENGLISH)) {
                    AutoSwitchIMELogger.debug("VimModeListener: duplicated English action skipped")
                } else {
                    AutoSwitchIMELogger.debug("VimModeListener (Normal-like mode): forcing ASCII (English)")
                }
                controller.setAsciiMode(true)
                CaretColorManager.updateCaretColor(editor, true, false)
            } else {
                val action = InsertModeDecision.evaluate(editor).action
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
}
