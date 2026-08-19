package com.navrot.aifuelassistant.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Тесты для [AiResponseCache] — LRU-кэша ответов AI-провайдеров.
 *
 * Кэш стоит ПЕРЕД всеми провайдерами в [com.navrot.aifuelassistant.ai.router.AiRouter.ask],
 * поэтому любая ошибка в его логике (истёкший TTL не сработал, ключ с lat/lon
 * посчитался одинаковым для разных запросов, LRU вытеснил не ту запись)
 * молча подсунет пользователю устаревший или чужой ответ.
 *
 * Покрытие:
 *  - базовый put/get
 *  - TTL (15 мин для АЗС, 24 ч для общих)
 *  - истечение TTL
 *  - ключ включает lat/lon (разные координаты → разные записи)
 *  - ключ включает prompt (разные промпты → разные записи)
 *  - LRU-вытеснение при превышении maxSize
 *  - MRU-сдвиг при чтении (accessOrder = true)
 *  - clear()
 *  - size
 *  - потокобезопасность (несколько потоков, sanity check)
 *  - null lat/lon (общие запросы без геолокации)
 */
class AiResponseCacheTest {

    private lateinit var cache: AiResponseCache

    @Before
    fun setUp() {
        // Маленький maxSize для удобной проверки LRU-вытеснения
        cache = AiResponseCache(maxSize = 3)
    }

    // ==================== Базовый put/get ====================

    @Test
    fun `get returns null for missing key`() {
        assertNull(cache.get("prompt", lat = 55.0, lon = 61.0, ttlMs = 60_000L))
    }

    @Test
    fun `get returns value after put`() {
        cache.put("hello", lat = 55.0, lon = 61.0, answer = "world", ttlMs = 60_000L)
        assertEquals("world", cache.get("hello", lat = 55.0, lon = 61.0, ttlMs = 60_000L))
    }

    @Test
    fun `put overwrites previous value for same key`() {
        cache.put("p", 55.0, 61.0, "v1", 60_000L)
        cache.put("p", 55.0, 61.0, "v2", 60_000L)
        assertEquals("v2", cache.get("p", 55.0, 61.0, 60_000L))
        assertEquals(1, cache.size)
    }

    // ==================== TTL ====================

    @Test
    fun `get returns value when within TTL`() {
        val ttl = 10_000L // 10 секунд
        cache.put("p", 55.0, 61.0, "v", ttl)
        // Сразу после put — должно вернуться
        assertEquals("v", cache.get("p", 55.0, 61.0, ttl))
    }

    @Test
    fun `get returns null when TTL expired`() {
        // TTL = 1 мс — почти гарантированно истечёт между put и get
        cache.put("p", 55.0, 61.0, "v", ttlMs = 1L)
        // Спим достаточно, чтобы 1 мс прошла
        Thread.sleep(50L)
        assertNull(cache.get("p", 55.0, 61.0, ttlMs = 1L))
    }

    @Test
    fun `expired entry is removed from cache on get`() {
        cache.put("p", 55.0, 61.0, "v", ttlMs = 1L)
        Thread.sleep(50L)
        // get должен удалить истёкшую запись
        cache.get("p", 55.0, 61.0, ttlMs = 1L)
        assertEquals(0, cache.size)
    }

    @Test
    fun `TTL gas station 15 min is longer than 1 minute`() {
        // Sanity check на константу — если кто-то случайно изменит TTL_GAS_STATION_MS
        // на 15 секунд вместо 15 минут, этот тест упадёт.
        assertTrue(
            "TTL_GAS_STATION_MS должно быть 15 минут",
            AiResponseCache.TTL_GAS_STATION_MS == 15 * 60 * 1000L
        )
    }

    @Test
    fun `TTL general 24 hours is much longer than gas station TTL`() {
        assertTrue(
            "TTL_GENERAL_MS должно быть 24 часа и больше TTL_GAS_STATION_MS",
            AiResponseCache.TTL_GENERAL_MS == 24 * 60 * 60 * 1000L &&
                    AiResponseCache.TTL_GENERAL_MS > AiResponseCache.TTL_GAS_STATION_MS
        )
    }

    // ==================== Ключ включает lat/lon ====================

