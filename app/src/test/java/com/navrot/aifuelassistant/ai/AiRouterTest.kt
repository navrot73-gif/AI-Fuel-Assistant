package com.navrot.aifuelassistant.ai

import com.navrot.aifuelassistant.ai.router.AiRouter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiRouterTest {

    private class FakeProvider(
        override val name: String,
        private val result: Result<String>,
        private val delayMs: Long = 0L
    ) : AiProvider {
        override suspend fun ask(prompt: String): String {
            if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
            return result.getOrThrow()
        }
    }

    /** No-op logger — заменяет android.util.Log в unit-тестах */
    private val noOpLogger: (String, String) -> Unit = { _, _ -> }

    private fun router(vararg providers: AiProvider, timeoutMs: Long = 5000) =
        AiRouter(providers.toList(), timeoutMs, noOpLogger)

    @Test
    fun `first successful provider wins`() = runTest {
        val fast = FakeProvider("Fast", Result.success("Ответ от Fast"))
        val slow = FakeProvider("Slow", Result.success("Ответ от Slow"), delayMs = 2000)

        val answer = router(fast, slow).ask("test")
        assertEquals("Ответ от Fast", answer)
    }

    @Test
    fun `returns from second provider if first fails`() = runTest {
        val failing = FakeProvider("Failing", Result.failure(RuntimeException("down")))
        val working = FakeProvider("Working", Result.success("Ответ от Working"))

        val answer = router(failing, working).ask("test")
        assertEquals("Ответ от Working", answer)
    }

    @Test(expected = IllegalStateException::class)
    fun `throws when all providers fail`() = runTest {
        val p1 = FakeProvider("P1", Result.failure(RuntimeException("err1")))
        val p2 = FakeProvider("P2", Result.failure(RuntimeException("err2")))
        router(p1, p2).ask("test")
    }

    @Test(expected = IllegalStateException::class)
    fun `throws when all providers timeout`() = runTest {
        val slow1 = FakeProvider("Slow1", Result.success("never"), delayMs = 10_000)
        val slow2 = FakeProvider("Slow2", Result.success("never"), delayMs = 10_000)
        router(slow1, slow2, timeoutMs = 100).ask("test")
    }

    @Test
    fun `single provider returns its answer`() = runTest {
        val provider = FakeProvider("Only", Result.success("Единственный ответ"))
        assertEquals("Единственный ответ", router(provider).ask("test"))
    }

    @Test(expected = IllegalStateException::class)
    fun `empty providers list throws`() = runTest {
        router().ask("test")
    }

    @Test
    fun `UnavailableAiProvider always throws`() = runTest {
        val provider = com.navrot.aifuelassistant.ai.UnavailableAiProvider("Нет провайдеров")
        try {
            provider.ask("test")
            fail("Should have thrown")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("not configured") == true)
        }
    }

    @Test
    fun `race - first successful provider wins over failing one`() = runTest {
        val fast = FakeProvider("Fast", Result.success("Быстрый ответ"), delayMs = 2000)
        val failing = FakeProvider("Failing", Result.failure(RuntimeException("ошибка")), delayMs = 0)

        val answer = router(fast, failing).ask("test")
        assertEquals("Быстрый ответ", answer)
    }

    @Test
    fun `all providers fail - returns error`() = runTest {
        val p1 = FakeProvider("P1", Result.failure(RuntimeException("error1")))
        val p2 = FakeProvider("P2", Result.failure(RuntimeException("error2")))
        
        try {
            router(p1, p2).ask("test")
            fail("Should have thrown")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Все AI провайдеры недоступны") == true)
        }
    }

    @Test
    fun `timeout stops the race after 8 seconds`() = runTest {
        val slow1 = FakeProvider("Slow1", Result.success("never"), delayMs = 9000)
        val slow2 = FakeProvider("Slow2", Result.success("never"), delayMs = 9000)
        
        try {
            router(slow1, slow2, timeoutMs = 8000).ask("test")
            fail("Should have thrown")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Все AI провайдеры недоступны") == true)
        }
    }
}
