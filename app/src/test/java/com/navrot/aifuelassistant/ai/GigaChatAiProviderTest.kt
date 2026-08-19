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
 * Тесты для [GigaChatAiProvider] с использованием [MockWebServer].
 *
 * GigaChat сложнее других провайдеров: использует двухфазный OAuth flow
 * (получение access_token → запрос к chat completions). Тесты покрывают:
 *
 *  - успешный chat-запрос с готовым токеном
 *  - OAuth: получение токена по authorizationKey (Base64)
 *  - OAuth: получение токена по clientId + clientSecret
 *  - токен кэшируется между вызовами (не запрашивается повторно)
 *  - 401 на chat → AuthError
 *  - 429 на chat → RateLimit
 *  - 500 на chat → ApiError
 *  - пустой ответ → EmptyResponse
 *  - нет credentials → AuthError
 *  - blank prompt → IllegalArgumentException
 *
 * Запускается под Robolectric (org.json + Base64 требуют Android runtime).
 */
@RunWith(RobolectricTestRunner::class)
class GigaChatAiProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** Успешный OAuth-ответ с access_token. */
    private fun oauthResponse(
        accessToken: String = "test-access-token",
        expiresAtSeconds: Long = (System.currentTimeMillis() / 1000) + 3600
    ): MockResponse = MockResponse()
        .setResponseCode(200)
        .setBody(
            """
            {
              "access_token": "$accessToken",
              "expires_at": $expiresAtSeconds
            }
            """.trimIndent()
        )

    /** Успешный chat-ответ (OpenAI-compatible). */
    private fun chatResponse(content: String): MockResponse = MockResponse()
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

    private fun providerWithAuthKey(): GigaChatAiProvider = GigaChatAiProvider(
        authorizationKey = "dGVzdC1jbGllbnQ6dGVzdC1zZWNyZXQ=", // base64 of "test-client:test-secret"
        httpClient = client,
        chatUrl = server.url("/chat/completions").toString(),
        oauthUrl = server.url("/oauth").toString()
    )

    private fun providerWithClientCredentials(): GigaChatAiProvider = GigaChatAiProvider(
        clientId = "test-client-id",
        clientSecret = "test-client-secret",
        httpClient = client,
        chatUrl = server.url("/chat/completions").toString(),
        oauthUrl = server.url("/oauth").toString()
    )

    // ==================== Успешный flow с OAuth ====================

    @Test
    fun `successful chat request with authorizationKey obtains token then chats`() = runBlocking {
        // 1) OAuth-запрос → токен
        server.enqueue(oauthResponse(accessToken = "my-token-123"))
        // 2) Chat-запрос → ответ
        server.enqueue(chatResponse("Hello from GigaChat!"))

        val provider = providerWithAuthKey()
        val result = provider.ask("Привет")

        assertEquals("Hello from GigaChat!", result)
        assertEquals(2, server.requestCount)

        // Проверяем, что chat-запрос использовал полученный токен
        val chatRequest = server.takeRequest() // OAuth
        val chatRequest2 = server.takeRequest() // chat
        assertEquals("Bearer my-token-123", chatRequest2.getHeader("Authorization"))
    }

    @Test
    fun `successful chat request with clientId and clientSecret`() = runBlocking {
        server.enqueue(oauthResponse(accessToken = "client-cred-token"))
        server.enqueue(chatResponse("OK"))

        val provider = providerWithClientCredentials()
        val result = provider.ask("test")

        assertEquals("OK", result)
    }

    @Test
    fun `OAuth request sends Basic auth header with authorizationKey`() = runBlocking {
        server.enqueue(oauthResponse())
        server.enqueue(chatResponse("OK"))

        val provider = providerWithAuthKey()
        provider.ask("test")

        val oauthRequest = server.takeRequest()
        val authHeader = oauthRequest.getHeader("Authorization")
        assertTrue(
            "OAuth should use Basic auth with authorizationKey: $authHeader",
            authHeader!!.startsWith("Basic ")
        )
    }

    @Test
    fun `OAuth request sends scope GIGACHAT_API_PERS`() = runBlocking {
        server.enqueue(oauthResponse())
        server.enqueue(chatResponse("OK"))

        val provider = providerWithAuthKey()
        provider.ask("test")

        val oauthRequest = server.takeRequest()
        val body = oauthRequest.body.readUtf8()
        assertTrue("OAuth body should contain scope", body.contains("GIGACHAT_API_PERS"))
    }

    @Test
    fun `OAuth request includes RqUID header`() = runBlocking {
        server.enqueue(oauthResponse())
        server.enqueue(chatResponse("OK"))

        val provider = providerWithAuthKey()
        provider.ask("test")

        val oauthRequest = server.takeRequest()
        val rqUid = oauthRequest.getHeader("RqUID")
        assertTrue("RqUID should be present and non-blank", !rqUid.isNullOrBlank())
    }

    // ==================== Кэширование токена ====================

    @Test
    fun `token is cached between calls - no second OAuth`() = runBlocking {
        // 1) OAuth → токен с большим expires_at
        server.enqueue(oauthResponse(accessToken = "cached-token", expiresAtSeconds = Long.MAX_VALUE / 1000))
        // 2) Chat #1
        server.enqueue(chatResponse("First"))
        // 3) Chat #2 — НЕ должен запрашивать OAuth снова
        server.enqueue(chatResponse("Second"))

        val provider = providerWithAuthKey()
        val r1 = provider.ask("first")
        val r2 = provider.ask("second")

        assertEquals("First", r1)
        assertEquals("Second", r2)
        // Только 3 запроса: 1 OAuth + 2 chat. Если бы токен не кэшировался, было бы 4.
        assertEquals(3, server.requestCount)
    }

    // ==================== Chat-запрос: содержимое ====================

    @Test
    fun `chat request body contains model and messages`() = runBlocking {
        server.enqueue(oauthResponse())
        server.enqueue(chatResponse("OK"))

        val provider = providerWithAuthKey()
        provider.ask("What is the fuel price?")

        // Пропускаем OAuth-запрос, берём chat-запрос
        server.takeRequest() // OAuth
        val chatRequest = server.takeRequest()
        val body = chatRequest.body.readUtf8()
        assertTrue("Body should contain GigaChat model", body.contains("\"model\":\"GigaChat\""))
        assertTrue("Body should contain user prompt", body.contains("What is the fuel price?"))
        assertTrue("Body should contain system role", body.contains("\"role\":\"system\""))
    }

    // ==================== Обработка ошибок chat-запроса ====================

    @Test
    fun `chat HTTP 401 throws AuthError`() = runBlocking {
        server.enqueue(oauthResponse())
        server.enqueue(MockResponse().setResponseCode(401).setBody("Token expired"))

        val provider = providerWithAuthKey()
        try {
            provider.ask("test")
            assert(false) { "Should throw" }
        } catch (e: AiException.AuthError) {
            assertTrue(e.message!!.contains("401"))
        }
    }

    @Test
    fun `chat HTTP 429 throws RateLimit`() = runBlocking {
        server.enqueue(oauthResponse())
        server.enqueue(MockResponse().setResponseCode(429).setBody("Rate limited"))

        val provider = providerWithAuthKey()
        try {
            provider.ask("test")
            assert(false) { "Should throw" }
        } catch (e: AiException.RateLimit) {
            assertTrue(e.message!!.contains("429"))
        }
    }

    @Test
    fun `chat HTTP 500 throws ApiError`() = runBlocking {
        server.enqueue(oauthResponse())
        server.enqueue(MockResponse().setResponseCode(500).setBody("Server error"))

        val provider = providerWithAuthKey()
        try {
            provider.ask("test")
            assert(false) { "Should throw" }
        } catch (e: AiException.ApiError) {
            assertTrue(e.message!!.contains("500"))
        }
    }

    @Test
    fun `chat empty body throws EmptyResponse`() = runBlocking {
        server.enqueue(oauthResponse())
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        val provider = providerWithAuthKey()
        try {
            provider.ask("test")
            assert(false) { "Should throw" }
        } catch (e: AiException.EmptyResponse) {
            assertTrue(e.message!!.contains("empty"))
        }
    }

    @Test
    fun `chat malformed JSON throws NetworkError`() = runBlocking {
        server.enqueue(oauthResponse())
        server.enqueue(MockResponse().setResponseCode(200).setBody("not json"))

        val provider = providerWithAuthKey()
        try {
            provider.ask("test")
            assert(false) { "Should throw" }
        } catch (e: AiException.NetworkError) {
            // JSONException теперь оборачивается в NetworkError (catch-all добавлен)
            assertTrue(e.message!!.contains("GigaChat error"))
        }
    }

    // ==================== Обработка ошибок OAuth ====================

    @Test
    fun `OAuth HTTP 400 throws ApiError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("Invalid credentials"))

        val provider = providerWithAuthKey()
        try {
            provider.ask("test")
            assert(false) { "Should throw" }
        } catch (e: AiException.ApiError) {
            assertTrue(e.message!!.contains("token request"))
        }
    }

    @Test
    fun `OAuth HTTP 401 throws AuthError`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))

        val provider = providerWithAuthKey()
        try {
            provider.ask("test")
            assert(false) { "Should throw" }
        } catch (e: AiException.AuthError) {
            assertTrue(e.message!!.contains("401"))
        }
    }

    @Test
    fun `OAuth empty body throws EmptyResponse`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        val provider = providerWithAuthKey()
        try {
            provider.ask("test")
            assert(false) { "Should throw" }
        } catch (e: AiException.EmptyResponse) {
            assertTrue(e.message!!.contains("token response body is empty"))
        }
    }

    // ==================== Нет credentials ====================

    @Test
    fun `no credentials throws AuthError`() = runBlocking {
        val provider = GigaChatAiProvider(
            clientId = null,
            clientSecret = null,
            authorizationKey = null,
            httpClient = client,
            chatUrl = server.url("/chat").toString(),
            oauthUrl = server.url("/oauth").toString()
        )
        try {
            provider.ask("test")
            assert(false) { "Should throw" }
        } catch (e: AiException.AuthError) {
            assertTrue(e.message!!.contains("credentials are not configured"))
        }
    }

    @Test
    fun `only clientId without clientSecret throws AuthError`() = runBlocking {
        val provider = GigaChatAiProvider(
            clientId = "only-client-id",
            clientSecret = null,
            authorizationKey = null,
            httpClient = client
        )
        try {
            provider.ask("test")
            assert(false) { "Should throw" }
        } catch (e: AiException.AuthError) {
            assertTrue(e.message!!.contains("credentials are not configured"))
        }
    }

    // ==================== Валидация ====================

    @Test(expected = IllegalArgumentException::class)
    fun `blank prompt throws`() = runBlocking {
        val provider = providerWithAuthKey()
        provider.ask("   ")
        Unit
    }

    // ==================== Имя провайдера ====================

    @Test
    fun `provider name is GigaChat`() {
        val provider = providerWithAuthKey()
        assertEquals("GigaChat", provider.name)
    }
}
