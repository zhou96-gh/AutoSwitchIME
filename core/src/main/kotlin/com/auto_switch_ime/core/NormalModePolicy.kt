package com.auto_switch_ime.core

object NormalModePolicy {
    fun resolveAction(
        normalLike: Boolean,
        strictNormal: Boolean,
        defaultApplied: Boolean
    ): ImeAction? {
        if (!normalLike) return null
        if (strictNormal || !defaultApplied) return ImeAction.ENGLISH
        return ImeAction.UNCHANGED
    }

    fun shouldEnforceEnglish(strictNormal: Boolean, asciiMode: Boolean, capsLock: Boolean): Boolean {
        return strictNormal && (!asciiMode || capsLock)
    }
}
