package com.auto_switch_ime.core

/**
 * 输入法专用能力容器。
 *
 * Provider 只实现与系统默认行为不同的能力；未提供的能力由 ImeGateway 交给系统级服务。
 */
interface ImeProvider {
    val type: ImeType

    /** 输入法名称，用于日志和调试。 */
    val name: String

    /** 输入法专用状态源；未提供或字段不可用时回退到系统级状态。 */
    val stateSource: ImeStateSource?
        get() = null

    /** 输入法专用中英文切换；未提供时使用系统级切换。 */
    val asciiModeSwitcher: ImeAsciiModeSwitcher?
        get() = null

    /** 输入法专用 CapsLock 切换；未提供时使用系统级切换。 */
    val capsLockSwitcher: ImeCapsLockSwitcher?
        get() = null

    fun start()

    fun dispose()
}

/** 输入法级别可以只提供其中一部分状态。 */
interface ImeStateSource {
    fun readAsciiMode(): Boolean? = null

    fun readCapsLock(): Boolean? = null

    fun readComposing(): Boolean? = null
}

fun interface ImeAsciiModeSwitcher {
    /** 返回 false 表示输入法专用切换执行失败，不再降级到系统级切换。 */
    suspend fun switchAsciiMode(ascii: Boolean, shouldContinue: () -> Boolean): Boolean
}

fun interface ImeCapsLockSwitcher {
    /** 返回 false 表示输入法专用切换执行失败，不再降级到系统级切换。 */
    suspend fun switchCapsLock(enabled: Boolean, shouldContinue: () -> Boolean): Boolean
}

data class ImeState(
    val isAsciiMode: Boolean,
    val isCapsLock: Boolean,
    val isComposing: Boolean = false
)
