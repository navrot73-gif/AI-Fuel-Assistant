package com.navrot.aifuelassistant.ui.map

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread

/**
 * PR #185: Локальный кеш для векторных стилей MapLibre (OpenFreeMap liberty/bright).
 *
 * Проблема (map-logs6.txt):
 *   T+0ms     activity_created
 *   T+582ms   Camera initialized
 *   T+3071ms  Style loaded  ← 2.5 сек на скачивание + парсинг Style JSON
 *   T+7034ms  First tile loaded
 *   T+13073ms Tile source "active within timeout"  ← итого 13 секунд на старт
 *
 *   3 секунды из 13 — это скачивание Style JSON (~50 КБ) с
 *   https://tiles.openfreemap.org/styles/liberty. При повторных запусках
 *   скачивание повторяется каждый раз.
 *
 * Решение:
 *   - При первом запуске скачиваем JSON синхронно (один раз) и сохраняем
 *     в filesDir/map_styles/<key>.json.
 *   - При последующих запусках возвращаем file:// URI — загрузка стиля
 *     занимает <100мс вместо 2-3 секунд.
 *   - В фоне (daemon thread) раз в REFRESH_INTERVAL_MS обновляем кеш,
 *     чтобы подхватывать обновления спрайтов/глифов.
 *
 *   Ожидаемый win: 13 сек → ~10 сек (ускорение ~25% на старте).
 *   На повторных запусках: 13 сек → ~10 сек (Style JSON мгновенно,
 *   тайлы всё равно с CDN).
 *
 *   Если CDN совсем медленный или оффлайн — кеш позволит хотя бы показать
 *   UI каркаса карты (background, water), не блокируя запуск.
 *
 * Использование (в MapLibreView.applyStyleWithFallback):
 *   val styleUri = mapStyleCache.resolveStyleUri(
 *       sourceKey = "openfreemap",
 *       remoteUrl = "https://tiles.openfreemap.org/styles/liberty",
 *       cacheKey = "openfreemap_light",
 *       isDarkMode = isDarkMode
 *   )
 *   map.setStyle(Style.Builder().fromUri(styleUri)) { ... }
 *
 *   Для растровых источников (osm_raster) и для LOCAL_STYLE_ASSET_URL
 *   кеширование не используется — там и так мгновенно из assets.
 */
