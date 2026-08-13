package com.auto_switch_ime.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InputDisplayModeTest {

    @Test
    fun `display mode depends only on input state`() {
        assertEquals(InputDisplayMode.ENGLISH, ImeState(true, false).toInputDisplayMode())
        assertEquals(InputDisplayMode.CHINESE, ImeState(false, false).toInputDisplayMode())
        assertEquals(InputDisplayMode.CAPS, ImeState(true, true).toInputDisplayMode())
        assertEquals(InputDisplayMode.CAPS, ImeState(false, true).toInputDisplayMode())
    }
}
