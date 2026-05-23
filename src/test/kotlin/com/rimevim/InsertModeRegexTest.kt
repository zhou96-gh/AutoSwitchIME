package com.rimevim

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.regex.Pattern

/**
 * 基础单元测试：验证 Insert 模式正则规则逻辑
 */
class InsertModeRegexTest {

    @Test
    fun `empty regex returns false (default Chinese mode)`() {
        val context = "some text"
        val asciiRegex = ""
        val result = evaluateAsciiRegex(context, asciiRegex)
        assertFalse(result, "Empty regex should default to Chinese mode")
    }

    @Test
    fun `ascii regex matching code context returns true`() {
        val context = "function test() { return x + y; }"
        val asciiRegex = "[a-zA-Z_][a-zA-Z0-9_]*\\s*\\("
        val result = evaluateAsciiRegex(context, asciiRegex)
        assertTrue(result, "Function call pattern should match ASCII mode")
    }

    @Test
    fun `ascii regex matching variable assignment returns true`() {
        val context = "let userName = 'test';"
        val asciiRegex = "[a-zA-Z_][a-zA-Z0-9_]*\\s*="
        val result = evaluateAsciiRegex(context, asciiRegex)
        assertTrue(result, "Variable assignment should match ASCII mode")
    }

    @Test
    fun `ascii regex not matching Chinese text returns false`() {
        val context = "这是一个中文注释"
        val asciiRegex = "[a-zA-Z_][a-zA-Z0-9_]*\\s*\\("
        val result = evaluateAsciiRegex(context, asciiRegex)
        assertFalse(result, "Chinese text should not match ASCII pattern")
    }

    @Test
    fun `invalid regex returns false and does not throw`() {
        val context = "some text"
        val invalidRegex = "[invalid(regex"
        val result = evaluateAsciiRegex(context, invalidRegex)
        assertFalse(result, "Invalid regex should return false")
    }

    @Test
    fun `regex matching string literal returns true`() {
        val context = "val message = \"hello world\""
        val asciiRegex = """[a-zA-Z_][a-zA-Z0-9_]*\s*=\s*"""
        val result = evaluateAsciiRegex(context, asciiRegex)
        assertTrue(result, "String assignment should match ASCII mode")
    }

    /**
     * 简化版正则评估函数（用于测试）
     */
    private fun evaluateAsciiRegex(context: String, regex: String): Boolean {
        if (regex.isBlank()) return false
        return try {
            val pattern = Pattern.compile(regex)
            pattern.matcher(context).find()
        } catch (e: Exception) {
            false
        }
    }
}
