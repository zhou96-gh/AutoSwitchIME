package com.rimevim.ime

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import java.io.File
import java.util.concurrent.TimeUnit

@Service
class RimeController {

    @Volatile
    private var currentAsciiMode: Boolean? = null

    private val weaselServerPath: String? by lazy {
        resolveWeaselServerPath()
    }

    /**
     * 解析 WeaselServer.exe 路径
     * 优先级：用户配置 > 注册表自动检测
     */
    private fun resolveWeaselServerPath(): String? {
        val settings = com.rimevim.settings.RimeVimSettings.instance
        // 1. 优先使用用户配置
        val configuredPath = settings.weaselServerPath
        if (configuredPath.isNotBlank()) {
            val file = File(configuredPath)
            if (file.exists()) {
                return file.absolutePath
            }
        }
        // 2. 回退到注册表/常见路径检测
        return WeaselPathDetector.detect()
    }

    fun setAsciiMode(ascii: Boolean) {
        if (currentAsciiMode == ascii) return // 状态未变，跳过

        val path = weaselServerPath
        if (path == null) {
            thisLogger().warn("WeaselServer.exe not found")
            return
        }

        if (!File(path).exists()) {
            thisLogger().warn("WeaselServer.exe not exists at: $path")
            return
        }

        try {
            val arg = if (ascii) "/ascii" else "/nascii"
            val process = ProcessBuilder(path, arg)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            process.waitFor(1, TimeUnit.SECONDS)
            currentAsciiMode = ascii
            thisLogger().info("Switched to ${if (ascii) "ASCII" else "Chinese"} mode")
        } catch (e: Exception) {
            thisLogger().warn(e, "Failed to switch IME mode")
        }
    }

    fun getWeaselServerPath(): String? = weaselServerPath
}
