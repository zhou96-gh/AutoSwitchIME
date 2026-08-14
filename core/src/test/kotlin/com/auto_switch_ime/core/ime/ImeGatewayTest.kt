package com.auto_switch_ime.core.ime

import com.auto_switch_ime.core.ImeAsciiModeSwitcher
import com.auto_switch_ime.core.ImeProvider
import com.auto_switch_ime.core.ImeStateSource
import com.auto_switch_ime.core.ImeType
import com.auto_switch_ime.core.ime.system.SystemImeProvider
import com.auto_switch_ime.core.ime.system.SystemImeProviderRegistry
import com.auto_switch_ime.core.ime.system.SystemType
import com.auto_switch_ime.core.util.Logger
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImeGatewayTest {
    @Test
    fun `system state is the default for capabilities not supplied by input method`() {
        val system = FakeSystem(ascii = true, caps = true, composing = false)
        val gateway = ImeGateway(FakeProvider(), system, FakeLogger)

        assertEquals(
            com.auto_switch_ime.core.ImeState(true, true, false),
            gateway.getCurrentState()
        )
    }

    @Test
    fun `input method state overrides only fields it supplies`() {
        val source = object : ImeStateSource {
            override fun readAsciiMode(): Boolean = false
        }
        val system = FakeSystem(ascii = true, caps = true, composing = true)
        val gateway = ImeGateway(FakeProvider(stateSource = source), system, FakeLogger)

        assertEquals(
            com.auto_switch_ime.core.ImeState(false, true, true),
            gateway.getCurrentState()
        )
    }

    @Test
    fun `missing input method switcher falls back to system switcher`() {
        val system = FakeSystem(ascii = false)
        val gateway = ImeGateway(FakeProvider(), system, FakeLogger)
        gateway.refreshState()

        runSuspend { gateway.setAsciiMode(true) }

        assertEquals(1, system.asciiSwitchCount)
        assertTrue(gateway.getTrackedState().isAsciiMode)
    }

    @Test
    fun `input method switcher takes precedence over system switcher`() {
        val switcher = FakeAsciiSwitcher(succeeds = true)
        val system = FakeSystem(ascii = false)
        val gateway = ImeGateway(FakeProvider(switcher = switcher), system, FakeLogger)
        gateway.refreshState()

        runSuspend { gateway.setAsciiMode(true) }

        assertEquals(1, switcher.callCount)
        assertEquals(0, system.asciiSwitchCount)
    }

    @Test
    fun `failed input method switch does not fall back to system switcher`() {
        val switcher = FakeAsciiSwitcher(succeeds = false)
        val system = FakeSystem(ascii = false)
        val gateway = ImeGateway(FakeProvider(switcher = switcher), system, FakeLogger)
        gateway.refreshState()

        runSuspend { gateway.setAsciiMode(true) }

        assertEquals(1, switcher.callCount)
        assertEquals(0, system.asciiSwitchCount)
        assertFalse(gateway.getTrackedState().isAsciiMode)
    }

    @Test
    fun `system providers are selected through registry`() {
        val registry = SystemImeProviderRegistry()
        val windows = FakeSystem()
        registry.register(SystemType.WINDOWS) { windows }

        assertEquals(windows, registry.create(SystemType.WINDOWS))
        assertThrows(IllegalStateException::class.java) {
            registry.create(SystemType.LINUX)
        }
        assertEquals(SystemType.WINDOWS, SystemType.current("Windows 11"))
        assertEquals(SystemType.MACOS, SystemType.current("Mac OS X"))
        assertEquals(SystemType.LINUX, SystemType.current("Linux"))
    }

    private class FakeProvider(
        override val stateSource: ImeStateSource? = null,
        switcher: ImeAsciiModeSwitcher? = null
    ) : ImeProvider {
        override val type = ImeType.RIME
        override val name = "fake"
        override val asciiModeSwitcher = switcher
        override fun start() = Unit
        override fun dispose() = Unit
    }

    private class FakeAsciiSwitcher(private val succeeds: Boolean) : ImeAsciiModeSwitcher {
        var callCount = 0

        override suspend fun switchAsciiMode(
            ascii: Boolean,
            shouldContinue: () -> Boolean
        ): Boolean {
            callCount++
            return succeeds && shouldContinue()
        }
    }

    private class FakeSystem(
        private var ascii: Boolean = true,
        private var caps: Boolean = false,
        private var composing: Boolean = false
    ) : SystemImeProvider {
        override val type = SystemType.WINDOWS
        var asciiSwitchCount = 0

        override fun readAsciiMode(): Boolean = ascii
        override fun readCapsLock(): Boolean = caps
        override fun readComposing(): Boolean = composing

        override suspend fun switchAsciiMode(
            ascii: Boolean,
            shouldContinue: () -> Boolean
        ): Boolean {
            asciiSwitchCount++
            if (!shouldContinue()) return false
            this.ascii = ascii
            return true
        }

        override suspend fun switchCapsLock(
            enabled: Boolean,
            shouldContinue: () -> Boolean
        ): Boolean {
            if (!shouldContinue()) return false
            caps = enabled
            return true
        }
    }

    private object FakeLogger : Logger {
        override fun debug(msg: String) = Unit
        override fun info(msg: String) = Unit
        override fun warn(msg: String, e: Throwable?) = Unit
        override fun error(msg: String, e: Throwable?) = Unit
    }

    private fun runSuspend(block: suspend () -> Unit) {
        var outcome: Result<Unit>? = null
        block.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<Unit>) {
                outcome = result
            }
        })
        outcome?.getOrThrow() ?: error("Suspended test did not complete synchronously")
    }
}
