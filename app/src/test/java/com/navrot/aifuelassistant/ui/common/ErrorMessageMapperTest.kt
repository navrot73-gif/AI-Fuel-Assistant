package com.navrot.aifuelassistant.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class ErrorMessageMapperTest {

    @Test
    fun mapToUserMessage_ioException_returnsContextSpecificMessage() {
        val ioException = IOException("Network connection lost")

        val routeMsg = ErrorMessageMapper.mapToUserMessage(ioException, ErrorContext.ROUTE)
        assertEquals("Не удалось построить маршрут. Показана прямая линия.", routeMsg)

        val pricesMsg = ErrorMessageMapper.mapToUserMessage(ioException, ErrorContext.PRICES)
        assertEquals("Не удалось обновить цены. Показаны данные из кэша.", pricesMsg)

        val aiMsg = ErrorMessageMapper.mapToUserMessage(ioException, ErrorContext.AI)
        assertEquals("AI-помощник временно недоступен. Попробуйте позже.", aiMsg)

        val generalMsg = ErrorMessageMapper.mapToUserMessage(ioException, ErrorContext.GENERAL)
        assertEquals("Проблема с соединением. Проверьте интернет.", generalMsg)
    }

    @Test
    fun mapToUserMessage_socketTimeoutException_returnsContextSpecificMessage() {
        val timeoutException = SocketTimeoutException("Read timed out")

        val routeMsg = ErrorMessageMapper.mapToUserMessage(timeoutException, ErrorContext.ROUTE)
        assertEquals("Не удалось построить маршрут. Показана прямая линия.", routeMsg)

        val pricesMsg = ErrorMessageMapper.mapToUserMessage(timeoutException, ErrorContext.PRICES)
        assertEquals("Не удалось обновить цены. Показаны данные из кэша.", pricesMsg)

        val aiMsg = ErrorMessageMapper.mapToUserMessage(timeoutException, ErrorContext.AI)
        assertEquals("AI-помощник временно недоступен. Попробуйте позже.", aiMsg)

        val generalMsg = ErrorMessageMapper.mapToUserMessage(timeoutException, ErrorContext.GENERAL)
        assertEquals("Проблема с соединением. Проверьте интернет.", generalMsg)
    }

    @Test
    fun mapToUserMessage_httpException5xx_returnsServerOverloadedMessage() {
        val http500 = HttpException(500, "Internal Server Error")
        val http503 = HttpException(503, "Service Unavailable")

        val msg500 = ErrorMessageMapper.mapToUserMessage(http500, ErrorContext.ROUTE)
        val msg503 = ErrorMessageMapper.mapToUserMessage(http503, ErrorContext.AI)

        assertEquals("Сервер временно перегружен. Попробуйте через минуту.", msg500)
        assertEquals("Сервер временно перегружен. Попробуйте через минуту.", msg503)
    }

    @Test
    fun mapToUserMessage_otherException_returnsGenericMessage() {
        val genericException = IllegalStateException("Unexpected state")
        val http404 = HttpException(404, "Not Found")

        val genericMsg = ErrorMessageMapper.mapToUserMessage(genericException, ErrorContext.GENERAL)
        val msg404 = ErrorMessageMapper.mapToUserMessage(http404, ErrorContext.PRICES)

        assertEquals("Что-то пошло не так. Попробуйте ещё раз.", genericMsg)
        assertEquals("Что-то пошло не так. Попробуйте ещё раз.", msg404)
    }
}
