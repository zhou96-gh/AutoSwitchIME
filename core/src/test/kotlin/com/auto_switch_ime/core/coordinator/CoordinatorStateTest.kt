package com.auto_switch_ime.core.coordinator

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoordinatorStateTest {

    @Test
    fun `new active editor invalidates previous request`() {
        val state = CoordinatorState<String>()
        state.focusEditor("editor-a")
        val requestA = state.newRequest("editor-a")

        state.focusEditor("editor-b")
        val requestB = state.newRequest("editor-b")

        assertNotNull(requestA)
        assertNotNull(requestB)
        assertFalse(state.isCurrent(requestA!!))
        assertTrue(state.isCurrent(requestB!!))
    }

    @Test
    fun `focus loss invalidates automatic requests`() {
        val state = CoordinatorState<String>()
        state.focusEditor("editor-a")
        val request = state.newRequest("editor-a")!!

        state.loseFocus()

        assertFalse(state.isCurrent(request))
        assertNull(state.newRequest("editor-a"))
    }

    @Test
    fun `focusing same editor keeps current request valid`() {
        val state = CoordinatorState<String>()
        state.focusEditor("editor-a")
        val request = state.newRequest("editor-a")!!

        state.focusEditor("editor-a")

        assertTrue(state.isCurrent(request))
    }

    @Test
    fun `explicit invalidation makes current request stale`() {
        val state = CoordinatorState<String>()
        state.focusEditor("editor-a")
        val request = state.newRequest("editor-a")!!

        state.invalidateRequests()

        assertFalse(state.isCurrent(request))
    }

    @Test
    fun `focus loss from inactive editor does not invalidate current request`() {
        val state = CoordinatorState<String>()
        state.focusEditor("editor-b")
        val request = state.newRequest("editor-b")!!

        state.loseFocus("editor-a")

        assertTrue(state.isCurrent(request))
    }

    @Test
    fun `shutdown rejects new requests`() {
        val state = CoordinatorState<String>()
        state.focusEditor("editor-a")

        state.shutdown()

        assertTrue(state.isShuttingDown())
        assertNull(state.newRequest("editor-a"))
    }
}
