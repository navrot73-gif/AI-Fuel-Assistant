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
 * PR #185 / #187: Локальный кеш для векторных стилей MapLibre (OpenFreeMap liberty/bright).
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
 * Решение (offline-first, background-populated):
 *   - resolveStyleUri() НЕ блокирует main thread.
 *   - Если валидный кеш есть — возвращает file:// URI мгновенно.
 *   - Если кеша НЕТ — возвращает remote URL сразу, а в background thread
 *     скачивает Style JSON и сохраняет в filesDir/map_styles/<key>.json.
 *     На следующем запуске кеш уже будет валиден — загрузка мгновенная.
 *   - Если кеш устарел (> 7 дней) — фоновое обновление (старый кеш остаётся
 *     рабочим, пока не приедет новый).
 *
 *   ВАЖНО (map-logs7.txt regression): в PR #185 resolveStyleUri() вызывал
 *   OkHttp.execute() синхронно из main thread (applyStyleWithFallback →
 *   onMapReady callback), что вызывало android.os.NetworkOnMainThreadException
 *   и кеш НЕ создавался. В #187 скачивание перенесено в background thread.
 *
 *   Ожидаемый win:
 *     - Первый запуск: ведёт себя как раньше (remote URL, 2-3 сек).
 *       НО в фоне кеш готовится для следующего запуска.
 *     - Второй и последующие запуски: file:// → <100мс вместо 2-3 сек.
 *     - Оффлайн: file:// → карта (background/water/road casing) отрисуется
 *       из кеша, тайлы поверх — по мере поступления.
 *
 * Использование (в MapLibreView.applyStyleWithFallback):
 *   val styleUri = mapStyleCache.resolveStyleUri(
 *       sourceKey = "openfreemap",
 *       remoteUrl = "https://tiles.openfreemap.org/styles/liberty",
 *       cacheKey = "openfreemap_light"
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
     * Возвращает URI стиля для загрузки. НЕ блокирует main thread.
     *
     * Стратегия (offline-first, background-populated — PR #187):
     *  1. Если в кеше есть ВАЛИДНЫЙ файл (≥1 КБ) — возвращаем file:// URI.
     *     Параллельно, если кеш устарел (> 7 дней), запускаем фоновое обновление.
     *  2. Если кеша НЕТ — возвращаем remoteUrl сразу и в фоне скачиваем файл
     *     для следующего запуска. Текущий запуск использует remote URL,
     *     что эквивалентно поведению без кеша (PR #183).
     *
     * ВАЖНО: resolveStyleUri() вызывается из applyStyleWithFallback, который
     * работает на main thread (getMapAsync callback). Поэтому сетевой запрос
     * должен быть asynchronous — иначе android.os.NetworkOnMainThreadException
     * (regression PR #185, fixed в PR #187).
     *
     * @param sourceKey ключ источника (например, "openfreemap").
     * @param remoteUrl URL Style JSON с сети.
     * @param cacheKey уникальный ключ для кеша (например, "openfreemap_light").
     *                 Один и тот же стиль для light/dark должен иметь РАЗНЫЕ cacheKey.
     * @return URI для передачи в Style.Builder().fromUri() — либо "file://...",
     *         либо исходный remoteUrl.
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

        // Кеша нет — НЕ блокируем main thread.
        // Возвращаем remote URL сразу, а кеш готовим в фоне для следующего запуска.
        Timber.tag(TAG).i(
            "No cached style %s — using remote URL, scheduling background download for next launch",
            cacheKey
        )
        scheduleRefresh(cacheKey, remoteUrl)
        return remoteUrl
    }

    /**
     * Принудительное обновление кеша в фоне (например, после удаления старого).
     * Fire-and-forget.
     */
    fun refreshAsync(cacheKey: String, remoteUrl: String) {
        scheduleRefresh(cacheKey, remoteUrl)
    }

    private fun scheduleRefresh(cacheKey: String, remoteUrl: String) {
        // PR #187: daemon thread, не блокирует main thread.
        // OkHttp.execute() — блокирующий вызов, но он в background.
        thread(name = "MapStyleCache-refresh-$cacheKey", isDaemon = true) {
            try {
                val cacheFile = File(cacheDir, "$cacheKey.json")
                downloadToBlocking(cacheFile, remoteUrl)
                Timber.tag(TAG).i(
                    "Background download of style %s succeeded (size=%dB) — will be used on next launch",
                    cacheKey, cacheFile.length()
                )
            } catch (e: Exception) {
                Timber.tag(TAG).w(
                    e,
                    "Background download of style %s failed (url=%s)",
                    cacheKey, remoteUrl
                )
            }
        }
    }

    /**
     * Синхронная (блокирующая) версия скачивания — вызывается ТОЛЬКО из
     * background thread (scheduleRefresh). На main thread не вызывать:
     * android.os.NetworkOnMainThreadException.
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
