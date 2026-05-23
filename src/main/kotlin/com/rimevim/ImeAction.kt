package com.rimevim

/**
 * IME 操作指令
 */
enum class ImeAction {
    CHINESE,    // 切换为中文
    CAPS,       // 切换为大写
    ENGLISH,    // 切换为英文（ASCII）
    UNCHANGED   // 保持当前状态
}
