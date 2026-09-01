package com.navrot.aifuelassistant.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class VectorOfflineManagerTest {

    @Test
    fun `calculateCityBounds creates correct bounding box around center`() {
        val manager = VectorOfflineManager(RuntimeEnvironment.getApplication())
        val lat = 55.1644
        val lon = 61.4368
        val radiusKm = 20.0

        val bounds = manager.calculateCityBounds(lat, lon, radiusKm)

        val center = bounds.center
        assertEquals(lat, center.latitude, 0.05)
        assertEquals(lon, center.longitude, 0.05)
        assertTrue(bounds.latitudeNorth > lat)
        assertTrue(bounds.latitudeSouth < lat)
        assertTrue(bounds.longitudeEast > lon)
        assertTrue(bounds.longitudeWest < lon)
    }

    @Test
    fun `createRegionDefinition creates definition with valid zoom and bounds`() {
        val manager = VectorOfflineManager(RuntimeEnvironment.getApplication())
        val bounds = LatLngBounds.Builder()
            .include(LatLng(55.2, 61.5))
            .include(LatLng(55.0, 61.3))
            .build()

        val definition = manager.createRegionDefinition(
            bounds = bounds,
            styleUrl = VectorOfflineManager.DEFAULT_STYLE_URL,
            minZoom = 10.0,
            maxZoom = 17.0,
            pixelRatio = 2.0f
        )

        assertNotNull(definition)
        assertEquals(VectorOfflineManager.DEFAULT_STYLE_URL, definition.styleURL)
        assertEquals(10.0, definition.minZoom, 0.001)
        assertEquals(17.0, definition.maxZoom, 0.001)
        assertEquals(2.0f, definition.pixelRatio, 0.001f)
        val defBounds = definition.bounds
        assertNotNull(defBounds)
        defBounds?.let {
            assertEquals(bounds.latitudeNorth, it.latitudeNorth, 0.001)
            assertEquals(bounds.latitudeSouth, it.latitudeSouth, 0.001)
        }
    }
}
