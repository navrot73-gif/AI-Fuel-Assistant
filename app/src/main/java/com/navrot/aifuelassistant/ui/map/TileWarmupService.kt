package com.navrot.aifuelassistant.ui.map

import android.content.Context
import android.util.Log
import com.navrot.aifuelassistant.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TileWarmupService @Inject constructor(
    private val context: Context,
    private val httpClient: OkHttpClient
) {

    companion object {
        private const val TAG = "TileWarmup"
        // Chelyabinsk center
        private const val CENTER_LAT = 55.164
        private const val CENTER_LON = 61.436
        // Zoom levels to prefetch
        private val ZOOMS = intArrayOf(11, 12, 13, 14)
        // 3x3 grid around center
        private const val GRID_RADIUS = 1
        // CARTO Dark Matter no-labels tile template (from OsmMapView)
        private const val TILE_TEMPLATE = "https://{s}.basemaps.cartocdn.com/dark_nolabels/{z}/{x}/{y}.png"
        private val SUBDOMAINS = arrayOf("a", "b", "c", "d")
    }

    /**
     * Start background prefetch of Chelyabinsk tiles.
     * Fire-and-forget, errors ignored, runs on IO dispatcher with low priority.
     */
    fun startPrefetch() {
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "TileWarmup-Prefetch").apply { priority = Thread.MIN_PRIORITY }
        }.execute {
            prefetchTiles()
        }
    }

    private fun prefetchTiles() {
        var totalPrefetched = 0

        for (zoom in ZOOMS) {
            // Calculate center tile at this zoom
            val (centerX, centerY) = latLonToTile(CENTER_LAT, CENTER_LON, zoom)

            // 3x3 grid around center
            for (dx in -GRID_RADIUS..GRID_RADIUS) {
                for (dy in -GRID_RADIUS..GRID_RADIUS) {
                    val x = centerX + dx
                    val y = centerY + dy

                    // Valid tile range check
                    val maxTile = (1 shl zoom) - 1
                    if (x !in 0..maxTile || y !in 0..maxTile) continue

                    // Rotate subdomain to distribute load
                    val subdomain = SUBDOMAINS[(x + y + zoom) % SUBDOMAINS.size]
                    val url = TILE_TEMPLATE
                        .replace("{s}", subdomain)
                        .replace("{z}", zoom.toString())
                        .replace("{x}", x.toString())
                        .replace("{y}", y.toString())

                    try {
                        val request = Request.Builder()
                            .url(url)
                            .header("User-Agent", context.packageName)
                            .build()
                        httpClient.newCall(request).execute().use { response ->
                            response.body?.close()
                            if (response.isSuccessful) {
                                totalPrefetched++
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore errors as per requirements
                    }
                }
            }
        }

        Log.d(TAG, "TileWarmup: prefetched $totalPrefetched tiles for chelyabinsk")
    }

    /**
     * Convert lat/lon to tile coordinates (XYZ scheme).
     */
    private fun latLonToTile(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> {
        val n = Math.pow(2.0, zoom.toDouble())
        val x = ((lon + 180.0) / 360.0 * n).toInt()
        val latRad = Math.toRadians(lat)
        val y = ((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n).toInt()
        return x to y
    }
}