package com.auto_switch_ime.core.ime

import com.auto_switch_ime.core.ImeCapsLockSwitcher
import com.auto_switch_ime.core.ImeProvider
import com.auto_switch_ime.core.ImeState
import com.auto_switch_ime.core.ime.system.SystemImeProvider
import com.auto_switch_ime.core.util.Logger
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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

    @Volatile
    private var stateSourceAvailable = provider.stateSource == null

    private var availabilityKnown = provider.stateSource == null

    private var lastPublishedState: ImeState? = null

    private val watchingStateChanges = AtomicBoolean(false)
    private val stateChangeExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AutoSwitchIME-StateSource").apply { isDaemon = true }
    }

    var onStateChanged: ((ImeState) -> Unit)? = null

    var onStateSourceAvailabilityChanged: ((Boolean) -> Unit)? = null

    /** 原生通知只唤醒平台层；平台完成焦点门禁后再调用 refreshState。 */
    var onStateChangeSignal: (() -> Unit)? = null

    fun start() {
        provider.start()
        startStateChangeWatcher()
    }

    fun getTrackedState(): ImeState = trackedState

    fun isStateSourceAvailable(): Boolean = stateSourceAvailable

    fun supportsStateChangeNotifications(): Boolean {
        return provider.stateSource?.supportsChangeNotifications() == true
    }

    fun getCurrentState(observedCapsLock: Boolean? = null): ImeState {
        refreshState(observedCapsLock)
        return trackedState
    }

    fun refreshState(observedCapsLock: Boolean? = null) {
        val source = provider.stateSource
        val specific = source?.readState()
        updateStateSourceAvailability(source?.isAvailable() ?: true)
        if (!stateSourceAvailable) return

        val systemAsciiMode = system.readAsciiMode()
        trackedState = ImeState(
            isAsciiMode = specific?.isAsciiMode
                ?: systemAsciiMode
                ?: trackedState.isAsciiMode,
            isCapsLock = specific?.isCapsLock
                ?: observedCapsLock
                ?: system.readCapsLock()
                ?: trackedState.isCapsLock,
            isComposing = specific?.isComposing
                ?: system.readComposing()
                ?: false
        )
        publishState()
    }

    fun isComposing(): Boolean {
        if (!stateSourceAvailable) return false
        val composing = provider.stateSource?.readState()?.isComposing
            ?: system.readComposing()
            ?: false
        trackedState = trackedState.copy(isComposing = composing)
        publishState()
        return composing
    }

    suspend fun setAsciiMode(
        ascii: Boolean,
        shouldContinue: () -> Boolean = { true },
        forceAsciiMode: Boolean = false
    ) {
        if (!stateSourceAvailable || !shouldContinue()) return
        if (trackedState.isCapsLock && (ownsCapsLock || forceAsciiMode)) {
            if (!switchCapsLock(false, shouldContinue)) {
                logger.warn("${provider.name} failed to disable CapsLock")
                return
            }
            logger.info("${provider.name} disabled CapsLock")
            ownsCapsLock = false
            refreshState()
        }

        val shouldSwitch = trackedState.isAsciiMode != ascii ||
            forceAsciiMode ||
            hasUnobservedProviderAsciiState()
        if (shouldSwitch) {
            val switched = switchAsciiMode(ascii, shouldContinue)
            if (!switched) {
                logger.warn("${provider.name} failed to switch ASCII mode to $ascii")
                return
            }
        }
        if (shouldContinue()) refreshState()
    }

    suspend fun ensureAsciiMode(shouldContinue: () -> Boolean = { true }) {
        if (!stateSourceAvailable || !shouldContinue()) return
        if (!trackedState.isAsciiMode || hasUnobservedProviderAsciiState()) {
            val switched = switchAsciiMode(true, shouldContinue)
            if (!switched) {
                logger.warn("${provider.name} failed to ensure ASCII mode")
                return
            }
        }
        if (shouldContinue()) refreshState()
    }

    suspend fun setCapsMode(shouldContinue: () -> Boolean = { true }) {
        if (!stateSourceAvailable || !shouldContinue()) return
        if (trackedState.isAsciiMode && trackedState.isCapsLock) return

        if (!trackedState.isAsciiMode || hasUnobservedProviderAsciiState()) {
            val switched = switchAsciiMode(true, shouldContinue)
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
        watchingStateChanges.set(false)
        stateChangeExecutor.shutdownNow()
        try {
            stateChangeExecutor.awaitTermination(2, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        provider.dispose()
    }

    private fun hasUnobservedProviderAsciiState(): Boolean {
        return provider.asciiModeSwitcher != null &&
            provider.stateSource?.readState()?.isAsciiMode == null
    }

    private suspend fun switchAsciiMode(
        ascii: Boolean,
        shouldContinue: () -> Boolean
    ): Boolean {
        return (provider.asciiModeSwitcher ?: system).switchAsciiMode(ascii, shouldContinue)
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
        if (!stateSourceAvailable) return
        if (trackedState == lastPublishedState) return
        lastPublishedState = trackedState
        onStateChanged?.invoke(trackedState)
    }

    private fun updateStateSourceAvailability(available: Boolean) {
        if (availabilityKnown && stateSourceAvailable == available) return
        availabilityKnown = true
        stateSourceAvailable = available
        if (available) {
            logger.info("${provider.name} state source available; AutoSwitchIME resumed")
        } else {
            logger.warn("${provider.name} state source unavailable; AutoSwitchIME suspended")
        }
        onStateSourceAvailabilityChanged?.invoke(available)
    }

    private fun startStateChangeWatcher() {
        val source = provider.stateSource ?: return
        if (!source.supportsChangeNotifications() || !watchingStateChanges.compareAndSet(false, true)) {
            return
        }
        stateChangeExecutor.execute {
            while (watchingStateChanges.get()) {
                val changed = source.waitForStateChange(1000)
                if (!watchingStateChanges.get()) break
                if (changed || !stateSourceAvailable) {
                    onStateChangeSignal?.invoke()
                }
            }
        }
    }
}
