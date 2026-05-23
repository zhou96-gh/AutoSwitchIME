package com.rimevim.adapter

import com.rimevim.core.util.Logger
import com.intellij.openapi.diagnostic.thisLogger

/**
 * IntelliJ 平台日志实现
 * 将核心库的 Logger 接口桥接到 IntelliJ 日志系统
 */
object IntelliJLogger : Logger {
    override fun info(msg: String) {
        thisLogger().info("[RimeVim] $msg")
    }

    override fun warn(msg: String, e: Throwable?) {
        if (e != null) {
            thisLogger().warn("[RimeVim] $msg", e)
        } else {
            thisLogger().warn("[RimeVim] $msg")
        }
    }

    override fun debug(msg: String) {
        thisLogger().debug("[RimeVim] $msg")
    }

    override fun error(msg: String, e: Throwable?) {
        if (e != null) {
            thisLogger().error("[RimeVim] $msg", e)
        } else {
            thisLogger().error("[RimeVim] $msg")
        }
    }
}
