package com.auto_switch_ime.listener

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.auto_switch_ime.ime.AutoSwitchIMEController
import com.auto_switch_ime.settings.AutoSwitchIMESettings
import com.auto_switch_ime.util.AutoSwitchIMELogger

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
        val controller = ApplicationManager.getApplication().getService(AutoSwitchIMEController::class.java)
            ?: return
        controller.requestEditorUpdate(editor, "VimModeListener")
    }
}
