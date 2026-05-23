package com.auto_switch_ime.core

/**
 * 支持的输入法类型
 */
enum class ImeType {
    RIME,          // 小狼毫 Rime/Weasel
    SOGOU,         // 搜狗输入法
    MS_PINYIN,     // 微软拼音
    CUSTOM         // 自定义（通过外部脚本）
}