@Singleton
class MapStyleCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "MapStyleCache"
        private const val STYLE_DIR_NAME = "map_styles"

        /** Кеш считаем свежим 7 дней, после — обновляем в фоне (но НЕ блокирующе). */
        private const val REFRESH_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000

        /** Минимальный размер файла, чтобы считать кеш валидным (защита от пустых записей). */
        private const val MIN_VALID_CACHE_SIZE_BYTES = 1024L
    }

    private val cacheDir: File by lazy {
        File(context.filesDir, STYLE_DIR_NAME).also { it.mkdirs() }
    }

    /**
     * Возвращает URI стиля для загрузки.
     *
     * Стратегия (offline-first):
     *  1. Если в кеше есть ВАЛИДНЫЙ файл (≥1 КБ) — возвращаем file:// URI.
     *     Параллельно, если кеш устарел (> 7 дней), запускаем фоновое обновление.
     *  2. Если кеша нет — синхронно скачиваем один раз (это первый запуск).
     *     Если синхронная загрузка упала — возвращаем remote URL как fallback.
     *
     * ВАЖНО: метод НЕ блокирует, если кеш валиден. Только первый запуск заблокирует
     * на время скачивания (~1-3 сек).
     *
     * @param sourceKey ключ источника (например, "openfreemap").
     * @param remoteUrl URL Style JSON с сети.
     * @param cacheKey уникальный ключ для кеша (например, "openfreemap_light").
     *                 Один и тот же стиль для light/dark должен иметь РАЗНЫЕ cacheKey.
     * @return URI для передачи в Style.Builder().fromUri() — либо "file://...",
     *         либо исходный remoteUrl, либо "asset://..." при критической ошибке.
     */
    fun resolveStyleUri(
        sourceKey: String,
        remoteUrl: String,
        cacheKey: String
    ): String {
        val cacheFile = File(cacheDir, "$cacheKey.json")

        if (cacheFile.exists() && cacheFile.length() >= MIN_VALID_CACHE_SIZE_BYTES) {
            // Кеш есть — используем его мгновенно
            val cacheAgeMs = System.currentTimeMillis() - cacheFile.lastModified()
            if (cacheAgeMs > REFRESH_INTERVAL_MS) {
                Timber.tag(TAG).d(
                    "Cache stale (age=%dms > %dms), scheduling background refresh for %s",
                    cacheAgeMs, REFRESH_INTERVAL_MS, cacheKey
                )
                scheduleRefresh(cacheKey, remoteUrl)
            }
            Timber.tag(TAG).d(
                "Using cached style %s (age=%dms, size=%dB, file=%s)",
                cacheKey, cacheAgeMs, cacheFile.length(), cacheFile.absolutePath
            )
            return "file://${cacheFile.absolutePath}"
        }

        // Кеша нет — синхронно скачиваем (первый запуск).
        // Это блокирует applyStyleWithFallback на 1-3 сек, но это лучше,
        // чем загружать remote URL каждый раз (было 3 сек КАЖДЫЙ запуск).
        return try {
            downloadToBlocking(cacheFile, remoteUrl)
            Timber.tag(TAG).i(
                "First-time download of style %s succeeded (size=%dB)",
                cacheKey, cacheFile.length()
            )
            "file://${cacheFile.absolutePath}"
        } catch (e: Exception) {
            Timber.tag(TAG).w(
                e,
                "First-time download failed for %s (url=%s), falling back to remote URL",
                cacheKey, remoteUrl
            )
            remoteUrl
        }
    }

    /**
     * Принудительное обновление кеша в фоне (например, после удаления старого).
     * Fire-and-forget.
     */
    fun refreshAsync(cacheKey: String, remoteUrl: String) {
        scheduleRefresh(cacheKey, remoteUrl)
    }

    private fun scheduleRefresh(cacheKey: String, remoteUrl: String) {
        // Используем thread вместо coroutine, чтобы не тащить зависимость от
        // Dispatchers.IO в non-suspend context.
        thread(name = "MapStyleCache-refresh-$cacheKey", isDaemon = true) {
            try {
                val cacheFile = File(cacheDir, "$cacheKey.json")
                downloadToBlocking(cacheFile, remoteUrl)
                Timber.tag(TAG).d(
                    "Background refresh of style %s succeeded (size=%dB)",
                    cacheKey, cacheFile.length()
                )
            } catch (e: Exception) {
                Timber.tag(TAG).w(
                    e,
                    "Background refresh of style %s failed (url=%s)",
                    cacheKey, remoteUrl
                )
            }
        }
    }

    /**
     * Синхронная (блокирующая) версия скачивания — для первого запуска, когда
     * кеша ещё нет и мы хотим его создать ДО того, как вернуть URI вызывающему
     * коду. OkHttp.newCall().execute() — блокирующий вызов, корутины не нужны.
     *
     * Атомарность: пишет во временный файл, потом rename. Если что-то упало —
     * старый кеш НЕ затрагивается.
     *
     * @throws IOException при сетевой ошибке или ошибке записи.
     */
    @Throws(IOException::class)
    private fun downloadToBlocking(cacheFile: File, remoteUrl: String) {
        val tmpFile = File(cacheDir, "${cacheFile.name}.tmp")
        val request = Request.Builder()
            .url(remoteUrl)
            .header("User-Agent", context.packageName)
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} ${response.message} for $remoteUrl")
            }
            val body = response.body
                ?: throw IOException("Empty response body for $remoteUrl")

            tmpFile.outputStream().use { out ->
                body.byteStream().use { it.copyTo(out) }
            }

            if (tmpFile.length() < MIN_VALID_CACHE_SIZE_BYTES) {
                tmpFile.delete()
                throw IOException("Downloaded file too small (${tmpFile.length()}B) for $remoteUrl")
            }

            // Atomic replace: удаляем старый, переименовываем tmp в финальный.
            if (cacheFile.exists()) cacheFile.delete()
            if (!tmpFile.renameTo(cacheFile)) {
                throw IOException("Failed to rename ${tmpFile.name} to ${cacheFile.name}")
            }
        }
    }

    /**
     * Для диагностики: возвращает размер кеша в байтах и количество файлов.
     */
    fun getCacheStats(): Pair<Long, Int> {
        val files = cacheDir.listFiles()?.toList() ?: emptyList()
        val totalSize = files.sumOf { it.length() }
        return totalSize to files.size
    }

    /**
     * Очищает весь кеш (для тестов или manual reset через Settings).
     */
    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
        Timber.tag(TAG).d("Cache cleared (dir=%s)", cacheDir.absolutePath)
    }
}
