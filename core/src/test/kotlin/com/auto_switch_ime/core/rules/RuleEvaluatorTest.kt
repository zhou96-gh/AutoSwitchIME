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
    fun `returns chinese before caps when both rules match`() {
        val action = RuleEvaluator.evaluate(
            before = "输入ABC",
            after = "中文",
            chineseBeforeRegex = ".*[一-龥].*",
            chineseAfterRegex = "^[一-龥].*",
            capsBeforeRegex = ".*[A-Z]{2,}[0-9_]?$",
            capsAfterRegex = ""
        )

        assertEquals(ImeAction.CHINESE, action)
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
    fun `ignores punctuation next to caret when matching caps context`() {
        val action = RuleEvaluator.evaluate(
            before = "HTTP_,",
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
