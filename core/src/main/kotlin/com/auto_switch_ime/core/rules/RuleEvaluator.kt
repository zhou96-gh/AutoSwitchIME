package com.auto_switch_ime.core.rules

import com.auto_switch_ime.core.ImeAction
import java.util.regex.Pattern

/**
 * 正则规则评估器
 * 评估光标前后文本，决定输入法动作
 */
object RuleEvaluator {

    private val patternCache = mutableMapOf<String, Pattern>()
    private val trailingPunctuationSymbolsNumbersOrSpaces = Pattern.compile("[\\p{P}\\p{S}\\p{N}\\s]+$")
    private val leadingPunctuationSymbolsNumbersOrSpaces = Pattern.compile("^[\\p{P}\\p{S}\\p{N}\\s]+")
    private val capsSeparators = Pattern.compile("[-_]")
    private val trailingNeutralCharacters = Pattern.compile("[\\p{N}\\s]+$")
    private val leadingNeutralCharacters = Pattern.compile("^[\\p{N}\\s]+")
    private val trailingEnglishCharacter = Pattern.compile("[\\x21-\\x7E]$")
    private val leadingEnglishCharacter = Pattern.compile("^[\\x21-\\x7E]")

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
        val chineseBefore = trailingPunctuationSymbolsNumbersOrSpaces.matcher(before).replaceFirst("")
        val chineseAfter = leadingPunctuationSymbolsNumbersOrSpaces.matcher(after).replaceFirst("")
        val capsBefore = capsSeparators.matcher(trailingNeutralCharacters.matcher(before).replaceFirst("")).replaceAll("")
        val capsAfter = capsSeparators.matcher(leadingNeutralCharacters.matcher(after).replaceFirst("")).replaceAll("")
        val englishBefore = trailingNeutralCharacters.matcher(before).replaceFirst("")
        val englishAfter = leadingNeutralCharacters.matcher(after).replaceFirst("")

        // 光标两侧同时命中时，以左侧上下文为准。
        if (matchesRegex(capsBeforeRegex, capsBefore)) {
            return ImeAction.CAPS
        }
        if (matchesRegex(englishBeforeRegex, englishBefore) || trailingEnglishCharacter.matcher(englishBefore).find()) {
            return ImeAction.ENGLISH
        }
        if (matchesRegex(chineseBeforeRegex, chineseBefore)) {
            return ImeAction.CHINESE
        }

        if (matchesRegex(capsAfterRegex, capsAfter)) {
            return ImeAction.CAPS
        }
        if (matchesRegex(englishAfterRegex, englishAfter) || leadingEnglishCharacter.matcher(englishAfter).find()) {
            return ImeAction.ENGLISH
        }
        if (matchesRegex(chineseAfterRegex, chineseAfter)) {
            return ImeAction.CHINESE
        }

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
