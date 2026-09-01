package com.navrot.aifuelassistant.domain.usecase

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NearestStationFinderTest {

    @Test
    fun findNearestStationByBrand_osmOnlyStationCloserWithoutPrice_returnsOsmStationAsNearestAndSuggestsAlternative() {
        val userLat = 55.1644
        val userLon = 61.4368
        val now = System.currentTimeMillis()

        // Closer OSM-only station (1 km away) with 0 price / no fuel
        val osmCloserStation = GasStation(
            id = -12345,
            name = "Лукойл OSM",
            brand = "Лукойл",
            address = "ул. Ленина, 1",
            latitude = 55.17,
            longitude = 61.44,
            fuelTypes = listOf(
                FuelPrice(type = "АИ-95", price = 0.0, available = false, source = FuelDataSource.OVERPASS, updatedAt = 0L)
            ),
            queueTime = 0,
            reliability = 30,
            dataSources = setOf(FuelDataSource.OVERPASS),
            updatedAt = 0L,
            osmId = "osm:12345"
        )

        // Farther Base station (3 km away) with available fuel
        val baseFartherStation = GasStation(
            id = 101,
            name = "Лукойл №12",
            brand = "Лукойл",
            address = "ул. Мира, 10",
            latitude = 55.19,
            longitude = 61.46,
            fuelTypes = listOf(
                FuelPrice(type = "АИ-95", price = 56.50, available = true, source = FuelDataSource.BENZONAVT, updatedAt = now)
            ),
            queueTime = 2,
            reliability = 95,
            dataSources = setOf(FuelDataSource.BENZONAVT),
            updatedAt = now
        )

        val stations = listOf(baseFartherStation, osmCloserStation)

        val result = NearestStationFinder.findNearestStationByBrand(
            stations = stations,
            brand = "Лукойл",
            userLat = userLat,
            userLon = userLon,
            fuelType = "АИ-95"
        )

        assertNotNull(result)
        assertEquals(-12345, result!!.nearestStation.id)
        assertFalse(result.isNearestFuelAvailable)
        assertNotNull(result.alternativeWithFuel)
        assertEquals(101, result.alternativeWithFuel!!.id)
    }

    @Test
    fun findNearestStationByBrand_whenNearestHasFuel_returnsNoAlternative() {
        val userLat = 55.1644
        val userLon = 61.4368
        val now = System.currentTimeMillis()

        val stationWithFuel = GasStation(
            id = 201,
            name = "Газпромнефть №5",
            brand = "Газпромнефть",
            address = "ул. Свободы, 20",
            latitude = 55.165,
            longitude = 61.437,
            fuelTypes = listOf(
                FuelPrice(type = "АИ-95", price = 54.90, available = true, source = FuelDataSource.BENZONAVT, updatedAt = now)
            ),
            queueTime = 1,
            reliability = 90,
            dataSources = setOf(FuelDataSource.BENZONAVT),
            updatedAt = now
        )

        val result = NearestStationFinder.findNearestStationByBrand(
            stations = listOf(stationWithFuel),
            brand = "Газпромнефть",
            userLat = userLat,
            userLon = userLon,
            fuelType = "АИ-95"
        )

        assertNotNull(result)
        assertEquals(201, result!!.nearestStation.id)
        assertTrue(result.isNearestFuelAvailable)
        assertNull(result.alternativeWithFuel)
    }
}
