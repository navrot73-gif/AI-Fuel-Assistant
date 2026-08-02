package com.navrot.aifuelassistant.data

import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import org.junit.Assert.*
import org.junit.Test

class GasStationRepositoryTest {

    private val testStations = listOf(
        GasStation(
            id = 1, name = "АЗС 1", brand = "BrandA", address = "Addr1",
            latitude = 55.0, longitude = 61.0,
            fuelTypes = listOf(
                FuelPrice("АИ-95", 65.0, true),
                FuelPrice("ДТ", 75.0, true)
            ),
            queueTime = 5, reliability = 90
        ),
        GasStation(
            id = 2, name = "АЗС 2", brand = "BrandB", address = "Addr2",
            latitude = 55.1, longitude = 61.1,
            fuelTypes = listOf(
                FuelPrice("АИ-95", 60.0, true),
                FuelPrice("ДТ", 70.0, false)
            ),
            queueTime = 10, reliability = 80
        ),
        GasStation(
            id = 3, name = "АЗС 3", brand = "BrandC", address = "Addr3",
            latitude = 55.2, longitude = 61.2,
            fuelTypes = listOf(
                FuelPrice("АИ-95", 70.0, false),
                FuelPrice("ДТ", 80.0, true)
            ),
            queueTime = 2, reliability = 95
        )
    )

    @Test
    fun `searchStations filters by name`() {
        val repo = GasStationRepository(android.app.Application())
        val result = repo.searchStations("АЗС 1", testStations)
        assertEquals(1, result.size)
        assertEquals("АЗС 1", result[0].name)
    }

    @Test
    fun `searchStations filters by brand`() {
        val repo = GasStationRepository(android.app.Application())
        val result = repo.searchStations("BrandB", testStations)
        assertEquals(1, result.size)
        assertEquals("BrandB", result[0].brand)
    }

    @Test
    fun `getBestStation returns cheapest available`() {
        val repo = GasStationRepository(android.app.Application())
        val best = repo.getBestStation("АИ-95", testStations)
        assertNotNull(best)
        assertEquals("АЗС 2", best?.name)
    }

    @Test
    fun `getBestStation returns null when no fuel available`() {
        val repo = GasStationRepository(android.app.Application())
        val best = repo.getBestStation("АИ-98", testStations)
        assertNull(best)
    }

    @Test
    fun `getStationsSortedByPriceAsc sorts correctly`() {
        val repo = GasStationRepository(android.app.Application())
        val sorted = repo.getStationsSortedByPriceAsc("АИ-95", null, null, 100.0, testStations)
        assertEquals(2, sorted.size)
        assertEquals("АЗС 2", sorted[0].name) // 60.0
        assertEquals("АЗС 1", sorted[1].name) // 65.0
    }

    @Test
    fun `getStationsByQueue sorts by queue time`() {
        val repo = GasStationRepository(android.app.Application())
        val sorted = repo.getStationsByQueue("ДТ", null, null, 100.0, testStations)
        assertEquals(2, sorted.size)
        assertEquals("АЗС 3", sorted[0].name) // queue 2
        assertEquals("АЗС 1", sorted[1].name) // queue 5
    }

    @Test
    fun `getStationsNearLocation filters by radius`() {
        val repo = GasStationRepository(android.app.Application())
        val nearby = repo.getStationsNearLocation(55.0, 61.0, 10.0, testStations)
        assertTrue(nearby.isNotEmpty())
        assertEquals("АЗС 1", nearby[0].name)
    }

    @Test
    fun `calculateDistance returns correct value`() {
        val dist = GasStationRepository.calculateDistance(55.0, 61.0, 55.1, 61.1)
        assertTrue(dist > 0)
        assertTrue(dist < 20) // ~11 km
    }
}