package com.auto_switch_ime.ime

import com.auto_switch_ime.adapter.IntelliJLogger
import com.auto_switch_ime.core.ImeConstants
import com.auto_switch_ime.core.ImeState
import com.auto_switch_ime.core.ImeType
import com.auto_switch_ime.core.ime.StateWatcher
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

@Service
class AutoSwitchIMEStateWatcher {
    private val stateWatcher = StateWatcher(
        stateFilePath = ImeConstants.getStateFilePath(ImeType.RIME),
        logger = IntelliJLogger,
        onStateChanged = { }
    )

    val isComposing: Boolean get() = stateWatcher.isComposing

    fun start() { stateWatcher.start() }
    fun stop() { stateWatcher.stop() }

    companion object {
        fun getInstance(): AutoSwitchIMEStateWatcher = service()
    }
}
