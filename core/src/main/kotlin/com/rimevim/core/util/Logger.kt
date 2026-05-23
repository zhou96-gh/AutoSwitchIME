package com.rimevim.core.util

/**
 * 平台无关的日志接口
 * 各平台实现此接口并注入到核心库
 */
interface Logger {
    fun info(msg: String)
    fun warn(msg: String, e: Throwable? = null)
    fun debug(msg: String)
    fun error(msg: String, e: Throwable? = null)
}
