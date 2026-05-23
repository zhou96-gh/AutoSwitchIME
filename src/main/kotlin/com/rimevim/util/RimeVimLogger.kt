package com.rimevim.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.rimevim.settings.RimeVimSettings

/**
 * 日志工具类：根据设置级别控制日志输出
 * 级别：OFF > ERROR > WARN > INFO > DEBUG
 * 
 * 注意：在 settings 未初始化时使用默认级别（ERROR + WARN + INFO）
 */
object RimeVimLogger {

    private const val LOGGER_CATEGORY = "com.rimevim"
    private val logger = Logger.getInstance(LOGGER_CATEGORY)

    private fun shouldLog(level: String): Boolean {
        return try {
            val settings = RimeVimSettings.instance
            when (level.uppercase()) {
                "ERROR" -> settings.logError
                "WARN" -> settings.logWarn
                "INFO" -> settings.logInfo
                "DEBUG" -> settings.logDebug
                else -> false
            }
        } catch (e: Exception) {
            // settings 未初始化时使用默认值
            when (level.uppercase()) {
                "ERROR", "WARN", "INFO" -> true
                else -> false
            }
        }
    }

    fun debug(message: String, throwable: Throwable? = null) {
        if (shouldLog("DEBUG")) {
            if (throwable != null) {
                logger.debug("[RimeVim] $message", throwable)
            } else {
                logger.debug("[RimeVim] $message")
            }
        }
    }

    fun info(message: String, throwable: Throwable? = null) {
        if (shouldLog("INFO")) {
            if (throwable != null) {
                logger.info("[RimeVim] $message", throwable)
            } else {
                logger.info("[RimeVim] $message")
            }
        }
    }

    fun warn(message: String, throwable: Throwable? = null) {
        if (shouldLog("WARN")) {
            if (throwable != null) {
                logger.warn("[RimeVim] $message", throwable)
            } else {
                logger.warn("[RimeVim] $message")
            }
        }
    }

    fun error(message: String, throwable: Throwable? = null) {
        if (shouldLog("ERROR")) {
            if (throwable != null) {
                logger.error("[RimeVim] $message", throwable)
            } else {
                logger.error("[RimeVim] $message")
            }
        }
    }
}
