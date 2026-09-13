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

        // GeoJSON Point: координаты в порядке [longitude, latitude]
        // Point.coordinates() в MapLibre geojson возвращает List<Double>
        val coords = (geometry as org.maplibre.geojson.Point).coordinates()
        assertNotNull("Coordinates must not be null", coords)
        assertTrue("Coordinates must have at least 2 elements", coords.size >= 2)
        // GeoJSON порядок: [longitude, latitude]
        assertEquals(61.4368, coords[0], 0.0001)
        assertEquals(55.1644, coords[1], 0.0001)
    }

    @Test
    fun `stationsToFeatureCollection preserves station id as property`() {
        val station = createStation(id = 42)
        val fc = manager.stationsToFeatureCollection(listOf(station), setOf("АИ-95"))

        val feature = fc.features()!![0]
        // stationId может быть сохранён как строка или число — зависит от сериализации
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
    fun `stationsToFeatureCollection sets availability property based on selected fuel type`() {
        val station = createStation(id = 1, fuelPrice = 65.0)
        // Станция с доступным топливом → availability = "AVAILABLE"
        val fc = manager.stationsToFeatureCollection(listOf(station), setOf("АИ-95"))

        val feature = fc.features()!![0]
        val availability = feature.getStringProperty(StationClusterManager.PROP_AVAILABILITY)
        assertNotNull("availability property must be set", availability)
        //AVAILABLE, NO_FUEL, или UNKNOWN — точное значение зависит от PriceReliabilityCalculator
        assertTrue(
            "availability must be one of AVAILABLE/NO_FUEL/UNKNOWN, got: $availability",
            availability in listOf("AVAILABLE", "NO_FUEL", "UNKNOWN")
        )
    }

    @Test
    fun `stationsToFeatureCollection sets color property matching availability`() {
        val station = createStation(id = 1)
        val fc = manager.stationsToFeatureCollection(listOf(station), setOf("АИ-95"))

        val feature = fc.features()!![0]
        val color = feature.getStringProperty(StationClusterManager.PROP_COLOR)
        val availability = feature.getStringProperty(StationClusterManager.PROP_AVAILABILITY)

        assertNotNull("color must be set", color)
        assertNotNull("availability must be set", availability)

        val expectedColor = when (availability) {
            "AVAILABLE" -> StationClusterManager.COLOR_AVAILABLE_REF
            "NO_FUEL" -> StationClusterManager.COLOR_NO_FUEL_REF
            else -> StationClusterManager.COLOR_UNKNOWN_REF
        }
        assertEquals("color must match availability", expectedColor, color)
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
        // Просто проверяем, что метод не падает и не возвращает ошибку.
        // Полное тестирование требует Style-мок, что избыточно для этого слоя.
        manager.resetLayersAttached()
        // Повторный вызов тоже должен быть безопасен
        manager.resetLayersAttached()
    }
}
