package com.navrot.aifuelassistant.ui.map

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты для [StationClusterManager] — конвертация станций в GeoJSON FeatureCollection.
 *
 * Не требует Robolectric/Android SDK: тестируется только логика преобразования
 * данных (List<GasStation> → FeatureCollection), без рендера слоёв.
 */
class StationClusterManagerTest {

    private val manager = StationClusterManager()

    private fun createStation(
        id: Int,
        lat: Double = 55.0 + id * 0.01,
        lon: Double = 61.0 + id * 0.01,
        fuelPrice: Double = 65.0,
        fuelType: String = "АИ-95"
    ) = GasStation(
        id = id,
        name = "Station $id",
        brand = "Lukoil",
        address = "Addr $id",
        latitude = lat,
        longitude = lon,
        fuelTypes = listOf(
            FuelPrice(
                type = fuelType,
                price = fuelPrice,
                available = true,
                source = FuelDataSource.BENZONAVT,
                updatedAt = System.currentTimeMillis()
            )
        ),
        queueTime = 0,
        reliability = 100
    )

    @Test
    fun `stationsToFeatureCollection converts empty list to empty feature collection`() {
        val fc = manager.stationsToFeatureCollection(emptyList(), setOf("АИ-95"))
        assertEquals(0, fc.features()?.size ?: 0)
    }

    @Test
    fun `stationsToFeatureCollection converts single station with correct coordinates`() {
        val station = createStation(id = 1, lat = 55.1644, lon = 61.4368)
        val fc = manager.stationsToFeatureCollection(listOf(station), setOf("АИ-95"))

        val features = fc.features()
        assertNotNull("FeatureCollection.features() must not be null", features)
        assertEquals(1, features!!.size)

        val feature = features[0]
        val geometry = feature.geometry()
        assertNotNull("Feature must have geometry", geometry)

        val coords = (geometry as org.maplibre.geojson.Point).coordinates()
        assertNotNull("Coordinates must not be null", coords)
        assertTrue("Coordinates must have at least 2 elements", coords.size >= 2)
        assertEquals(61.4368, coords[0], 0.0001)
        assertEquals(55.1644, coords[1], 0.0001)
    }

    @Test
    fun `stationsToFeatureCollection preserves station id as property`() {
        val station = createStation(id = 42)
        val fc = manager.stationsToFeatureCollection(listOf(station), setOf("АИ-95"))

        val feature = fc.features()!![0]
        val stationIdStr = feature.getStringProperty(StationClusterManager.PROP_STATION_ID)
        val stationIdNum = feature.getNumberProperty(StationClusterManager.PROP_STATION_ID)
        val stationId = stationIdStr?.toIntOrNull() ?: stationIdNum?.toInt()
        assertEquals("stationId property must be preserved", 42, stationId)
    }

    @Test
    fun `stationsToFeatureCollection preserves station name as property`() {
        val station = createStation(id = 1).copy(name = "Лукойл Мира 65")
        val fc = manager.stationsToFeatureCollection(listOf(station), setOf("АИ-95"))

        val feature = fc.features()!![0]
        val name = feature.getStringProperty(StationClusterManager.PROP_STATION_NAME)
        assertEquals("Лукойл Мира 65", name)
    }

    @Test
    fun `stationsToFeatureCollection sets status property based on selected fuel type`() {
        val station = createStation(id = 1, fuelPrice = 65.0)
        val fc = manager.stationsToFeatureCollection(listOf(station), setOf("АИ-95"))

        val feature = fc.features()!![0]
        val status = feature.getStringProperty(StationClusterManager.PROP_STATUS)
        assertNotNull("status property must be set", status)
        assertTrue(
            "status must be one of AVAILABLE/NO_FUEL/UNKNOWN, got: $status",
            status in listOf("AVAILABLE", "NO_FUEL", "UNKNOWN")
        )
    }

    @Test
    fun `stationsToFeatureCollection sets statusColor property matching status`() {
        val station = createStation(id = 1)
        val fc = manager.stationsToFeatureCollection(listOf(station), setOf("АИ-95"))

        val feature = fc.features()!![0]
        val statusColor = feature.getStringProperty(StationClusterManager.PROP_STATUS_COLOR)
        val status = feature.getStringProperty(StationClusterManager.PROP_STATUS)

        assertNotNull("statusColor must be set", statusColor)
        assertNotNull("status must be set", status)

        val expectedColor = when (status) {
            "AVAILABLE" -> StationClusterManager.COLOR_AVAILABLE_REF
            "NO_FUEL" -> StationClusterManager.COLOR_NO_FUEL_REF
            else -> StationClusterManager.COLOR_UNKNOWN_REF
        }
        assertEquals("statusColor must match status", expectedColor, statusColor)
    }

    @Test
    fun `stationsToFeatureCollection handles multiple stations`() {
        val stations = listOf(
            createStation(id = 1, lat = 55.0, lon = 61.0),
            createStation(id = 2, lat = 55.1, lon = 61.1),
            createStation(id = 3, lat = 55.2, lon = 61.2)
        )
        val fc = manager.stationsToFeatureCollection(stations, setOf("АИ-95"))

        assertEquals(3, fc.features()?.size)
    }

    @Test
    fun `resetLayersAttached clears internal state`() {
        manager.resetLayersAttached()
        manager.resetLayersAttached()
    }
}
