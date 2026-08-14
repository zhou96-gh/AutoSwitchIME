package com.auto_switch_ime.core.ime.input

import com.auto_switch_ime.core.ImeConfig
import com.auto_switch_ime.core.ImeException
import com.auto_switch_ime.core.ImeProvider
import com.auto_switch_ime.core.ImeType
import com.auto_switch_ime.core.util.Logger

class ImeProviderRegistry {
    private val providers = mutableMapOf<ImeType, (ImeConfig, Logger) -> ImeProvider>()

    fun register(type: ImeType, factory: (ImeConfig, Logger) -> ImeProvider) {
        providers[type] = factory
    }

    fun createProvider(config: ImeConfig, logger: Logger): ImeProvider {
        val factory = providers[config.type]
            ?: throw ImeException.ProviderNotFound(config.type)
        return factory(config, logger)
    }

    fun supportedTypes(): Set<ImeType> = providers.keys.toSet()
}

/**
 * 平台入口注册已实现的输入法级 Provider，Coordinator 只依赖 ImeGateway。
 */
object ImeProviderFactory {
    private val registry = ImeProviderRegistry()

    fun register(type: ImeType, factory: (ImeConfig, Logger) -> ImeProvider) {
        registry.register(type, factory)
    }

    fun createProvider(config: ImeConfig, logger: Logger): ImeProvider {
        return registry.createProvider(config, logger)
    }

    fun supportedTypes(): Set<ImeType> = registry.supportedTypes()
}
