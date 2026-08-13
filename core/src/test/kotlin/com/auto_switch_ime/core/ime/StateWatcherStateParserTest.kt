package com.auto_switch_ime.core.ime

import com.auto_switch_ime.core.ImeState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StateWatcherStateParserTest {

    @Test
    fun `parses complete protocol v2 state json`() {
        val state = parseRimeSessionStateJson(
            """{"protocol_version":2,"provider":"rime","session_token":"s1","sequence":3,"ascii_mode":false,"caps_lock":true,"is_composing":true,"timestamp":1730000000}"""
        )

        assertEquals(false, state?.state?.isAsciiMode)
        assertEquals(true, state?.state?.isCapsLock)
        assertEquals(true, state?.state?.isComposing)
        assertEquals("s1", state?.sessionToken)
        assertEquals(3, state?.sequence)
    }

    @Test
    fun `returns null for incomplete json writes`() {
        val state = parseRimeSessionStateJson("""{"ascii_mode":""")

        assertNull(state)
    }

    @Test
    fun `rejects protocol v1 state`() {
        assertNull(
            parseRimeSessionStateJson(
                """{"ascii_mode":false,"caps_lock":true,"is_composing":true}"""
            )
        )
    }

    @Test
    fun `rejects incomplete session aware state`() {
        val update = parseRimeSessionStateJson(
            """{"protocol_version":2,"provider":"rime","sequence":1,"ascii_mode":true,"caps_lock":false,"is_composing":false,"timestamp":1730000000}"""
        )

        assertNull(update)
        assertNull(
            parseRimeSessionStateJson(
                """{"protocol_version":2,"provider":"rime","session_token":"s1","sequence":0,"ascii_mode":true,"caps_lock":false,"is_composing":false,"timestamp":1730000000}"""
            )
        )
    }

    @Test
    fun `tracker filters stale sequence and accepts a new session`() {
        val tracker = RimeSessionTracker()
        val state = ImeState(isAsciiMode = true, isCapsLock = false)

        assertTrue(tracker.accept(RimeSessionState(state, "s1", 1)))
        assertFalse(tracker.accept(RimeSessionState(state, "s1", 1)))
        assertTrue(tracker.accept(RimeSessionState(state, "s2", 1)))
    }
}
