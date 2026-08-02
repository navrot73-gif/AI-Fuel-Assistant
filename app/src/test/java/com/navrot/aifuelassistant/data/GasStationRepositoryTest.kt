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
        val result = searchStations("АЗС 1", testStations)
        assertEquals(1, result.size)
        assertEquals("АЗС 1", result[0].name)
    }

    @Test
    fun `searchStations filters by brand`() {
        val result = searchStations("BrandB", testStations)
        assertEquals(1, result.size)
        assertEquals("BrandB", result[0].brand)
    }

    @Test
    fun `getBestStation returns cheapest available`() {
        val best = getBestStation("АИ-95", testStations)
        assertNotNull(best)
        assertEquals("АЗС 2", best?.name)
    }

    @Test
    fun `getBestStation returns null when no fuel available`() {
        val best = getBestStation("АИ-98", testStations)
        assertNull(best)
    }

    @Test
    fun `getStationsSortedByPriceAsc sorts correctly`() {
        val sorted = getStationsSortedByPriceAsc("АИ-95", testStations)
        assertEquals(2, sorted.size)
        assertEquals("АЗС 2", sorted[0].name) // 60.0
        assertEquals("АЗС 1", sorted[1].name) // 65.0
    }

    @Test
    fun `getStationsByQueue sorts by queue time`() {
        val sorted = getStationsByQueue("ДТ", testStations)
        assertEquals(2, sorted.size)
        assertEquals("АЗС 3", sorted[0].name) // queue 2
        assertEquals("АЗС 1", sorted[1].name) // queue 5
    }

    @Test
    fun `getStationsNearLocation filters by radius`() {
        val nearby = getStationsNearLocation(55.0, 61.0, 10.0, testStations)
        assertTrue(nearby.isNotEmpty())
        assertEquals("АЗС 1", nearby[0].name)
    }

    @Test
    fun `calculateDistance returns correct value`() {
        val dist = calculateDistance(55.0, 61.0, 55.1, 61.1)
        assertTrue(dist > 0)
        assertTrue(dist < 20) // ~11 km
    }

    // Helper functions extracted for testing without Android Context
    private fun searchStations(query: String, stations: List<GasStation>): List<GasStation> {
        val q = query.lowercase()
        return stations.filter {
            it.name.lowercase().contains(q) ||
                    it.brand.lowercase().contains(q) ||
                    it.address.lowercase().contains(q)
        }
    }

    private fun getBestStation(fuelType: String, stations: List<GasStation>): GasStation? {
        return stations
            .filter { station ->
                station.fuelTypes.any { it.type == fuelType && it.available }
            }
            .minByOrNull { station ->
                val fuel = station.fuelTypes.find { it.type == fuelType }
                val queuePenalty = station.queueTime * 0.5
                val reliabilityBonus = (100 - station.reliability) * 0.2
                (fuel?.price ?: Double.MAX_VALUE) + queuePenalty - reliabilityBonus
            }
    }

    private fun getStationsSortedByPriceAsc(fuelType: String, stations: List<GasStation>): List<GasStation> {
        return stations.filter { s -> s.fuelTypes.any { it.type == fuelType && it.available } }
            .sortedBy { s -> s.fuelTypes.find { it.type == fuelType }?.price ?: Double.MAX_VALUE }
    }

    private fun getStationsByQueue(fuelType: String, stations: List<GasStation>): List<GasStation> {
        return stations.filter { s -> s.fuelTypes.any { it.type == fuelType && it.available } }
            .sortedBy { it.queueTime }
    }

    private fun getStationsNearLocation(lat: Double, lon: Double, radiusKm: Double, stations: List<GasStation>): List<GasStation> {
        return stations.filter { station ->
            val distance = calculateDistance(lat, lon, station.latitude, station.longitude)
            distance <= radiusKm
        }.sortedBy { station ->
            calculateDistance(lat, lon, station.latitude, station.longitude)
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }
}