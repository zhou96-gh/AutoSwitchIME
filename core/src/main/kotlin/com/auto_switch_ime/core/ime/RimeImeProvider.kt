package com.auto_switch_ime.core.ime

import com.auto_switch_ime.core.*
import com.auto_switch_ime.core.util.Logger
import java.io.File
import java.util.concurrent.TimeUnit

class RimeImeProvider(
    private val config: ImeConfig,
    private val logger: Logger
) : ImeProvider {

    override val name: String = "Rime/Weasel"

    @Volatile
    private var currentAsciiMode: Boolean = true

    @Volatile
    private var ownsCapsLock: Boolean = false

    private val switchLock = Any()

    /** 状态文件变化回调 */
    var onStateChanged: ((ImeState) -> Unit)? = null

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

    fun start() {
        stateWatcher.start()
    }

    fun stop() {
        stateWatcher.stop()
    }

    override suspend fun setAsciiMode(ascii: Boolean) {
        synchronized(switchLock) {
            stateWatcher.isForcingImeSwitch = true
            try {
                val capsOn = NativeImeSys.imeCapsRead()

                if (currentAsciiMode == ascii && !capsOn) return

                if (capsOn && ownsCapsLock) {
                    logger.debug("Exiting caps mode before switching IME mode")
                    NativeImeSys.imeCapsSet(false)
                    ownsCapsLock = false
                }

                if (currentAsciiMode == ascii) return

                currentAsciiMode = ascii
                switchImeMode(if (ascii) "/ascii" else "/nascii", if (ascii) "ASCII" else "Chinese")
            } finally {
                stateWatcher.isForcingImeSwitch = false
            }
        }
    }

    override suspend fun setCapsMode() {
        synchronized(switchLock) {
            stateWatcher.isForcingImeSwitch = true
            try {
                val capsOnBefore = NativeImeSys.imeCapsRead()
                if (currentAsciiMode && capsOnBefore) return

                // Caps 模式 = WeaselServer 英文模式 + CapsLock 开启
                // 先确保 WeaselServer 在英文模式（/ascii），这样输出大写英文字母
                if (!currentAsciiMode) {
                    currentAsciiMode = true
                    switchImeMode("/ascii", "ASCII")
                }

                // 再开启 CapsLock
                if (!NativeImeSys.imeCapsRead()) {
                    NativeImeSys.imeCapsSet(true)
                    ownsCapsLock = NativeImeSys.imeCapsRead()
                } else {
                    ownsCapsLock = false
                }

                onStateChanged?.invoke(ImeState(true, NativeImeSys.imeCapsRead(), stateWatcher.isComposing))
                logger.info("Caps mode activated (ASCII + CapsLock)")
            } finally {
                stateWatcher.isForcingImeSwitch = false
            }
        }
    }

    override suspend fun releaseOwnedCapsLock() {
        synchronized(switchLock) {
            if (!ownsCapsLock) return
            if (NativeImeSys.imeCapsRead()) {
                NativeImeSys.imeCapsSet(false)
            }
            ownsCapsLock = false
            onStateChanged?.invoke(ImeState(currentAsciiMode, NativeImeSys.imeCapsRead(), stateWatcher.isComposing))
            logger.info("Released plugin-owned CapsLock")
        }
    }

    override suspend fun isComposing(): Boolean {
        return ImeStateDetector.isComposing(stateWatcher)
    }

    override fun getTrackedState(): ImeState {
        return ImeState(currentAsciiMode, NativeImeSys.imeCapsRead())
    }

    override fun syncTrackedState(ascii: Boolean, caps: Boolean) {
        currentAsciiMode = ascii
    }

    override fun dispose() {
        stateWatcher.stop()
    }

    private fun onImeStateChanged(state: ImeState) {
        currentAsciiMode = state.isAsciiMode
        onStateChanged?.invoke(state.copy(isCapsLock = NativeImeSys.imeCapsRead()))
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
