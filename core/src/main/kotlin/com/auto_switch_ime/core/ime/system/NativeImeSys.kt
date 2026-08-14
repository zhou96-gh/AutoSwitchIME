package com.auto_switch_ime.core.ime.system

import com.auto_switch_ime.core.util.Logger
import com.sun.jna.Library
import com.sun.jna.Native
import java.io.File
import java.io.FileOutputStream

object NativeImeSys {

    private var loaded = false

    interface ImeSysLib : Library {
        fun ime_caps_read(): Int
        fun ime_caps_toggle(): Int
        fun ime_caps_set(on: Int): Int
        fun ime_foreground_window(): Long
        fun ime_foreground_process_id(): Int
        fun ime_get_conversion_status(): Long
        fun ime_set_ascii_mode(ascii: Int): Int
        fun ime_is_composing(): Int
    }

    private var lib: ImeSysLib? = null

    fun load(logger: Logger? = null) {
        if (loaded) return
        try {
            val dllPath = extractDll()
            lib = Native.load(dllPath, ImeSysLib::class.java)
            loaded = true
            logger?.info("NativeImeSys loaded: ime_sys.dll")
        } catch (e: Exception) {
            logger?.warn("Failed to load ime_sys.dll, falling back to JNA", e)
        }
    }

    fun isAvailable(): Boolean = loaded && lib != null

    fun imeCapsRead(): Boolean {
        return lib?.ime_caps_read()?.let { it != 0 } ?: false
    }

    fun imeCapsReadOrNull(): Boolean? {
        if (!isAvailable()) return null
        return imeCapsRead()
    }

    fun imeCapsToggle(): Boolean {
        return lib?.ime_caps_toggle()?.let { it != 0 } ?: false
    }

    fun imeCapsSet(on: Boolean): Boolean {
        return lib?.ime_caps_set(if (on) 1 else 0)?.let { it != 0 } ?: false
    }

    fun imeForegroundProcessId(): Long {
        return try {
            lib?.ime_foreground_process_id()?.toLong()?.and(0xFFFF_FFFFL) ?: 0L
        } catch (_: UnsatisfiedLinkError) {
            0L
        }
    }

    fun imeIsComposing(): Int {
        return try {
            lib?.ime_is_composing() ?: -1
        } catch (_: UnsatisfiedLinkError) {
            -1
        }
    }

    fun imeGetSystemStatus(): SystemImeStatus? {
        val packed = try {
            lib?.ime_get_conversion_status() ?: return null
        } catch (_: UnsatisfiedLinkError) {
            return null
        }
        return decodeSystemImeStatus(packed)
    }

    fun imeSetAsciiMode(ascii: Boolean): Boolean {
        return try {
            lib?.ime_set_ascii_mode(if (ascii) 1 else 0)?.let { it != 0 } ?: false
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    internal fun decodeSystemImeStatus(packed: Long): SystemImeStatus? {
        if (packed < 0) return null
        val conversionMode = packed and 0xFFFF_FFFFL
        val isOpen = packed and (1L shl 32) != 0L
        return SystemImeStatus(
            isOpen = isOpen,
            isAsciiMode = !isOpen || conversionMode and 0x01L == 0L,
            conversionMode = conversionMode
        )
    }

    private fun extractDll(): String {
        val resource = "/native/ime_sys.dll"
        val dest = File(
            System.getProperty("java.io.tmpdir"),
            "ime_sys/${ProcessHandle.current().pid()}/ime_sys.dll"
        )
        if (dest.exists()) return dest.absolutePath

        dest.parentFile.mkdirs()
        val url = NativeImeSys::class.java.getResource(resource)
            ?: throw RuntimeException("Native library not found in classpath: $resource")
        url.openStream().use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
        dest.deleteOnExit()
        return dest.absolutePath
    }
}

data class SystemImeStatus(
    val isOpen: Boolean,
    val isAsciiMode: Boolean,
    val conversionMode: Long
)
