package com.rimevim

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.rimevim.caret.CaretColorManager
import com.rimevim.ime.ImeStateDetector
import com.rimevim.ime.RimeController
import com.rimevim.settings.RimeVimSettings

class RimeVimPlugin : StartupActivity {

    private val rimeController = ApplicationManager.getApplication().getService(RimeController::class.java)

    override fun runActivity(project: Project) {
        if (!RimeVimSettings.instance.enabled) {
            thisLogger().info("RimeVim IME is disabled")
            return
        }

        thisLogger().info("RimeVim IME initialized")

        // 监听所有编辑器的插入/退出事件
        setupEditorListeners(project)
    }

    private fun setupEditorListeners(project: Project) {
        val editorFactory = EditorFactory.getInstance()

        // 监听文档变化（输入时检测模式）
        editorFactory.eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val editor = event.editor
                if (editor.isDisposed) return

                ApplicationManager.getApplication().invokeLater {
                    if (editor.isDisposed) return@invokeLater
                    val state = ImeStateDetector.getCurrentState()
                    CaretColorManager.updateCaretColor(editor, state.isAsciiMode, state.isCapsLock)
                }
            }
        })

        // 监听光标变化
        editorFactory.eventMulticaster.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                val editor = event.editor
                if (editor.isDisposed) return

                ApplicationManager.getApplication().invokeLater {
                    if (editor.isDisposed) return@invokeLater
                    val state = ImeStateDetector.getCurrentState()
                    CaretColorManager.updateCaretColor(editor, state.isAsciiMode, state.isCapsLock)
                }
            }
        })
    }
}
