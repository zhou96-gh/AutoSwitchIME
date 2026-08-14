package com.auto_switch_ime.core.ime.system

import com.auto_switch_ime.core.ImeAsciiModeSwitcher
import com.auto_switch_ime.core.ImeCapsLockSwitcher
import com.auto_switch_ime.core.ImeStateSource

enum class SystemType {
    WINDOWS,
    MACOS,
    LINUX,
    UNKNOWN;

    companion object {
        fun current(osName: String = System.getProperty("os.name")): SystemType {
            val normalized = osName.lowercase()
            return when {
                normalized.contains("win") -> WINDOWS
                normalized.contains("mac") -> MACOS
                normalized.contains("linux") -> LINUX
                else -> UNKNOWN
            }
        }
    }
}

/** 操作系统级默认能力；不同操作系统通过独立实现扩展。 */
interface SystemImeProvider : ImeStateSource, ImeAsciiModeSwitcher, ImeCapsLockSwitcher {
    val type: SystemType
}

class SystemImeProviderRegistry {
    private val providers = mutableMapOf<SystemType, () -> SystemImeProvider>()

    fun register(type: SystemType, factory: () -> SystemImeProvider) {
        providers[type] = factory
    }

    fun create(type: SystemType): SystemImeProvider {
        val factory = providers[type]
            ?: throw IllegalStateException("System IME provider not found for type: $type")
        return factory()
    }

    fun supportedTypes(): Set<SystemType> = providers.keys.toSet()
}

class WindowsSystemImeProvider : SystemImeProvider {
    override val type: SystemType = SystemType.WINDOWS

    override fun readAsciiMode(): Boolean? = NativeImeSys.imeGetSystemStatus()?.isAsciiMode

    override fun readCapsLock(): Boolean? = NativeImeSys.imeCapsReadOrNull()

    override fun readComposing(): Boolean = NativeImeSys.imeIsComposing() == 1

    override suspend fun switchAsciiMode(
        ascii: Boolean,
        shouldContinue: () -> Boolean
    ): Boolean {
        repeat(3) {
            if (!shouldContinue()) return false
            if (NativeImeSys.imeSetAsciiMode(ascii)) return true
            Thread.sleep(50)
        }
        return false
    }

    override suspend fun switchCapsLock(
        enabled: Boolean,
        shouldContinue: () -> Boolean
    ): Boolean {
        repeat(5) {
            if (!shouldContinue()) return false
            if (NativeImeSys.imeCapsReadOrNull() == enabled) return true
            NativeImeSys.imeCapsSet(enabled)
            Thread.sleep(50)
        }
        return NativeImeSys.imeCapsReadOrNull() == enabled
    }
}
