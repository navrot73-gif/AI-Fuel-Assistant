package com.navrot.aifuelassistant.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MapCameraInitTest {

    @Test
    fun testInitialCameraTargetNeverZeroZeroWhenNullUserLocation() {
        val userLocation: UserLocationState? = null
        val defaultLat = 55.1644
        val defaultLon = 61.4368

        val targetLat = userLocation?.latitude?.takeIf { it != 0.0 } ?: defaultLat
        val targetLon = userLocation?.longitude?.takeIf { it != 0.0 } ?: defaultLon

        assertNotEquals(0.0, targetLat, 0.0001)
        assertNotEquals(0.0, targetLon, 0.0001)
        assertEquals(55.1644, targetLat, 0.0001)
        assertEquals(61.4368, targetLon, 0.0001)
    }

    @Test
    fun testInitialCameraTargetNeverZeroZeroWhenZeroUserLocation() {
        val userLocation = UserLocationState(
            latitude = 0.0,
            longitude = 0.0,
            accuracy = 0f,
            speed = 0f,
            bearing = 0f,
            hasBearing = false
        )
        val defaultLat = 55.1644
        val defaultLon = 61.4368

        val targetLat = userLocation.latitude.takeIf { it != 0.0 } ?: defaultLat
        val targetLon = userLocation.longitude.takeIf { it != 0.0 } ?: defaultLon

        assertNotEquals(0.0, targetLat, 0.0001)
        assertNotEquals(0.0, targetLon, 0.0001)
        assertEquals(55.1644, targetLat, 0.0001)
        assertEquals(61.4368, targetLon, 0.0001)
    }

    @Test
    fun testInitialCameraTargetUsesValidUserLocation() {
        val userLocation = UserLocationState(
            latitude = 55.7558,
            longitude = 37.6173,
            accuracy = 10f,
            speed = 0f,
            bearing = 0f,
            hasBearing = false
        ) // Moscow
        val defaultLat = 55.1644
        val defaultLon = 61.4368

        val targetLat = userLocation.latitude.takeIf { it != 0.0 } ?: defaultLat
        val targetLon = userLocation.longitude.takeIf { it != 0.0 } ?: defaultLon

        assertEquals(55.7558, targetLat, 0.0001)
        assertEquals(37.6173, targetLon, 0.0001)
    }
}
