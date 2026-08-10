package com.navrot.aifuelassistant.ai

import java.security.MessageDigest
import java.util.LinkedHashMap

/**
 * Потокобезопасный LRU-кэш ответов AI-провайдеров.
 *
 * Ключ: MD5(prompt + lat + lon)
 * Ёмкость: 100 записей (LRU-вытеснение при переполнении)
 * TTL:   15 минут для АЗС-запросов, 24 часа для общих вопросов
 *
 * Потокобезопасность: все операции с картой защищены synchronized(lock),
 * LRU-порядок обеспечивается LinkedHashMap(accessOrder = true).
 */
class AiResponseCache(
    private val maxSize: Int = MAX_SIZE
) {

    private data class CacheEntry(
        val answer: String,
        val expiresAt: Long
    )

    private val lock = Any()

    private val cache = object : LinkedHashMap<String, CacheEntry>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean =
            size > maxSize
    }

    /**
     * Возвращает закэшированный ответ, если он существует и ещё не истёк.
     * Успешное чтение сдвигает запись в конец (MRU) благодаря accessOrder = true.
     */
    fun get(prompt: String, lat: Double?, lon: Double?, ttlMs: Long): String? {
        val key = buildKey(prompt, lat, lon)
        synchronized(lock) {
            val entry = cache[key] ?: return null
            if (System.currentTimeMillis() > entry.expiresAt) {
                cache.remove(key)
                return null
            }
            return entry.answer
        }
    }

    /** Сохраняет ответ в кэше с указанным TTL. */
    fun put(prompt: String, lat: Double?, lon: Double?, answer: String, ttlMs: Long) {
        val key = buildKey(prompt, lat, lon)
        synchronized(lock) {
            cache[key] = CacheEntry(
                answer = answer,
                expiresAt = System.currentTimeMillis() + ttlMs
            )
        }
    }

    /** Очищает весь кэш. */
    fun clear() {
        synchronized(lock) {
            cache.clear()
        }
    }

    /** Текущее количество записей в кэше. */
    val size: Int
        get() = synchronized(lock) { cache.size }

    private fun buildKey(prompt: String, lat: Double?, lon: Double?): String =
        md5("$prompt|$lat|$lon")

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5")
            .digest(input.toByteArray(Charsets.UTF_8))
        return buildString(bytes.size * 2) {
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                append(HEX_CHARS[v ushr 4])
                append(HEX_CHARS[v and 0x0F])
            }
        }
    }

    companion object {
        const val MAX_SIZE = 100

        /** 15 минут — цены и очереди на АЗС меняются быстро. */
        const val TTL_GAS_STATION_MS = 15 * 60 * 1000L

        /** 24 часа — общие вопросы не зависят от местоположения и времени. */
        const val TTL_GENERAL_MS = 24 * 60 * 60 * 1000L

        private val HEX_CHARS = "0123456789abcdef".toCharArray()
    }
}