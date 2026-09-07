package com.navrot.aifuelassistant.domain.usecase

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StationQueryFacadeTest {

    private val now = System.currentTimeMillis()

    private val station1 = GasStation(
        id = 1,
        brand = "Газпромнефть",
        name = "АЗС 1",
        latitude = 55.1500,
        longitude = 61.4000,
        address = "Челябинск, ул. Курчатова, 2/1",
        fuelTypes = listOf(FuelPrice("АИ-95", 55.0, available = true, source = FuelDataSource.DEMO, updatedAt = now)),
        queueTime = 0,
        reliability = 100,
        dataSources = setOf(FuelDataSource.DEMO)
    )

    private val station2 = GasStation(
        id = 2,
        brand = "Газпромнефть",
        name = "АЗС 2",
        latitude = 55.1800,
        longitude = 61.3800,
        address = "Челябинск, Свердловский тракт, 12В",
        fuelTypes = listOf(FuelPrice("АИ-95", 56.0, available = true, source = FuelDataSource.DEMO, updatedAt = now)),
        queueTime = 0,
        reliability = 100,
        dataSources = setOf(FuelDataSource.DEMO)
    )

    private val station3 = GasStation(
        id = 3,
        brand = "Лукойл",
        name = "АЗС 3",
        latitude = 55.1600,
        longitude = 61.4200,
        address = "Челябинск, Свердловский тракт, 16/3",
        fuelTypes = listOf(FuelPrice("АИ-95", 57.0, available = true, source = FuelDataSource.DEMO, updatedAt = now)),
        queueTime = 0,
        reliability = 100,
        dataSources = setOf(FuelDataSource.DEMO)
    )

    private val stations = listOf(station1, station2, station3)

    @Test
    fun resolveQuery_withAddressTokens_matchesExactStreetAndHouse() {
        // User GPS is close to station1 (Kurchatova)
        val userLat = 55.1501
        val userLon = 61.4001

        val result = StationQueryFacade.resolveQuery(
            text = "Газпромнефть Свердловский тракт 12",
            stations = stations,
            userLat = userLat,
            userLon = userLon
        )

        assertNotNull(result)
        assertEquals(2, result!!.nearestStation.id)
        assertTrue(result.nearestStation.address.contains("Свердловский"))
    }

    @Test
    fun resolveQuery_withBrandOnly_resolvesNearestByGps() {
        // User GPS is closer to station1 (Kurchatova)
        val userLat = 55.1501
        val userLon = 61.4001

        val result = StationQueryFacade.resolveQuery(
            text = "Газпромнефть",
            stations = stations,
            userLat = userLat,
            userLon = userLon
        )

        assertNotNull(result)
        assertEquals(1, result!!.nearestStation.id)
    }

    @Test
    fun resolveQuery_withNonMatchingAddressTokens_returnsNull() {
        val userLat = 55.1501
        val userLon = 61.4001

        val result = StationQueryFacade.resolveQuery(
            text = "Газпромнефть Несуществующая улица 99",
            stations = stations,
            userLat = userLat,
            userLon = userLon
        )

        assertNull(result)
    }
}
