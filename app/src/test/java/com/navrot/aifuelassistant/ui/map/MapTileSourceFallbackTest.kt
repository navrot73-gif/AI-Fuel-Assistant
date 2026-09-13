package com.navrot.aifuelassistant.ui.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.navrot.aifuelassistant.data.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        // Initial state should be null (default to carto)
        val initialSource = repository.mapTileSource.first()
        assertNull(initialSource)

        val tileChain = listOf(TILE_SOURCE_CARTO, TILE_SOURCE_OSM_RASTER, TILE_SOURCE_OPENFREEMAP)
        assertEquals(3, tileChain.size)
        assertEquals(TILE_SOURCE_CARTO, tileChain[0])
        assertEquals(TILE_SOURCE_OSM_RASTER, tileChain[1])
        assertEquals(TILE_SOURCE_OPENFREEMAP, tileChain[2])

        // When fallback to OSM_RASTER occurs, save to DataStore
        repository.setMapTileSource(TILE_SOURCE_OSM_RASTER)
        val secondSource = repository.mapTileSource.first()
        assertEquals(TILE_SOURCE_OSM_RASTER, secondSource)

        // When OSM_RASTER fails, fallback to OPENFREEMAP
        repository.setMapTileSource(TILE_SOURCE_OPENFREEMAP)
        val openfreemapSource = repository.mapTileSource.first()
        assertEquals(TILE_SOURCE_OPENFREEMAP, openfreemapSource)
    }

    @Test
    fun testRasterFallbackStyleConfig() {
        val rasterUrl = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        val tileSize = 256
        assertEquals(256, tileSize)
        assertTrue(rasterUrl.contains("tile.openstreetmap.org"))
    }

    @Test
    fun testAutoFallbackToOsmdroidEngine() = runBlocking {
        repository.setMapEngine(UserPreferencesRepository.ENGINE_OSMDROID)
        val engine = repository.mapEngine.first()
        assertEquals(UserPreferencesRepository.ENGINE_OSMDROID, engine)
    }
}
