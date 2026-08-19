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
 * Тесты для [DeepSeekAiProvider] с использованием [MockWebServer].
 *
 * Запускается под Robolectric, т.к. провайдер использует [org.json.JSONObject]
 * для парсинга ответа — в pure JVM unit-тестах org.json это stub,
 * который кидает NPE.
 *
 * Покрывает:
 *  - успешный запрос: парсинг content из OpenAI-compatible ответа
 *  - отправка Authorization header с API-ключом
 *  - 401 → [AiException.AuthError]
 *  - 429 → [AiException.RateLimit]
 *  - 500 → [AiException.ApiError]
 *  - пустой body → [AiException.EmptyResponse]
 *  - невалидный JSON → [AiException.NetworkError] (оборачивание)
 *  - system prompt передаётся в messages
 *  - prompt blank → IllegalArgumentException
 */
@RunWith(RobolectricTestRunner::class)
class DeepSeekAiProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var provider: DeepSeekAiProvider

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
        provider = DeepSeekAiProvider(
            httpClient = client,
            apiUrl = server.url("/chat/completions").toString(),
            apiKey = "test-api-key-12345"
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** OpenAI-compatible успешный ответ. */
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
    fun `successful response returns content from choices`() = runBlocking {
        server.enqueue(successResponse("Hello from DeepSeek!"))

        val result = provider.ask("Hi")
        assertEquals("Hello from DeepSeek!", result)
    }

    @Test
    fun `authorization header is sent with API key`() = runBlocking {
        server.enqueue(successResponse("OK"))

        provider.ask("test")
        val recordedRequest = server.takeRequest()
        assertEquals("Bearer test-api-key-12345", recordedRequest.getHeader("Authorization"))
    }

    @Test
    fun `content-type header is application json`() = runBlocking {
        server.enqueue(successResponse("OK"))

        provider.ask("test")
        val recordedRequest = server.takeRequest()
        // OkHttp добавляет charset=utf-8 к Content-Type автоматически
        val contentType = recordedRequest.getHeader("Content-Type")
        assertTrue(
            "Content-Type should start with application/json: $contentType",
            contentType!!.startsWith("application/json")
        )
    }

    @Test
    fun `request body contains model and messages`() = runBlocking {
        server.enqueue(successResponse("OK"))

        provider.ask("What is the best gas station?")

        val recordedRequest = server.takeRequest()
        val body = recordedRequest.body.readUtf8()
        assertTrue("Body should contain model", body.contains("\"model\":\"deepseek-chat\""))
        assertTrue("Body should contain user prompt", body.contains("What is the best gas station?"))
        assertTrue("Body should contain system role", body.contains("\"role\":\"system\""))
        assertTrue(
            "Body should contain SYSTEM_PROMPT excerpt",
            body.contains("Ты ассистент топливного приложения")
        )
    }

    // ==================== Обработка ошибок HTTP ====================

    @Test
    fun `HTTP 401 throws AuthError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Invalid API key"))

        try {
            provider.ask("test")
            assert(false) { "Should have thrown" }
        } catch (e: AiException.AuthError) {
            assertTrue("Message should mention 401", e.message!!.contains("401"))
        }
    }

    @Test
    fun `HTTP 403 throws AuthError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))

        try {
            provider.ask("test")
            assert(false) { "Should have thrown" }
        } catch (e: AiException.AuthError) {
            assertTrue("Message should mention 403", e.message!!.contains("403"))
        }
    }

    @Test
    fun `HTTP 429 throws RateLimit`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("Rate limited"))

        try {
            provider.ask("test")
            assert(false) { "Should have thrown" }
        } catch (e: AiException.RateLimit) {
            assertTrue("Message should mention 429", e.message!!.contains("429"))
        }
    }

    @Test
    fun `HTTP 500 throws ApiError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal server error"))

        try {
            provider.ask("test")
            assert(false) { "Should have thrown" }
        } catch (e: AiException.ApiError) {
            assertTrue("Message should mention 500", e.message!!.contains("500"))
        }
    }

    @Test
    fun `HTTP 502 throws ApiError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(502).setBody("Bad gateway"))

        try {
            provider.ask("test")
            assert(false) { "Should have thrown" }
        } catch (e: AiException.ApiError) {
            assertTrue(e.message!!.contains("502"))
        }
    }

    @Test
    fun `HTTP 400 throws ApiError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("Bad request"))

        try {
            provider.ask("test")
            assert(false) { "Should have thrown" }
        } catch (e: AiException.ApiError) {
            assertTrue(e.message!!.contains("400"))
        }
    }

    // ==================== Пустой / невалидный ответ ====================

    @Test
    fun `empty response body throws EmptyResponse`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        try {
            provider.ask("test")
            assert(false) { "Should have thrown" }
        } catch (e: AiException.EmptyResponse) {
            assertTrue(e.message!!.contains("empty"))
        }
    }

    @Test
    fun `blank response body throws EmptyResponse`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("   "))

        try {
            provider.ask("test")
            assert(false) { "Should have thrown" }
        } catch (e: AiException.EmptyResponse) {
            assertTrue(e.message!!.contains("empty"))
        }
    }

    @Test
    fun `malformed JSON throws NetworkError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not a json"))

        try {
            provider.ask("test")
            assert(false) { "Should have thrown" }
        } catch (e: AiException.NetworkError) {
            // JSONException оборачивается в NetworkError
            assertTrue(e.message!!.contains("DeepSeek error"))
        }
    }

    @Test
    fun `response missing choices array throws NetworkError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"foo":"bar"}"""))

        try {
            provider.ask("test")
            assert(false) { "Should have thrown" }
        } catch (e: AiException.NetworkError) {
            assertTrue(e.message!!.contains("DeepSeek error"))
        }
    }

    // ==================== Сетевые ошибки ====================

    @Test
    fun `connection refused throws NetworkError`() = runBlocking {
        // Shutdown сервер → connection refused
        server.shutdown()

        try {
            provider.ask("test")
            assert(false) { "Should have thrown" }
        } catch (e: AiException.NetworkError) {
            assertTrue(e.message!!.contains("DeepSeek error"))
        }
    }

    // ==================== Валидация ====================

    @Test(expected = IllegalArgumentException::class)
    fun `blank prompt throws IllegalArgumentException`() = runBlocking {
        provider.ask("   ")
        Unit
    }

    // ==================== Имя провайдера ====================

    @Test
    fun `provider name is DeepSeek`() {
        assertEquals("DeepSeek", provider.name)
    }
}
