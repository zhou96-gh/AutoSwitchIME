package com.auto_switch_ime.util

import com.auto_switch_ime.adapter.IntelliJLogger
import com.auto_switch_ime.core.util.Logger

/**
 * AutoSwitchIME 日志门面
 * 为 intellij 模块提供便捷的静态方法调用风格
 */
object AutoSwitchIMELogger : Logger {
    override fun info(msg: String) = IntelliJLogger.info(msg)
    override fun warn(msg: String, e: Throwable?) = IntelliJLogger.warn(msg, e)
    override fun debug(msg: String) = IntelliJLogger.debug(msg)
    override fun error(msg: String, e: Throwable?) = IntelliJLogger.error(msg, e)
}