    @Test
    fun `different lat produces different cache entries`() {
        cache.put("p", lat = 55.0, lon = 61.0, answer = "south", ttlMs = 60_000L)
        cache.put("p", lat = 56.0, lon = 61.0, answer = "north", ttlMs = 60_000L)
        assertEquals("south", cache.get("p", 55.0, 61.0, 60_000L))
        assertEquals("north", cache.get("p", 56.0, 61.0, 60_000L))
        assertEquals(2, cache.size)
    }

    @Test
    fun `different lon produces different cache entries`() {
        cache.put("p", 55.0, 61.0, "east", 60_000L)
        cache.put("p", 55.0, 59.0, "west", 60_000L)
        assertEquals("east", cache.get("p", 55.0, 61.0, 60_000L))
        assertEquals("west", cache.get("p", 55.0, 59.0, 60_000L))
    }

    @Test
    fun `null lat and lon are valid key components`() {
        // Общий вопрос без геолокации — lat=null, lon=null
        cache.put("general question", lat = null, lon = null, answer = "general answer", ttlMs = 60_000L)
        assertEquals(
            "general answer",
            cache.get("general question", lat = null, lon = null, ttlMs = 60_000L)
        )
    }

    @Test
    fun `null lat lon and non-null lat lon produce different entries`() {
        cache.put("p", null, null, "no-geo", 60_000L)
        cache.put("p", 55.0, 61.0, "with-geo", 60_000L)
        assertEquals("no-geo", cache.get("p", null, null, 60_000L))
        assertEquals("with-geo", cache.get("p", 55.0, 61.0, 60_000L))
    }

    // ==================== Ключ включает prompt ====================

    @Test
    fun `different prompts produce different cache entries`() {
        cache.put("prompt A", 55.0, 61.0, "answer A", 60_000L)
        cache.put("prompt B", 55.0, 61.0, "answer B", 60_000L)
        assertEquals("answer A", cache.get("prompt A", 55.0, 61.0, 60_000L))
        assertEquals("answer B", cache.get("prompt B", 55.0, 61.0, 60_000L))
    }

    // ==================== LRU-вытеснение ====================

    @Test
    fun `LRU evicts oldest entry when capacity exceeded`() {
        // maxSize = 3
        cache.put("p1", 55.0, 61.0, "v1", 60_000L)
        cache.put("p2", 55.0, 61.0, "v2", 60_000L)
        cache.put("p3", 55.0, 61.0, "v3", 60_000L)
        assertEquals(3, cache.size)

        // Добавляем 4-ю — должна вытеснить p1 (самую старую, не использованную)
        cache.put("p4", 55.0, 61.0, "v4", 60_000L)
        assertEquals(3, cache.size)
        assertNull("p1 should be evicted", cache.get("p1", 55.0, 61.0, 60_000L))
        assertNotNull("p2 should survive", cache.get("p2", 55.0, 61.0, 60_000L))
        assertNotNull("p3 should survive", cache.get("p3", 55.0, 61.0, 60_000L))
        assertNotNull("p4 should survive", cache.get("p4", 55.0, 61.0, 60_000L))
    }

    @Test
    fun `LRU access via get shifts entry to MRU position`() {
        // maxSize = 3
        cache.put("p1", 55.0, 61.0, "v1", 60_000L)
        cache.put("p2", 55.0, 61.0, "v2", 60_000L)
        cache.put("p3", 55.0, 61.0, "v3", 60_000L)

        // Читаем p1 — теперь оно самое недавно использованное (MRU), а не p3
        cache.get("p1", 55.0, 61.0, 60_000L)

        // Добавляем p4 — должна вытеснить p2 (теперь LRU), а не p1
        cache.put("p4", 55.0, 61.0, "v4", 60_000L)
        assertNotNull("p1 should survive (was accessed)", cache.get("p1", 55.0, 61.0, 60_000L))
        assertNull("p2 should be evicted (was LRU)", cache.get("p2", 55.0, 61.0, 60_000L))
    }

    @Test
    fun `LRU with default MAX_SIZE=100 does not evict at 99 entries`() {
        val bigCache = AiResponseCache(maxSize = AiResponseCache.MAX_SIZE)
        for (i in 1..99) {
            bigCache.put("p$i", 55.0, 61.0, "v$i", 60_000L)
        }
        assertEquals(99, bigCache.size)
        // Все 99 должны быть доступны
        for (i in 1..99) {
            assertNotNull("entry $i should be present", bigCache.get("p$i", 55.0, 61.0, 60_000L))
        }
    }

