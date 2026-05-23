package com.rimevim.core

/**
 * IME 相关异常基类
 */
sealed class ImeException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ProviderNotFound(type: ImeType) : ImeException("IME provider not found for type: $type")
    class InitializationFailed(provider: String, cause: Throwable) :
        ImeException("Failed to initialize IME provider: $provider", cause)
    class SwitchFailed(provider: String, action: String, cause: Throwable) :
        ImeException("Failed to switch IME ($provider): $action", cause)
    class StateFileError(path: String, cause: Throwable) :
        ImeException("Failed to read/write state file: $path", cause)
}
