package com.auto_switch_ime.core

/**
 * 输入法配置
 */
data class ImeConfig(
    val type: ImeType = ImeType.RIME,
    val weaselServerPath: String? = null,
    val customSwitchScript: String? = null,
)
