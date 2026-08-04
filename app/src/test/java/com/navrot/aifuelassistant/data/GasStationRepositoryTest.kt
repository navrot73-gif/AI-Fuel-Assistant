package com.navrot.aifuelassistant.data

import com.navrot.aifuelassistant.geo.GeoUtils
import org.junit.Assert.*
import org.junit.Test

/**
 * Тесты для [GeoUtils] и [GasStationRepository].
 */
class GasStationRepositoryTest {

    // ==================== GeoUtils.calculateDistance ====================

    @Test
    fun `calculateDistance same point returns zero`() {
        val dist = GeoUtils.calculateDistance(55.1644, 61.4368, 55.1644, 61.4368)
        assertEquals(0.0, dist, 0.001)
    }

    @Test
    fun `calculateDistance chelyabinsk to magnitogorsk is reasonable`() {
        // Челябинск (55.164, 61.437) -> Магнитогорск (53.416, 59.048) ~248.5 км
        val dist = GeoUtils.calculateDistance(55.164, 61.437, 53.416, 59.048)
        assertTrue("Distance should be ~248 km, got $dist", dist in 240.0..300.0)
    }

    @Test
    fun `calculateDistance is symmetric`() {
        val d1 = GeoUtils.calculateDistance(55.0, 60.0, 54.0, 59.0)
        val d2 = GeoUtils.calculateDistance(54.0, 59.0, 55.0, 60.0)
        assertEquals(d1, d2, 0.0001)
    }

    @Test
    fun `calculateDistance short distance is small`() {
        // ~100 метров
        val dist = GeoUtils.calculateDistance(55.1644, 61.4368, 55.1653, 61.4381)
        assertTrue("Short distance should be < 0.5 km, got $dist", dist < 0.5)
    }

    @Test
    fun `calculateDistance equator 1 degree is about 111 km`() {
        val dist = GeoUtils.calculateDistance(0.0, 0.0, 0.0, 1.0)
        assertTrue("1 degree at equator ~111 km, got $dist", dist in 110.0..112.0)
    }
}
