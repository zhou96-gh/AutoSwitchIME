package com.auto_switch_ime.util

import com.auto_switch_ime.core.ImeAction
import com.intellij.openapi.editor.Editor

object ActionDeduplicator {
    private var lastEditor: Editor? = null
    private var lastDocumentStamp: Long = -1L
    private var lastCaretOffset: Int = -1
    private var lastAction: ImeAction? = null

    @Synchronized
    fun shouldSkip(editor: Editor, action: ImeAction): Boolean {
        val documentStamp = editor.document.modificationStamp
        val caretOffset = editor.caretModel.primaryCaret.offset
        val skip = lastEditor === editor &&
                lastDocumentStamp == documentStamp &&
                lastCaretOffset == caretOffset &&
                lastAction == action

        lastEditor = editor
        lastDocumentStamp = documentStamp
        lastCaretOffset = caretOffset
        lastAction = action

        return skip
    }

    @Synchronized
    fun invalidate() {
        lastEditor = null
        lastDocumentStamp = -1L
        lastCaretOffset = -1
        lastAction = null
    }
}
