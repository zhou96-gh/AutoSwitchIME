package com.auto_switch_ime.core.rules

import com.auto_switch_ime.core.ImeAction
import java.util.regex.Pattern

/**
 * 正则规则评估器
 * 评估光标前后文本，决定输入法动作
 */
object RuleEvaluator {

    private val patternCache = mutableMapOf<String, Pattern>()
    private val trailingPunctuationOrSymbols = Pattern.compile("[\\p{P}\\p{S}]+$")
    private val leadingPunctuationOrSymbols = Pattern.compile("^[\\p{P}\\p{S}]+")

    /**
     * 评估 Insert 模式下的输入法动作
     * @param before 光标前文本
     * @param after 光标后文本
     * @param chineseBeforeRegex 中文规则（光标前）
     * @param chineseAfterRegex 中文规则（光标后）
     * @param capsBeforeRegex 大写规则（光标前）
     * @param capsAfterRegex 大写规则（光标后）
     * @param englishBeforeRegex 英文规则（光标前）
     * @param englishAfterRegex 英文规则（光标后）
     * @return 输入法动作
     */
    fun evaluate(
        before: String,
        after: String,
        chineseBeforeRegex: String,
        chineseAfterRegex: String,
        capsBeforeRegex: String,
        capsAfterRegex: String,
        englishBeforeRegex: String = "",
        englishAfterRegex: String = ""
    ): ImeAction {
        val normalizedBefore = trailingPunctuationOrSymbols.matcher(before).replaceFirst("")
        val normalizedAfter = leadingPunctuationOrSymbols.matcher(after).replaceFirst("")

        // 1. 检查中文规则：前后任一匹配
        if (matchesRegex(chineseBeforeRegex, normalizedBefore) || matchesRegex(chineseAfterRegex, normalizedAfter)) {
            return ImeAction.CHINESE
        }

        // 2. 检查大写规则：前后任一匹配
        if (matchesRegex(capsBeforeRegex, normalizedBefore) || matchesRegex(capsAfterRegex, normalizedAfter)) {
            return ImeAction.CAPS
        }

        // 3. 检查英文规则：前后任一匹配
        if (matchesRegex(englishBeforeRegex, normalizedBefore) || matchesRegex(englishAfterRegex, normalizedAfter)) {
            return ImeAction.ENGLISH
        }

        // 4. 默认英文
        return ImeAction.ENGLISH
    }

    /**
     * 检查正则是否匹配（空规则不触发）
     */
    private fun matchesRegex(pattern: String, text: String): Boolean {
        if (pattern.isBlank()) return false
        return try {
            val compiled = patternCache.getOrPut(pattern) { Pattern.compile(pattern) }
            compiled.matcher(text).find()
        } catch (e: Exception) {
            false
        }
    }
}
