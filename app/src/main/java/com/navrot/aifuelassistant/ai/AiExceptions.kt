package com.navrot.aifuelassistant.ai

/**
 * Типы исключений для AI-провайдеров.
 */
sealed class AiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ApiError(message: String, cause: Throwable? = null) : AiException(message, cause)
    class AuthError(message: String, cause: Throwable? = null) : AiException(message, cause)
    class RateLimit(message: String, cause: Throwable? = null) : AiException(message, cause)
    class EmptyResponse(message: String, cause: Throwable? = null) : AiException(message, cause)
    class NetworkError(message: String, cause: Throwable? = null) : AiException(message, cause)
}
