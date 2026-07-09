package com.auto_switch_ime.util

import com.auto_switch_ime.core.ImeAction
import com.auto_switch_ime.core.rules.RuleEvaluator
import com.auto_switch_ime.settings.AutoSwitchIMESettings
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange

object InsertModeDecision {
    data class Context(
        val before: String,
        val after: String
    )

    data class Result(
        val context: Context,
        val action: ImeAction
    )

    fun evaluate(
        editor: Editor,
        settings: AutoSwitchIMESettings = AutoSwitchIMESettings.instance
    ): Result {
        val context = getLineContextText(editor)
        return Result(context, evaluateContext(context, settings))
    }

    fun evaluateContext(context: Context, settings: AutoSwitchIMESettings): ImeAction {
        return RuleEvaluator.evaluate(
            before = context.before,
            after = context.after,
            chineseBeforeRegex = settings.insertModeChineseBeforeRegex,
            chineseAfterRegex = settings.insertModeChineseAfterRegex,
            capsBeforeRegex = settings.insertModeCapsBeforeRegex,
            capsAfterRegex = settings.insertModeCapsAfterRegex
        )
    }

    private fun getLineContextText(editor: Editor): Context {
        return runReadActionBlocking {
            try {
                val document = editor.document
                val caretOffset = editor.caretModel.primaryCaret.offset
                val lineNumber = document.getLineNumber(caretOffset)
                val lineStart = document.getLineStartOffset(lineNumber)
                val lineEnd = document.getLineEndOffset(lineNumber)

                val beforeStart = maxOf(lineStart, caretOffset - 5)
                val afterEnd = minOf(lineEnd, caretOffset + 5)
                val before = document.getText(TextRange(beforeStart, caretOffset))
                val after = document.getText(TextRange(caretOffset, afterEnd))
                Context(before, after)
            } catch (e: Exception) {
                AutoSwitchIMELogger.warn("Failed to get line context text", e)
                Context("", "")
            }
        }
    }
}
