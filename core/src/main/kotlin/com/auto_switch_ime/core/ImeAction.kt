package com.auto_switch_ime.core

/**
 * 输入法动作枚举
 * 用于规则评估结果
 */
enum class ImeAction {
    CHINESE,    // 切换到中文模式
    CAPS,       // 切换到大写模式
    ENGLISH,    // 切换到英文模式
    UNCHANGED   // 保持当前状态
}
