package com.rimevim.ime

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.rimevim.caret.CaretColorManager
import com.rimevim.util.RimeVimLogger
import java.io.File
import java.util.concurrent.TimeUnit

@Service
class RimeController {

    /**
     * 内部跟踪的 IME 状态（主要来源）
     * 由于 TSF 应用（如 IntelliJ）不响应 IMM32 API，ImeStateDetector 经常返回错误结果
     * 因此以内部跟踪状态为准，ImeStateDetector 仅作为辅助
     */
    @Volatile
    private var currentAsciiMode: Boolean = true  // 默认英文

    @Volatile
    private var currentCapsMode: Boolean = false

    /**
     * 获取当前跟踪的 IME 状态（供外部查询）
     */
    fun getTrackedState(): ImeStateDetector.ImeState {
        return ImeStateDetector.ImeState(currentAsciiMode, currentCapsMode)
    }

    /**
     * 同步内部跟踪状态（当检测到手动切换时调用）
     * 不触发实际的 IME 切换，只更新内部状态
     */
    fun syncTrackedState(ascii: Boolean, caps: Boolean) {
        currentAsciiMode = ascii
        currentCapsMode = caps
        RimeVimLogger.debug("Synced tracked state: ascii=$ascii, caps=$caps (manual switch detected)")
    }

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
            if (file.isFile) {
                RimeVimLogger.info("Using configured WeaselServer path: ${file.absolutePath}")
                return file.absolutePath
            } else {
                RimeVimLogger.warn("Configured WeaselServer path is not a valid file: ${configuredPath}")
            }
        }
        // 2. 回退到注册表/常见路径检测
        val detectedPath = WeaselPathDetector.detect()
        if (detectedPath != null) {
            RimeVimLogger.info("Auto-detected WeaselServer path: $detectedPath")
        } else {
            RimeVimLogger.warn("WeaselServer.exe not found in registry or common paths")
        }
        return detectedPath
    }

    /**
     * 切换输入法模式
     * @param ascii true=英文(ASCII), false=中文
     */
    fun setAsciiMode(ascii: Boolean) {
        // 如果当前已是大写模式，先退出大写
        if (currentCapsMode && ascii) {
            RimeVimLogger.debug("Exiting caps mode before switching to ASCII")
            exitCapsMode()
        }

        // 更新内部跟踪状态
        currentAsciiMode = ascii
        // 每次都实际调用 WeaselServer，不依赖内部状态跳过（TSF 应用下初始状态可能不准确）
        switchImeMode(if (ascii) "/ascii" else "/nascii", if (ascii) "ASCII" else "Chinese")

        // 切换后更新所有编辑器的光标颜色
        updateAllCaretColors(ascii, false)
    }

    /**
     * 切换大写模式
     * 使用 SendInput 模拟 CapsLock 按键，系统级生效
     */
    fun setCapsMode() {
        if (currentCapsMode) {
            RimeVimLogger.debug("IME already in Caps mode (tracked), skipping")
            return
        }

        val eventsSent = CapsLockController.toggleCapsLock()
        if (eventsSent == 2) {
            currentCapsMode = true
            currentAsciiMode = false
            RimeVimLogger.info("Caps mode activated via SendInput ($eventsSent events)")

            // 切换后更新所有编辑器的光标颜色
            updateAllCaretColors(false, true)
        } else {
            RimeVimLogger.warn("Failed to toggle CapsLock via SendInput (sent $eventsSent events)")
        }
    }

    /**
     * 退出大写模式
     */
    fun exitCapsMode() {
        if (!currentCapsMode) {
            RimeVimLogger.debug("Not in Caps mode, skipping")
            return
        }

        val eventsSent = CapsLockController.toggleCapsLock()
        if (eventsSent == 2) {
            currentCapsMode = false
            RimeVimLogger.info("Exited Caps mode via SendInput ($eventsSent events)")

            // 退出大写后更新所有编辑器的光标颜色
            updateAllCaretColors(currentAsciiMode, false)
        } else {
            RimeVimLogger.warn("Failed to exit CapsLock via SendInput (sent $eventsSent events)")
        }
    }

    /**
     * 更新所有编辑器的光标颜色
     */
    private fun updateAllCaretColors(isAsciiMode: Boolean, isCapsLock: Boolean) {
        ApplicationManager.getApplication().invokeLater {
            val editors = EditorFactory.getInstance().allEditors
            for (editor in editors) {
                if (!editor.isDisposed) {
                    CaretColorManager.updateCaretColor(editor, isAsciiMode, isCapsLock)
                }
            }
        }
    }

    /**
     * 执行 WeaselServer 命令
     */
    private fun switchImeMode(arg: String, label: String) {
        val path = weaselServerPath
        if (path == null) {
            RimeVimLogger.warn("WeaselServer.exe not found")
            return
        }

        if (!File(path).exists()) {
            RimeVimLogger.warn("WeaselServer.exe not exists at: $path")
            return
        }

        try {
            RimeVimLogger.debug("Executing: $path $arg")
            val process = ProcessBuilder(path, arg)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            val exited = process.waitFor(1, TimeUnit.SECONDS)
            if (exited) {
                RimeVimLogger.info("Switched to $label mode (exitCode=${process.exitValue()})")
            } else {
                RimeVimLogger.warn("Switch to $label mode timed out")
                process.destroy()
            }
        } catch (e: Exception) {
            RimeVimLogger.warn("Failed to switch IME mode: $arg", e)
        }
    }

    fun resolvePath(): String? = weaselServerPath
}
