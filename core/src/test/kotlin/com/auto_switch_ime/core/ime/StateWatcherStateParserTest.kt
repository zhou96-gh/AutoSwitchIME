package com.auto_switch_ime.core.ime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StateWatcherStateParserTest {

    @Test
    fun `parses complete state json`() {
        val state = parseImeStateJson(
            """{"ascii_mode":false,"caps_lock":true,"is_composing":true}"""
        )

        assertEquals(false, state?.isAsciiMode)
        assertEquals(true, state?.isCapsLock)
        assertEquals(true, state?.isComposing)
    }

    @Test
    fun `defaults optional caps and composing fields to false`() {
        val state = parseImeStateJson("""{"ascii_mode":true}""")

        assertTrue(state?.isAsciiMode == true)
        assertFalse(state?.isCapsLock == true)
        assertFalse(state?.isComposing == true)
    }

    @Test
    fun `returns null when ascii mode is missing`() {
        val state = parseImeStateJson("""{"caps_lock":true,"is_composing":false}""")

        assertNull(state)
    }

    @Test
    fun `returns null for incomplete json writes`() {
        val state = parseImeStateJson("""{"ascii_mode":""")

        assertNull(state)
    }
}
