package com.navrot.aifuelassistant.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.navrot.aifuelassistant.data.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MapTileSourceFallbackTest {

    private lateinit var context: Context
    private lateinit var repository: UserPreferencesRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = UserPreferencesRepository(context)
    }

    @Test
    fun testTileSourceFallbackChainOrderAndPersistence() = runBlocking {
        // Initial state should be null (default to openfreemap)
        val initialSource = repository.mapTileSource.first()
        assertNull(initialSource)

        val tileChain = listOf(TILE_SOURCE_OPENFREEMAP, TILE_SOURCE_VERSATILES, TILE_SOURCE_OSM_RASTER)
        assertEquals(3, tileChain.size)
        assertEquals(TILE_SOURCE_OPENFREEMAP, tileChain[0])
        assertEquals(TILE_SOURCE_VERSATILES, tileChain[1])
        assertEquals(TILE_SOURCE_OSM_RASTER, tileChain[2])

        // When fallback to VERSATILES occurs, save to DataStore
        repository.setMapTileSource(TILE_SOURCE_VERSATILES)
        val secondSource = repository.mapTileSource.first()
        assertEquals(TILE_SOURCE_VERSATILES, secondSource)

        // When vector fails completely, fallback to OSM_RASTER
        repository.setMapTileSource(TILE_SOURCE_OSM_RASTER)
        val rasterSource = repository.mapTileSource.first()
        assertEquals(TILE_SOURCE_OSM_RASTER, rasterSource)
    }
}
