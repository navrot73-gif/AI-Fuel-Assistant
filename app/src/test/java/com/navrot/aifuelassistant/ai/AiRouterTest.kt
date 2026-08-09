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

    private val noOpLogger: (String, String) -> Unit = { _, _ -> }

    private fun router(vararg providers: AiProvider, timeoutMs: Long = 5000) =
        AiRouter(providers.toList(), timeoutMs, noOpLogger)

    @Test
    fun `first successful provider wins`() = runTest {
        val fast = FakeProvider("Fast", Result.success("Ответ от Fast"))
        val slow = FakeProvider("Slow", Result.success("Ответ от Slow"), delayMs = 2000)
        assertEquals("Ответ от Fast", router(fast, slow).ask("test"))
    }

    @Test
    fun `returns from second provider if first fails`() = runTest {
        val failing = FakeProvider("Failing", Result.failure(RuntimeException("down")))
        val working = FakeProvider("Working", Result.success("Ответ от Working"))
        assertEquals("Ответ от Working", router(failing, working).ask("test"))
    }

    @Test(expected = IllegalStateException::class)
    fun `throws when all providers fail`() = runTest {
        router(
            FakeProvider("P1", Result.failure(RuntimeException("err1"))),
            FakeProvider("P2", Result.failure(RuntimeException("err2")))
        ).ask("test")
    }

    @Test(expected = IllegalStateException::class)
    fun `throws when all providers timeout`() = runTest {
        router(
            FakeProvider("Slow1", Result.success("never"), delayMs = 10_000),
            FakeProvider("Slow2", Result.success("never"), delayMs = 10_000),
            timeoutMs = 100
        ).ask("test")
    }

    @Test
    fun `single provider returns its answer`() = runTest {
        assertEquals(
            "Единственный ответ",
            router(FakeProvider("Only", Result.success("Единственный ответ"))).ask("test")
        )
    }

    @Test(expected = IllegalStateException::class)
    fun `empty providers list throws`() = runTest {
        router().ask("test")
    }

    @Test
    fun `empty answer is skipped in favor of working provider`() = runTest {
        val empty = FakeProvider("Empty", Result.success("   "))
        val working = FakeProvider("Working", Result.success("Нормальный ответ"))
        assertEquals("Нормальный ответ", router(empty, working).ask("test"))
    }

    @Test
    fun `UnavailableAiProvider always throws`() = runTest {
        val provider = UnavailableAiProvider("Нет провайдеров")
        try {
            provider.ask("test")
            fail("Should have thrown")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("not configured") == true)
        }
    }
}
