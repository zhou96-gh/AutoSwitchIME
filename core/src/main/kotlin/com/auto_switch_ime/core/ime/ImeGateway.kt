package com.auto_switch_ime.core.ime

import com.auto_switch_ime.core.ImeAsciiModeSwitcher
import com.auto_switch_ime.core.ImeCapsLockSwitcher
import com.auto_switch_ime.core.ImeProvider
import com.auto_switch_ime.core.ImeState
import com.auto_switch_ime.core.ime.system.SystemImeProvider
import com.auto_switch_ime.core.util.Logger

/**
 * 合并输入法级可选能力与系统级默认能力，并维护统一 ImeState。
 */
class ImeGateway(
    private val provider: ImeProvider,
    private val system: SystemImeProvider,
    private val logger: Logger
) {
    @Volatile
    private var trackedState = ImeState(isAsciiMode = true, isCapsLock = false)

    @Volatile
    private var ownsCapsLock = false

    private var lastPublishedState: ImeState? = null

    var onStateChanged: ((ImeState) -> Unit)? = null

    fun start() {
        provider.start()
    }

    fun getTrackedState(): ImeState = trackedState

    fun getCurrentState(): ImeState {
        refreshState()
        return trackedState
    }

    fun refreshState() {
        val specific = provider.stateSource
        trackedState = ImeState(
            isAsciiMode = specific?.readAsciiMode()
                ?: system.readAsciiMode()
                ?: trackedState.isAsciiMode,
            isCapsLock = specific?.readCapsLock()
                ?: system.readCapsLock()
                ?: trackedState.isCapsLock,
            isComposing = specific?.readComposing()
                ?: system.readComposing()
                ?: false
        )
        publishState()
    }

    fun isComposing(): Boolean {
        val composing = provider.stateSource?.readComposing()
            ?: system.readComposing()
            ?: false
        trackedState = trackedState.copy(isComposing = composing)
        publishState()
        return composing
    }

    suspend fun setAsciiMode(
        ascii: Boolean,
        shouldContinue: () -> Boolean = { true },
        forceLowercase: Boolean = false
    ) {
        if (!shouldContinue()) return
        if (trackedState.isCapsLock && (ownsCapsLock || forceLowercase)) {
            if (!switchCapsLock(false, shouldContinue)) return
            ownsCapsLock = false
            refreshState()
        }

        if (trackedState.isAsciiMode != ascii) {
            val switched = asciiSwitcher().switchAsciiMode(ascii, shouldContinue)
            if (!switched) {
                logger.warn("${provider.name} failed to switch ASCII mode to $ascii")
                return
            }
        }
        if (shouldContinue()) refreshState()
    }

    suspend fun ensureAsciiMode(shouldContinue: () -> Boolean = { true }) {
        if (!shouldContinue()) return
        if (!trackedState.isAsciiMode) {
            val switched = asciiSwitcher().switchAsciiMode(true, shouldContinue)
            if (!switched) {
                logger.warn("${provider.name} failed to ensure ASCII mode")
                return
            }
        }
        if (shouldContinue()) refreshState()
    }

    suspend fun setCapsMode(shouldContinue: () -> Boolean = { true }) {
        if (!shouldContinue()) return
        if (trackedState.isAsciiMode && trackedState.isCapsLock) return

        if (!trackedState.isAsciiMode) {
            val switched = asciiSwitcher().switchAsciiMode(true, shouldContinue)
            if (!switched) {
                logger.warn("${provider.name} failed to enter ASCII mode before CapsLock")
                return
            }
            refreshState()
        }
        if (!shouldContinue()) return

        if (!trackedState.isCapsLock) {
            ownsCapsLock = switchCapsLock(true, shouldContinue)
            if (!ownsCapsLock) {
                logger.warn("${provider.name} failed to enable CapsLock")
                return
            }
        } else {
            ownsCapsLock = false
        }
        if (shouldContinue()) refreshState()
    }

    suspend fun releaseOwnedCapsLock() {
        if (!ownsCapsLock) return
        switchCapsLock(false) { true }
        ownsCapsLock = false
        refreshState()
    }

    fun dispose() {
        provider.dispose()
    }

    private fun asciiSwitcher(): ImeAsciiModeSwitcher {
        return provider.asciiModeSwitcher ?: system
    }

    private fun capsLockSwitcher(): ImeCapsLockSwitcher {
        return provider.capsLockSwitcher ?: system
    }

    private suspend fun switchCapsLock(
        enabled: Boolean,
        shouldContinue: () -> Boolean
    ): Boolean {
        return capsLockSwitcher().switchCapsLock(enabled, shouldContinue)
    }

    private fun publishState() {
        if (trackedState == lastPublishedState) return
        lastPublishedState = trackedState
        onStateChanged?.invoke(trackedState)
    }
}
