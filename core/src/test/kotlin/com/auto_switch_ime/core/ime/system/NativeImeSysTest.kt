package com.auto_switch_ime.core.ime.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NativeImeSysTest {

    @Test
    fun `zero conversion mode is valid ascii state`() {
        val status = NativeImeSys.decodeSystemImeStatus(0)

        assertEquals(0, status?.conversionMode)
        assertTrue(status?.isAsciiMode == true)
        assertFalse(status?.isOpen == true)
    }

    @Test
    fun `native flag maps to chinese state`() {
        val status = NativeImeSys.decodeSystemImeStatus((1L shl 32) or 0x781)

        assertTrue(status?.isOpen == true)
        assertFalse(status?.isAsciiMode == true)
        assertEquals(0x781, status?.conversionMode)
    }

    @Test
    fun `closed ime maps native flags to ascii input`() {
        val status = NativeImeSys.decodeSystemImeStatus(0x781)

        assertFalse(status?.isOpen == true)
        assertTrue(status?.isAsciiMode == true)
    }

    @Test
    fun `negative result is unavailable`() {
        assertNull(NativeImeSys.decodeSystemImeStatus(-1))
    }

    @Test
    fun `rime state decodes flags and event sequence`() {
        val state = NativeImeSys.decodeRimeInputState((17L shl 2) or 0x03)

        assertTrue(state?.isAsciiMode == true)
        assertTrue(state?.isComposing == true)
        assertEquals(17, state?.eventSequence)
    }

    @Test
    fun `negative rime state is unavailable`() {
        assertNull(NativeImeSys.decodeRimeInputState(-1))
    }
}
