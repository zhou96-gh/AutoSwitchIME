package com.auto_switch_ime.core.ime.input

import com.auto_switch_ime.core.ImeConfig
import com.auto_switch_ime.core.ImeException
import com.auto_switch_ime.core.ImeProvider
import com.auto_switch_ime.core.ImeType
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
        override fun start() = Unit
        override fun dispose() = Unit
    }

    private object FakeLogger : Logger {
        override fun debug(msg: String) = Unit
        override fun info(msg: String) = Unit
        override fun warn(msg: String, e: Throwable?) = Unit
        override fun error(msg: String, e: Throwable?) = Unit
    }
}
