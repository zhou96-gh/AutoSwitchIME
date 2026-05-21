package com.rimevim.listener

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.rimevim.caret.CaretColorManager
import com.rimevim.ime.ImeStateDetector
import com.rimevim.settings.RimeVimSettings

class VimModeListener : EditorFactoryListener {

    private val rimeController = ApplicationManager.getApplication().getService(com.rimevim.ime.RimeController::class.java)

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        val project = editor.project ?: return

        // 监听文件编辑器变化
        project.messageBus.connect().subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    updateEditorState(editor)
                }
            }
        )

        // 监听编辑器鼠标事件（用于焦点变化）
        editor.addEditorMouseListener(object : EditorMouseListener {
            override fun mouseEntered(event: EditorMouseEvent) {
                updateEditorState(editor)
            }
        })

        // 初始化状态
        updateEditorState(editor)
    }

    private fun updateEditorState(editor: Editor) {
        if (!RimeVimSettings.instance.enabled) return

        ApplicationManager.getApplication().invokeLater {
            if (editor.isDisposed) return@invokeLater

            val state = ImeStateDetector.getCurrentState()
            CaretColorManager.updateCaretColor(editor, state.isAsciiMode, state.isCapsLock)
        }
    }
}
