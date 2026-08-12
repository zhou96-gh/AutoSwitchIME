package com.auto_switch_ime.core.rules

import com.auto_switch_ime.core.ImeAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RuleEvaluatorTest {

    @Test
    fun `returns chinese when before text matches chinese rule`() {
        val action = RuleEvaluator.evaluate(
            before = "输入",
            after = "",
            chineseBeforeRegex = ".*[一-龥]$",
            chineseAfterRegex = "^[一-龥].*",
            capsBeforeRegex = ".*[A-Z]{2,}[0-9_]?$",
            capsAfterRegex = ""
        )

        assertEquals(ImeAction.CHINESE, action)
    }

    @Test
    fun `ignores punctuation next to caret when matching chinese context`() {
        val action = RuleEvaluator.evaluate(
            before = "输入，。！？",
            after = "",
            chineseBeforeRegex = ".*[一-龥]$",
            chineseAfterRegex = "^[一-龥].*",
            capsBeforeRegex = ".*[A-Z]{2,}[0-9_]?$",
            capsAfterRegex = ""
        )

        assertEquals(ImeAction.CHINESE, action)
    }

    @Test
    fun `ignores punctuation next to caret when matching text after caret`() {
        val action = RuleEvaluator.evaluate(
            before = "",
            after = "（《中文",
            chineseBeforeRegex = ".*[一-龥]$",
            chineseAfterRegex = "^[一-龥].*",
            capsBeforeRegex = ".*[A-Z]{2,}[0-9_]?$",
            capsAfterRegex = ""
        )

        assertEquals(ImeAction.CHINESE, action)
    }

    @Test
    fun `prefers matching context before caret when both sides match`() {
        val action = RuleEvaluator.evaluate(
            before = "ABC",
            after = "中文",
            chineseBeforeRegex = ".*[一-龥].*",
            chineseAfterRegex = "^[一-龥].*",
            capsBeforeRegex = ".*[A-Z]{2,}[0-9_]?$",
            capsAfterRegex = ""
        )

        assertEquals(ImeAction.CAPS, action)
    }

    @Test
    fun `returns english for letters and english punctuation before caret`() {
        for (before in listOf("a", ",")) {
            val action = RuleEvaluator.evaluate(
                before = before,
                after = "中文",
                chineseBeforeRegex = ".*[一-龥]$",
                chineseAfterRegex = "^[一-龥].*",
                capsBeforeRegex = ".*[A-Z]{2,}[0-9_]?$",
                capsAfterRegex = ""
            )

            assertEquals(ImeAction.ENGLISH, action)
        }
    }

    @Test
    fun `ignores numbers for chinese caps and english matching`() {
        val contexts = listOf(
            "中文1" to ImeAction.CHINESE,
            "HTTP1" to ImeAction.CAPS,
            "a1" to ImeAction.ENGLISH
        )

        for ((before, expected) in contexts) {
            val action = RuleEvaluator.evaluate(
                before = before,
                after = "",
                chineseBeforeRegex = ".*[一-龥]$",
                chineseAfterRegex = "^[一-龥].*",
                capsBeforeRegex = ".*[A-Z]{2,}[0-9_]?$",
                capsAfterRegex = ""
            )

            assertEquals(expected, action)
        }
    }

    @Test
    fun `number-only left context does not override right match`() {
        val action = RuleEvaluator.evaluate(
            before = "1",
            after = "中文",
            chineseBeforeRegex = ".*[一-龥]$",
            chineseAfterRegex = "^[一-龥].*",
            capsBeforeRegex = ".*[A-Z]{2,}[0-9_]?$",
            capsAfterRegex = ""
        )

        assertEquals(ImeAction.CHINESE, action)
    }

    @Test
    fun `space-only left context does not override right match`() {
        val action = RuleEvaluator.evaluate(
            before = " ",
            after = "中文",
            chineseBeforeRegex = ".*[一-龥]$",
            chineseAfterRegex = "^[一-龥].*",
            capsBeforeRegex = ".*[A-Z]{2,}[0-9_]?$",
            capsAfterRegex = ""
        )

        assertEquals(ImeAction.CHINESE, action)
    }

    @Test
    fun `ignores spaces and numbers for all matching modes`() {
        val contexts = listOf(
            "中文 1" to ImeAction.CHINESE,
            "HTTP 1" to ImeAction.CAPS,
            "a 1" to ImeAction.ENGLISH
        )

        for ((before, expected) in contexts) {
            val action = RuleEvaluator.evaluate(
                before = before,
                after = "",
                chineseBeforeRegex = ".*[一-龥]$",
                chineseAfterRegex = "^[一-龥].*",
                capsBeforeRegex = ".*[A-Z]{2,}[0-9_]?$",
                capsAfterRegex = ""
            )

            assertEquals(expected, action)
        }
    }

    @Test
    fun `does not treat full-width punctuation as english`() {
        val action = RuleEvaluator.evaluate(
            before = "，",
            after = "中文",
            chineseBeforeRegex = ".*[一-龥]$",
            chineseAfterRegex = "^[一-龥].*",
            capsBeforeRegex = ".*[A-Z]{2,}[0-9_]?$",
            capsAfterRegex = ""
        )

        assertEquals(ImeAction.CHINESE, action)
    }

    @Test
    fun `treats english punctuation as english instead of chinese context`() {
        val action = RuleEvaluator.evaluate(
            before = "中文,",
            after = "",
            chineseBeforeRegex = ".*[一-龥]$",
            chineseAfterRegex = "^[一-龥].*",
            capsBeforeRegex = ".*[A-Z]{2,}[0-9_]?$",
            capsAfterRegex = ""
        )

        assertEquals(ImeAction.ENGLISH, action)
    }

    @Test
    fun `treats english punctuation after caret as english`() {
        val action = RuleEvaluator.evaluate(
            before = "",
            after = ",中文",
            chineseBeforeRegex = ".*[一-龥]$",
            chineseAfterRegex = "^[一-龥].*",
            capsBeforeRegex = ".*[A-Z]{2,}[0-9_]?$",
            capsAfterRegex = ""
        )

        assertEquals(ImeAction.ENGLISH, action)
    }

    @Test
    fun `returns caps when caps rule matches and chinese does not`() {
        val action = RuleEvaluator.evaluate(
            before = "HTTP_",
            after = "",
            chineseBeforeRegex = ".*[一-龥]$",
            chineseAfterRegex = "^[一-龥].*",
            capsBeforeRegex = ".*[A-Z]{2,}[0-9_]?$",
            capsAfterRegex = ""
        )

        assertEquals(ImeAction.CAPS, action)
    }

    @Test
    fun `does not ignore general punctuation when matching caps context`() {
        val action = RuleEvaluator.evaluate(
            before = "HTTP_,",
            after = "",
            chineseBeforeRegex = ".*[一-龥]$",
            chineseAfterRegex = "^[一-龥].*",
            capsBeforeRegex = ".*[A-Z]{2,}[0-9_]?$",
            capsAfterRegex = ""
        )

        assertEquals(ImeAction.ENGLISH, action)
    }

    @Test
    fun `ignores only hyphens and underscores when matching caps context`() {
        val action = RuleEvaluator.evaluate(
            before = "HTTP_-",
            after = "",
            chineseBeforeRegex = ".*[一-龥]$",
            chineseAfterRegex = "^[一-龥].*",
            capsBeforeRegex = ".*[A-Z]{2,}[0-9_]?$",
            capsAfterRegex = ""
        )

        assertEquals(ImeAction.CAPS, action)
    }

    @Test
    fun `returns english when rules are blank or invalid`() {
        val action = RuleEvaluator.evaluate(
            before = "plain",
            after = "text",
            chineseBeforeRegex = "",
            chineseAfterRegex = "[",
            capsBeforeRegex = "",
            capsAfterRegex = "["
        )

        assertEquals(ImeAction.ENGLISH, action)
    }
}
