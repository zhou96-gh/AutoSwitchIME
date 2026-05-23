package com.auto_switch_ime.core

import com.auto_switch_ime.core.util.Logger

/**
 * IME Provider 工厂
 * 支持注册和创建不同类型的输入法提供者
 * 
 * 注意：内置 Provider（如 RimeImeProvider）由 intellij 模块在初始化时注册
 */
object ImeProviderFactory {
    private val providers = mutableMapOf<ImeType, (ImeConfig, Logger) -> ImeProvider>()
    
    /**
     * 注册 Provider（包括内置和自定义）
     */
    fun register(type: ImeType, factory: (ImeConfig, Logger) -> ImeProvider) {
        providers[type] = factory
    }
    
    /**
     * 创建 Provider 实例
     */
    fun createProvider(config: ImeConfig, logger: Logger): ImeProvider {
        val factory = providers[config.type]
            ?: throw ImeException.ProviderNotFound(config.type)
        return factory(config, logger)
    }
}
