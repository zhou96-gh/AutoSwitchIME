package com.auto_switch_ime.core

enum class InputDisplayMode {
    ENGLISH,
    CHINESE,
    CAPS
}

fun ImeState.toInputDisplayMode(): InputDisplayMode {
    return when {
        isCapsLock -> InputDisplayMode.CAPS
        isAsciiMode -> InputDisplayMode.ENGLISH
        else -> InputDisplayMode.CHINESE
    }
}
