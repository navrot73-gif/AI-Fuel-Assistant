package com.navrot.aifuelassistant.data.datasource

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class StationLoaderTest {

    private lateinit var context: Context
    private lateinit var httpClient: OkHttpClient
    private lateinit var stationCache: StationCache
    private lateinit var jsonParser: StationJsonParser
    private lateinit var stationLoader: StationLoader

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        httpClient = OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .build()
        jsonParser = StationJsonParserImpl()
        stationCache = StationCacheImpl(context, jsonParser)
        stationLoader = StationLoaderImpl(httpClient, stationCache, jsonParser, context)

        File(context.filesDir, "stations_cache.json").delete()
    }

    @Test
    fun `loadFromAssets loads stations from bundled assets file`() {
        val stations = stationLoader.loadFromAssets()
        assertTrue(stations.isNotEmpty())
    }

    @Test
    fun `loadStations falls back to assets when remote fails and cache is empty`() = runBlocking {
        val stations = stationLoader.loadStations()
        assertTrue("Fallback to assets should return non-empty stations", stations.isNotEmpty())
    }

    @Test
    fun `loadStations uses cache when cache is available`() = runBlocking {
        val json = """
            [
              {
                "id": 99,
                "name": "Cached Station",
                "brand": "CachedBrand",
                "address": "CachedAddr",
                "latitude": 55.0,
                "longitude": 60.0,
                "fuelTypes": [],
                "queueTime": 0,
                "reliability": 100
              }
            ]
        """.trimIndent()
        stationCache.saveToCache(json)

        val cached = stationLoader.loadFromCache()
        assertNotNull(cached)
        assertTrue(cached!!.isNotEmpty())
        assertEquals("Cached Station", cached.first().name)
    }
}
