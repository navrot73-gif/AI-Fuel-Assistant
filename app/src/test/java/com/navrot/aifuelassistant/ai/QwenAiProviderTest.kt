package com.navrot.aifuelassistant.ai

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

/**
 * Тесты для [QwenAiProvider] с использованием [MockWebServer].
 *
 * Qwen использует OpenAI-compatible API (как DeepSeek), поэтому структура тестов
 * идентична [DeepSeekAiProviderTest]. Отличия: model=qwen-plus, другой URL по умолчанию.
 *
 * Запускается под Robolectric (org.json требует Android runtime).
 */
@RunWith(RobolectricTestRunner::class)
class QwenAiProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var provider: QwenAiProvider

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
        provider = QwenAiProvider(
            httpClient = client,
            baseUrl = server.url("/v1/chat/completions").toString(),
            apiKey = "qwen-test-key"
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun successResponse(content: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setBody(
            """
            {
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": "$content"
                  }
                }
              ]
            }
            """.trimIndent()
        )

    // ==================== Успешный запрос ====================

    @Test
    fun `successful response returns content`() = runBlocking {
        server.enqueue(successResponse("Hello from Qwen!"))
        assertEquals("Hello from Qwen!", provider.ask("Hi"))
    }

    @Test
    fun `authorization header sent with API key`() = runBlocking {
        server.enqueue(successResponse("OK"))
        provider.ask("test")
        assertEquals("Bearer qwen-test-key", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `request body contains qwen-plus model`() = runBlocking {
        server.enqueue(successResponse("OK"))
        provider.ask("test")
        val body = server.takeRequest().body.readUtf8()
        assertTrue("Should use qwen-plus model", body.contains("\"model\":\"qwen-plus\""))
    }

    @Test
    fun `request body contains system prompt`() = runBlocking {
        server.enqueue(successResponse("OK"))
        provider.ask("test")
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"role\":\"system\""))
        assertTrue(body.contains("Ты ассистент топливного приложения"))
    }

    // ==================== Обработка ошибок ====================

    @Test
    fun `HTTP 401 throws AuthError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))
        try {
            provider.ask("test")
            assert(false) { "Should throw" }
        } catch (e: AiException.AuthError) {
            assertTrue(e.message!!.contains("401"))
        }
    }

    @Test
    fun `HTTP 429 throws RateLimit`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("Slow down"))
        try {
            provider.ask("test")
            assert(false) { "Should throw" }
        } catch (e: AiException.RateLimit) {
            assertTrue(e.message!!.contains("429"))
        }
    }

    @Test
    fun `HTTP 500 throws ApiError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Server error"))
        try {
            provider.ask("test")
            assert(false) { "Should throw" }
        } catch (e: AiException.ApiError) {
            assertTrue(e.message!!.contains("500"))
        }
    }

    @Test
    fun `empty body throws EmptyResponse`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))
        try {
            provider.ask("test")
            assert(false) { "Should throw" }
        } catch (e: AiException.EmptyResponse) {
            assertTrue(e.message!!.contains("empty"))
        }
    }

    @Test
    fun `malformed JSON throws NetworkError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))
        try {
            provider.ask("test")
            assert(false) { "Should throw" }
        } catch (e: AiException.NetworkError) {
            assertTrue(e.message!!.contains("Qwen error"))
        }
    }

    @Test
    fun `connection error throws NetworkError`() = runBlocking {
        server.shutdown()
        try {
            provider.ask("test")
            assert(false) { "Should throw" }
        } catch (e: AiException.NetworkError) {
            assertTrue(e.message!!.contains("Qwen error"))
        }
    }

    // ==================== Валидация ====================

    @Test(expected = IllegalArgumentException::class)
    fun `blank prompt throws`() = runBlocking {
        provider.ask("")
        Unit
    }

    @Test
    fun `provider name is Qwen`() {
        assertEquals("Qwen", provider.name)
    }
}
