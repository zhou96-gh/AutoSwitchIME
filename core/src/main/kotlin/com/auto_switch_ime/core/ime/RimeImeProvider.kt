package com.auto_switch_ime.core.ime

import com.auto_switch_ime.core.*
import com.auto_switch_ime.core.util.Logger
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Rime/Weasel 输入法提供者
 * 实现 ImeProvider 接口，提供平台无关的 IME 切换逻辑
 */
class RimeImeProvider(
    private val config: ImeConfig,
    private val logger: Logger
) : ImeProvider {

    override val name: String = "Rime/Weasel"

    @Volatile
    private var currentAsciiMode: Boolean = true

    @Volatile
    private var currentCapsMode: Boolean = false

    val stateWatcher: StateWatcher
    private val weaselServerPath: String?

    init {
        weaselServerPath = config.weaselServerPath ?: WeaselPathDetector.detect(logger)
        stateWatcher = StateWatcher(
            stateFilePath = ImeConstants.getStateFilePath(ImeType.RIME),
            logger = logger,
            onStateChanged = { state -> onImeStateChanged(state) }
        )
    }

    /**
     * 启动状态监听
     */
    fun start() {
        stateWatcher.start()
    }

    /**
     * 停止状态监听
     */
    fun stop() {
        stateWatcher.stop()
    }

    override suspend fun setAsciiMode(ascii: Boolean) {
        // 如果已是目标模式且非大写，跳过
        if (currentAsciiMode == ascii && !currentCapsMode) {
            return
        }

        // 如果当前是大写模式且切换到英文，先退出大写
        if (currentCapsMode && ascii) {
            logger.debug("Exiting caps mode before switching to ASCII")
            exitCapsMode()
        }

        currentAsciiMode = ascii
        switchImeMode(if (ascii) "/ascii" else "/nascii", if (ascii) "ASCII" else "Chinese")
    }

    override suspend fun setCapsMode() {
        if (currentCapsMode) {
            logger.debug("IME already in Caps mode, skipping")
            return
        }

        val eventsSent = CapsLockController.toggleCapsLock()
        if (eventsSent == 2) {
            currentCapsMode = true
            currentAsciiMode = false
            logger.info("Caps mode activated via SendInput ($eventsSent events)")
        } else {
            logger.warn("Failed to toggle CapsLock via SendInput (sent $eventsSent events)")
        }
    }

    private fun exitCapsMode() {
        if (!currentCapsMode) return

        val eventsSent = CapsLockController.toggleCapsLock()
        if (eventsSent == 2) {
            currentCapsMode = false
            logger.info("Exited caps mode via SendInput ($eventsSent events)")
        }
    }

    override suspend fun isComposing(): Boolean {
        return stateWatcher.isComposing
    }

    override fun getTrackedState(): ImeState {
        return ImeState(currentAsciiMode, currentCapsMode)
    }

    override fun syncTrackedState(ascii: Boolean, caps: Boolean) {
        currentAsciiMode = ascii
        currentCapsMode = caps
        logger.debug("Synced tracked state: ascii=$ascii, caps=$caps")
    }

    override fun dispose() {
        stateWatcher.stop()
    }

    private fun onImeStateChanged(state: ImeState) {
        syncTrackedState(state.isAsciiMode, state.isCapsLock)
    }

    private fun switchImeMode(arg: String, label: String) {
        val path = weaselServerPath
        if (path == null) {
            logger.warn("WeaselServer.exe not found")
            return
        }

        if (!File(path).exists()) {
            logger.warn("WeaselServer.exe not exists at: $path")
            return
        }

        try {
            logger.debug("Executing: $path $arg")
            val process = ProcessBuilder(path, arg)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            val exited = process.waitFor(1, TimeUnit.SECONDS)
            if (exited) {
                logger.info("Switched to $label mode (exitCode=${process.exitValue()})")
            } else {
                logger.warn("Switch to $label mode timed out")
                process.destroy()
            }
        } catch (e: Exception) {
            logger.warn("Failed to switch IME mode: $arg", e)
        }
    }
}
