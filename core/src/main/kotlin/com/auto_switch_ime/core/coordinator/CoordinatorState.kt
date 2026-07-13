package com.auto_switch_ime.core.coordinator

data class CoordinatorRequest<T>(
    val editorId: T,
    val generation: Long
)

class CoordinatorState<T> {
    private var activeEditorId: T? = null
    private var windowFocused = false
    private var enabled = true
    private var generation = 0L
    private var shuttingDown = false

    @Synchronized
    fun focusEditor(editorId: T) {
        if (shuttingDown) return
        if (windowFocused && activeEditorId == editorId) return
        activeEditorId = editorId
        windowFocused = true
        generation++
    }

    @Synchronized
    fun loseFocus(): Boolean {
        clearFocus()
        return true
    }

    @Synchronized
    fun loseFocus(editorId: T): Boolean {
        if (activeEditorId != editorId) return false
        clearFocus()
        return true
    }

    private fun clearFocus() {
        activeEditorId = null
        windowFocused = false
        generation++
    }

    @Synchronized
    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (!value) {
            activeEditorId = null
        }
        generation++
    }

    @Synchronized
    fun newRequest(editorId: T): CoordinatorRequest<T>? {
        if (shuttingDown || !enabled || !windowFocused || activeEditorId != editorId) {
            return null
        }
        generation++
        return CoordinatorRequest(editorId, generation)
    }

    @Synchronized
    fun invalidateRequests() {
        generation++
    }

    @Synchronized
    fun isCurrent(request: CoordinatorRequest<T>, platformFocused: Boolean): Boolean {
        return !shuttingDown &&
                enabled &&
                windowFocused &&
                platformFocused &&
                activeEditorId == request.editorId &&
                generation == request.generation
    }

    @Synchronized
    fun shutdown() {
        if (shuttingDown) return
        shuttingDown = true
        activeEditorId = null
        windowFocused = false
        generation++
    }

    @Synchronized
    fun isShuttingDown(): Boolean = shuttingDown
}
