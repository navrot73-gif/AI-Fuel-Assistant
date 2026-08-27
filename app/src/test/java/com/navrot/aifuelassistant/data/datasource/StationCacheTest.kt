package com.navrot.aifuelassistant.data.datasource

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class StationCacheTest {

    private lateinit var context: Context
    private lateinit var jsonParser: StationJsonParser
    private lateinit var cache: StationCache

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        jsonParser = StationJsonParserImpl()
        cache = StationCacheImpl(context, jsonParser)

        // Clear cache file before test
        File(context.filesDir, "stations_cache.json").delete()
    }

    @Test
    fun `loadFromCache returns null when file does not exist`() {
        assertNull(cache.loadFromCache())
        assertNull(cache.getLastCacheUpdateTime())
    }

    @Test
    fun `saveToCache and loadFromCache successfully reads saved stations`() {
        val json = """
            [
              {
                "id": 10,
                "name": "Shell",
                "brand": "Shell",
                "address": "Lenina 10",
                "latitude": 55.16,
                "longitude": 61.40,
                "fuelTypes": [{"type": "AI-92", "price": 50.0, "available": true}],
                "queueTime": 2,
                "reliability": 95
              }
            ]
        """.trimIndent()

        cache.saveToCache(json)

        val loaded = cache.loadFromCache()
        assertNotNull(loaded)
        assertEquals(1, loaded!!.size)
        assertEquals(10, loaded.first().id)
        assertEquals("Shell", loaded.first().name)

        val updateTime = cache.getLastCacheUpdateTime()
        assertNotNull(updateTime)
        assertEquals(true, updateTime!! > 0)
    }
}
