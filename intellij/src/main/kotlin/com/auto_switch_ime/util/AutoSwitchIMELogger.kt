package com.auto_switch_ime.util

import com.auto_switch_ime.adapter.IntelliJLogger

/**
 * AutoSwitchIME 日志门面
 * 为 intellij 模块提供便捷的静态方法调用风格
 */
object AutoSwitchIMELogger {
    fun info(msg: String) = IntelliJLogger.info(msg)
    fun warn(msg: String, e: Throwable? = null) = IntelliJLogger.warn(msg, e)
    fun debug(msg: String) = IntelliJLogger.debug(msg)
    fun error(msg: String, e: Throwable? = null) = IntelliJLogger.error(msg, e)
}
