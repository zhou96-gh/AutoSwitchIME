package com.auto_switch_ime.ime

import com.auto_switch_ime.adapter.IntelliJLogger
import com.auto_switch_ime.core.*
import com.auto_switch_ime.core.ime.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.runBlocking

@Service
class AutoSwitchIMEController {
    private val logger = IntelliJLogger
    private val provider: RimeImeProvider by lazy {
        val config = ImeConfig(type = ImeType.RIME)
        RimeImeProvider(config, logger).also { it.start() }
    }

    val stateWatcher: StateWatcher get() = provider.stateWatcher

    fun getTrackedState(): ImeState = provider.getTrackedState()

    fun setAsciiMode(ascii: Boolean) {
        runBlocking { provider.setAsciiMode(ascii) }
    }

    fun setCapsMode() {
        runBlocking { provider.setCapsMode() }
    }

    fun resolvePath(): String? {
        return WeaselPathDetector.detect(logger)
    }

    fun dispose() {
        try {
            provider.dispose()
        } catch (e: Exception) {
            logger.warn("Error disposing AutoSwitchIMEController", e)
        }
    }

    companion object {
        fun getInstance(): AutoSwitchIMEController = service()
    }
}
