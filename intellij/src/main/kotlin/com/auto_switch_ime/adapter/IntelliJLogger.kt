package com.auto_switch_ime.adapter

import com.auto_switch_ime.core.util.Logger
import com.intellij.openapi.diagnostic.thisLogger

/**
 * IntelliJ 平台日志实现
 * 将核心库的 Logger 接口桥接到 IntelliJ 日志系统
 */
object IntelliJLogger : Logger {
    override fun info(msg: String) {
        thisLogger().info("[AutoSwitchIME] $msg")
    }

    override fun warn(msg: String, e: Throwable?) {
        if (e != null) {
            thisLogger().warn("[AutoSwitchIME] $msg", e)
        } else {
            thisLogger().warn("[AutoSwitchIME] $msg")
        }
    }

    override fun debug(msg: String) {
        thisLogger().debug("[AutoSwitchIME] $msg")
    }

    override fun error(msg: String, e: Throwable?) {
        if (e != null) {
            thisLogger().error("[AutoSwitchIME] $msg", e)
        } else {
            thisLogger().error("[AutoSwitchIME] $msg")
        }
    }
}
