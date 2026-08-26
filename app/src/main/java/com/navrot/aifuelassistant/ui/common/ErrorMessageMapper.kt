package com.navrot.aifuelassistant.ui.common

import com.navrot.aifuelassistant.geo.GeoException
import java.io.IOException
import java.net.SocketTimeoutException

enum class ErrorContext { ROUTE, PRICES, AI, GENERAL }

/**
 * Исключение для эмуляции или передачи HTTP-ошибок (например, от Retrofit или OkHttp).
 */
class HttpException(val code: Int, message: String? = null) : Exception(message ?: "HTTP $code")

object ErrorMessageMapper {
    fun mapToUserMessage(throwable: Throwable, context: ErrorContext): String {
        return when {
            isNetworkError(throwable) ->
                when (context) {
                    ErrorContext.ROUTE -> "Не удалось построить маршрут. Показана прямая линия."
                    ErrorContext.PRICES -> "Не удалось обновить цены. Показаны данные из кэша."
                    ErrorContext.AI -> "AI-помощник временно недоступен. Попробуйте позже."
                    ErrorContext.GENERAL -> "Проблема с соединением. Проверьте интернет."
                }
            throwable is HttpException && throwable.code in 500..599 ->
                "Сервер временно перегружен. Попробуйте через минуту."
            else -> "Что-то пошло не так. Попробуйте ещё раз."
        }
    }

    private fun isNetworkError(throwable: Throwable): Boolean {
        return throwable is IOException ||
                throwable is SocketTimeoutException ||
                throwable is GeoException.NetworkError ||
                (throwable.cause != null && isNetworkError(throwable.cause!!))
    }
}
