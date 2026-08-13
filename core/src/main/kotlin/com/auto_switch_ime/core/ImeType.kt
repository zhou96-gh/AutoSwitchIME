package com.auto_switch_ime.core

/**
 * 支持的输入法类型
 */
enum class ImeType(
    val configValue: String,
    val displayName: String,
    val available: Boolean = false
) {
    RIME("rime", "Rime / 小狼毫", true),
    SOGOU("sogou", "搜狗输入法"),
    MS_PINYIN("ms_pinyin", "微软拼音"),
    CUSTOM("custom", "自定义输入法");

    override fun toString(): String = displayName

    companion object {
        fun fromConfig(value: String?): ImeType {
            return availableTypes().firstOrNull {
                it.configValue.equals(value, ignoreCase = true)
            } ?: RIME
        }

        fun availableTypes(): List<ImeType> = entries.filter(ImeType::available)
    }
}