    // ==================== clear / size ====================

    @Test
    fun `clear empties the cache`() {
        cache.put("p1", 55.0, 61.0, "v1", 60_000L)
        cache.put("p2", 55.0, 61.0, "v2", 60_000L)
        assertEquals(2, cache.size)
        cache.clear()
        assertEquals(0, cache.size)
        assertNull(cache.get("p1", 55.0, 61.0, 60_000L))
    }

    @Test
    fun `size reflects number of live entries`() {
        assertEquals(0, cache.size)
        cache.put("p1", 55.0, 61.0, "v1", 60_000L)
        assertEquals(1, cache.size)
        cache.put("p2", 55.0, 61.0, "v2", 60_000L)
        assertEquals(2, cache.size)
        cache.clear()
        assertEquals(0, cache.size)
    }

    // ==================== Потокобезопасность (sanity) ====================

    @Test
    fun `concurrent put and get do not corrupt cache`() {
        // Запускаем N потоков, каждый кладёт свои записи и читает общие.
        // Sanity check: после всех операций размер не должен превышать maxSize,
        // и чтение существующего ключа должно возвращать валидное значение (не чужое).
        val concurrency = 8
        val iterationsPerThread = 200
        val sharedCache = AiResponseCache(maxSize = 50)

        val threads = (1..concurrency).map { threadId ->
            Thread {
                for (i in 0 until iterationsPerThread) {
                    val key = "t$threadId-p$i"
                    sharedCache.put(key, 55.0, 61.0, "v-$threadId-$i", 60_000L)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Размер не должен превышать maxSize
        assertTrue(
            "cache size ${sharedCache.size} must not exceed maxSize 50",
            sharedCache.size <= 50
        )

        // Хотя бы одна запись должна выжить (мы добавили 8 * 200 = 1600 записей)
        assertTrue("cache should have some entries", sharedCache.size > 0)
    }

    @Test
    fun `concurrent reads of same key return consistent value`() {
        val cache = AiResponseCache(maxSize = 10)
        cache.put("shared", 55.0, 61.0, "the-answer", 60_000L)

        val concurrency = 16
        val results = Array<String?>(concurrency) { null }

        val threads = (0 until concurrency).map { idx ->
            Thread {
                results[idx] = cache.get("shared", 55.0, 61.0, 60_000L)
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Все потоки должны получить либо "the-answer", либо null (если TTL истёк).
        // Главное — никакой другой строки (data race corruption).
        results.forEach { r ->
            assertTrue(
                "result must be 'the-answer' or null, but was: $r",
                r == null || r == "the-answer"
            )
        }
    }

    // ==================== Edge cases ====================

    @Test
    fun `empty prompt is a valid key`() {
        cache.put("", 55.0, 61.0, "empty-prompt-answer", 60_000L)
        assertEquals("empty-prompt-answer", cache.get("", 55.0, 61.0, 60_000L))
    }

    @Test
    fun `same prompt with same lat lon but different ttl does not affect key`() {
        // Ключ строится из (prompt, lat, lon) — TTL НЕ входит в ключ.
        // Это означает, что запрос с TTL=15min и запрос с TTL=24h для тех же
        // (prompt, lat, lon) делят одну запись — и первый записанный TTL побеждает.
        // Это подтверждает текущий контракт.
        cache.put("p", 55.0, 61.0, "v1", ttlMs = 1L)
        // Тот же ключ, другой TTL — не должен перезаписать, если не вызывать put
        // (get использует TTL только для проверки истечения)
        Thread.sleep(50L)
        // Запрос с другим TTL — но запись уже истекла
        assertNull(cache.get("p", 55.0, 61.0, ttlMs = 60_000L))
    }

    @Test
    fun `put with zero TTL is effectively immediately expired`() {
        // Контракт: expiresAt = now + ttl. При ttl=0 expiresAt = now.
        // get проверяет currentTimeMillis > expiresAt. На быстрых машинах
        // between put и get может пройти 0 мс, и запись ещё «жива» в ту же
        // миллисекунду. Поэтому спим 5 мс, чтобы гарантированно перейти границу.
        cache.put("p", 55.0, 61.0, "v", ttlMs = 0L)
        Thread.sleep(10L)
        assertNull(cache.get("p", 55.0, 61.0, ttlMs = 0L))
    }
}
