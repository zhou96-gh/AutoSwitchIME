package com.auto_switch_ime.core

/**
 * 平台无关的输入法提供者接口
 * 各输入法实现此接口以支持 IME 切换
 */
interface ImeProvider {
    /** 输入法名称，用于日志和调试 */
    val name: String
    
    /** 切换中英文模式 */
    suspend fun setAsciiMode(ascii: Boolean)
    
    /** 切换大写模式 */
    suspend fun setCapsMode()

    /** 释放插件自身开启的 CapsLock，不影响用户原本开启的 CapsLock */
    suspend fun releaseOwnedCapsLock()
    
    /** 是否正在输入（显示候选词窗口） */
    suspend fun isComposing(): Boolean
    
    /** 获取当前跟踪的 IME 状态 */
    fun getTrackedState(): ImeState
    
    /** 同步内部跟踪状态（不触发实际切换） */
    fun syncTrackedState(ascii: Boolean, caps: Boolean)
    
    /** 释放资源 */
    fun dispose()
}

/**
 * IME 状态数据类
 */
data class ImeState(
    val isAsciiMode: Boolean,
    val isCapsLock: Boolean,
    val isComposing: Boolean = false
)
