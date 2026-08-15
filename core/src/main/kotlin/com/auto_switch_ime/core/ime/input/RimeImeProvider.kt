package com.auto_switch_ime.core.ime.input

import com.auto_switch_ime.core.ImeAsciiModeSwitcher
import com.auto_switch_ime.core.ImeConfig
import com.auto_switch_ime.core.ImeProvider
import com.auto_switch_ime.core.ImePartialState
import com.auto_switch_ime.core.ImeStateSource
import com.auto_switch_ime.core.ImeType
import com.auto_switch_ime.core.ime.system.NativeImeSys
import com.auto_switch_ime.core.util.Logger
import java.io.File
import java.util.concurrent.TimeUnit

/** Rime 通过 Lua 共享内存提供中英文/输入中状态，并使用 Weasel 指令切换中英文。 */
class RimeImeProvider(
    config: ImeConfig,
    private val logger: Logger
) : ImeProvider, ImeAsciiModeSwitcher, ImeStateSource {
    override val type: ImeType = ImeType.RIME
    override val name: String = "Rime/Weasel"
    override val asciiModeSwitcher: ImeAsciiModeSwitcher = this
    override val stateSource: ImeStateSource = this

    private val weaselServerPath = config.weaselServerPath ?: WeaselPathDetector.detect(logger)

    @Volatile
    private var stateAvailable = false

    override fun start() = Unit

    override fun readState(): ImePartialState {
        val actual = NativeImeSys.imeGetRimeState()
        stateAvailable = actual != null
        return actual?.let {
            ImePartialState(isAsciiMode = it.isAsciiMode, isComposing = it.isComposing)
        } ?: ImePartialState()
    }

    override fun isAvailable(): Boolean = stateAvailable

    override fun supportsChangeNotifications(): Boolean = true

    override fun waitForStateChange(timeoutMillis: Int): Boolean {
        val result = NativeImeSys.imeWaitForRimeStateChange(timeoutMillis)
        if (result < 0) {
            try {
                Thread.sleep(timeoutMillis.toLong())
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        return result > 0
    }

    override suspend fun switchAsciiMode(
        ascii: Boolean,
        shouldContinue: () -> Boolean
    ): Boolean {
        val path = weaselServerPath
        if (path == null) {
            logger.warn("WeaselServer.exe not found")
            return false
        }
        if (!File(path).exists()) {
            logger.warn("WeaselServer.exe not exists at: $path")
            return false
        }

        val argument = if (ascii) "/ascii" else "/nascii"
        val label = if (ascii) "ASCII" else "Chinese"
        return try {
            if (!shouldContinue()) return false
            logger.debug("Executing: $path $argument")
            val process = ProcessBuilder(path, argument)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            val exited = process.waitFor(1, TimeUnit.SECONDS)
            if (exited) {
                logger.info("Switched to $label mode (exitCode=${process.exitValue()})")
                process.exitValue() == 0
            } else {
                logger.warn("Switch to $label mode timed out")
                process.destroy()
                false
            }
        } catch (e: Exception) {
            logger.warn("Failed to switch IME mode: $argument", e)
            false
        }
    }

    override fun dispose() = Unit
}
