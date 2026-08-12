package com.auto_switch_ime.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NormalModePolicyTest {
    @Test
    fun `strict normal always requires english`() {
        assertEquals(ImeAction.ENGLISH, NormalModePolicy.resolveAction(true, true, false))
        assertEquals(ImeAction.ENGLISH, NormalModePolicy.resolveAction(true, true, true))
    }

    @Test
    fun `other normal-like modes allow manual switching after default`() {
        assertEquals(ImeAction.ENGLISH, NormalModePolicy.resolveAction(true, false, false))
        assertEquals(ImeAction.UNCHANGED, NormalModePolicy.resolveAction(true, false, true))
    }

    @Test
    fun `insert mode delegates to context rules`() {
        assertNull(NormalModePolicy.resolveAction(false, false, false))
    }

    @Test
    fun `manual chinese or caps switch is rejected only in strict normal`() {
        assertEquals(true, NormalModePolicy.shouldEnforceEnglish(true, false, false))
        assertEquals(true, NormalModePolicy.shouldEnforceEnglish(true, true, true))
        assertEquals(false, NormalModePolicy.shouldEnforceEnglish(true, true, false))
        assertEquals(false, NormalModePolicy.shouldEnforceEnglish(false, false, true))
    }
}
