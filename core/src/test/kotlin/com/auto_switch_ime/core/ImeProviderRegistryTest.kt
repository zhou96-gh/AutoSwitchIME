package com.auto_switch_ime.core

import com.auto_switch_ime.core.util.Logger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ImeProviderRegistryTest {

    @Test
    fun `unavailable or invalid config falls back to rime`() {
        assertEquals(ImeType.RIME, ImeType.fromConfig(null))
        assertEquals(ImeType.RIME, ImeType.fromConfig("invalid"))
        assertEquals(ImeType.RIME, ImeType.fromConfig(ImeType.SOGOU.configValue))
    }

    @Test
    fun `creates registered provider and rejects unsupported type`() {
        val registry = ImeProviderRegistry()
        registry.register(ImeType.RIME) { _, _ -> FakeProvider }

        assertEquals(FakeProvider, registry.createProvider(ImeConfig(ImeType.RIME), FakeLogger))
        assertThrows(ImeException.ProviderNotFound::class.java) {
            registry.createProvider(ImeConfig(ImeType.SOGOU), FakeLogger)
        }
    }

    private object FakeProvider : ImeProvider {
        override val type = ImeType.RIME
        override val name = "fake"
        override var onStateChanged: ((ImeState) -> Unit)? = null
        override fun start() = Unit
        override suspend fun setAsciiMode(ascii: Boolean, shouldContinue: () -> Boolean, forceLowercase: Boolean) = Unit
        override suspend fun ensureAsciiMode(shouldContinue: () -> Boolean) = Unit
        override suspend fun setCapsMode(shouldContinue: () -> Boolean) = Unit
        override suspend fun releaseOwnedCapsLock() = Unit
        override suspend fun isComposing(): Boolean = false
        override fun getTrackedState() = ImeState(true, false)
        override fun getCurrentState() = getTrackedState()
        override fun refreshState() = Unit
        override fun syncTrackedState(ascii: Boolean, caps: Boolean) = Unit
        override fun dispose() = Unit
    }

    private object FakeLogger : Logger {
        override fun debug(msg: String) = Unit
        override fun info(msg: String) = Unit
        override fun warn(msg: String, e: Throwable?) = Unit
        override fun error(msg: String, e: Throwable?) = Unit
    }
}
