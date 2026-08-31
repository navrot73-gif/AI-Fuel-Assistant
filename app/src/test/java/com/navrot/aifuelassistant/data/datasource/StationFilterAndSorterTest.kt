package com.navrot.aifuelassistant.data.datasource

import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import com.navrot.aifuelassistant.domain.reliability.PriceReliabilityCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StationFilterAndSorterTest {

    private lateinit var filterAndSorter: StationFilterAndSorter

    @Before
    fun setUp() {
        filterAndSorter = StationFilterAndSorterImpl()
    }

    private fun createStation(
        id: Int,
        name: String,
        brand: String,
        address: String,
        lat: Double,
        lon: Double,
        price: Double,
        queueTime: Int,
        available: Boolean = true,
        updatedAt: Long = System.currentTimeMillis()
    ): GasStation {
        return GasStation(
            id = id,
            name = name,
            brand = brand,
            address = address,
            latitude = lat,
            longitude = lon,
            fuelTypes = listOf(FuelPrice(type = "AI-95", price = price, available = available, updatedAt = updatedAt)),
            queueTime = queueTime,
            reliability = 100
        )
    }

    @Test
    fun `stations with NO_FUEL are NOT excluded from sorting and cheapest`() {
        val now = System.currentTimeMillis()
        val s1 = createStation(1, "No Fuel Cheap", "B1", "A1", 55.0, 60.0, 40.0, 1, available = false, updatedAt = now)
        val s2 = createStation(2, "Available Expensive", "B2", "A2", 55.0, 60.0, 50.0, 1, available = true, updatedAt = now)

        val cheapest = filterAndSorter.getCheapestStation(listOf(s1, s2), "AI-95")
        assertEquals(1, cheapest?.id)

        val asc = filterAndSorter.sortPriceAscending(listOf(s1, s2), "AI-95")
        assertEquals(2, asc.size)
        assertEquals(1, asc[0].id)
    }

    @Test
    fun `calculateFuelAvailability calculates status correctly for fresh and old marks`() {
        val now = System.currentTimeMillis()
        val freshAvailable = createStation(1, "S1", "B1", "A1", 55.0, 60.0, 50.0, 1, available = true, updatedAt = now)
        val freshNoFuel = createStation(2, "S2", "B2", "A2", 55.0, 60.0, 50.0, 1, available = false, updatedAt = now)
        val oldMark = createStation(3, "S3", "B3", "A3", 55.0, 60.0, 50.0, 1, available = true, updatedAt = now - (9 * 60 * 60 * 1000L))

        assertEquals(
            FuelAvailabilityStatus.AVAILABLE,
            PriceReliabilityCalculator.calculateFuelAvailability(freshAvailable, "AI-95", now)
        )
        assertEquals(
            FuelAvailabilityStatus.NO_FUEL,
            PriceReliabilityCalculator.calculateFuelAvailability(freshNoFuel, "AI-95", now)
        )
        assertEquals(
            FuelAvailabilityStatus.UNKNOWN,
            PriceReliabilityCalculator.calculateFuelAvailability(oldMark, "AI-95", now)
        )
    }

    @Test
    fun `search matches by name, brand, or address case insensitively`() {
        val stations = listOf(
            createStation(1, "Gazpromneft", "GPN", "Lenina 1", 55.0, 60.0, 50.0, 5),
            createStation(2, "Lukoil Center", "Lukoil", "Pobedy 10", 55.0, 60.0, 52.0, 3)
        )

        val byName = filterAndSorter.search(stations, "gazprom")
        assertEquals(1, byName.size)
        assertEquals(1, byName.first().id)

        val byBrand = filterAndSorter.search(stations, "LUKOIL")
        assertEquals(1, byBrand.size)
        assertEquals(2, byBrand.first().id)

        val byAddress = filterAndSorter.search(stations, "pobedy")
        assertEquals(1, byAddress.size)
        assertEquals(2, byAddress.first().id)
    }

    @Test
    fun `filterByCity filters by address substring`() {
        val stations = listOf(
            createStation(1, "Station 1", "B1", "Moscow, Tverskaya 1", 55.0, 37.0, 50.0, 1),
            createStation(2, "Station 2", "B2", "Chelyabinsk, Lenina 1", 55.0, 61.0, 50.0, 1)
        )

        val result = filterAndSorter.filterByCity(stations, "Chelyabinsk")
        assertEquals(1, result.size)
        assertEquals(2, result.first().id)
    }

    @Test
    fun `getCheapestStation returns cheapest station for given fuel type`() {
        val stations = listOf(
            createStation(1, "Expensive", "B1", "Addr", 55.0, 60.0, 60.0, 5),
            createStation(2, "Cheapest", "B2", "Addr", 55.0, 60.0, 50.0, 5)
        )

        val cheapest = filterAndSorter.getCheapestStation(stations, "AI-95")
        assertEquals(2, cheapest?.id)

        val nonExistent = filterAndSorter.getCheapestStation(stations, "DIESEL")
        assertNull(nonExistent)
    }

    @Test
    fun `sortPriceAscending and sortPriceDescending sort correctly`() {
        val stations = listOf(
            createStation(1, "Medium", "B1", "A", 55.0, 60.0, 55.0, 1),
            createStation(2, "Low", "B2", "A", 55.0, 60.0, 50.0, 1),
            createStation(3, "High", "B3", "A", 55.0, 60.0, 60.0, 1)
        )

        val asc = filterAndSorter.sortPriceAscending(stations, "AI-95")
        assertEquals(listOf(2, 1, 3), asc.map { it.id })

        val desc = filterAndSorter.sortPriceDescending(stations, "AI-95")
        assertEquals(listOf(3, 1, 2), desc.map { it.id })
    }

    @Test
    fun `sortByQueue sorts stations by queue time ascending`() {
        val stations = listOf(
            createStation(1, "S1", "B", "A", 55.0, 60.0, 50.0, 10),
            createStation(2, "S2", "B", "A", 55.0, 60.0, 50.0, 2),
            createStation(3, "S3", "B", "A", 55.0, 60.0, 50.0, 5)
        )

        val sorted = filterAndSorter.sortByQueue(stations, "AI-95")
        assertEquals(listOf(2, 3, 1), sorted.map { it.id })
    }

    @Test
    fun `filterOpen does NOT exclude UNKNOWN opening hours stations`() {
        val unknownHoursStation = createStation(10, "OSM Station", "Brand", "Addr", 55.0, 60.0, 0.0, 0, available = false, updatedAt = 0L)
            .copy(openingHours = null)
        val alwaysOpenStation = createStation(20, "Always Open", "Brand", "Addr", 55.0, 60.0, 50.0, 0, available = true, updatedAt = System.currentTimeMillis())
            .copy(openingHours = "24/7")

        val result = filterAndSorter.filterOpen(listOf(unknownHoursStation, alwaysOpenStation))
        assertEquals(2, result.size)
        assertTrue(result.any { it.id == 10 })
        assertTrue(result.any { it.id == 20 })
    }

    @Test
    fun `filterByBrands matches station by normalized brand OR name`() {
        val osmStationWithoutBrandTag = createStation(100, "Газпромнефть", "Газпромнефть", "Свердловский тракт 12в", 55.0, 60.0, 0.0, 0)
        val osmStationWithNameOnly = createStation(200, "АЗС Газпромнефть", "Прочие", "Addr", 55.0, 60.0, 0.0, 0)
        val otherStation = createStation(300, "Лукойл", "Лукойл", "Addr", 55.0, 60.0, 50.0, 0)

        val filtered = filterAndSorter.filterByBrands(
            listOf(osmStationWithoutBrandTag, osmStationWithNameOnly, otherStation),
            setOf("Газпромнефть")
        )

        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.id == 100 })
        assertTrue(filtered.any { it.id == 200 })
    }

    @Test
    fun `getStationsNearLocation filters by radius and sorts by distance`() {
        val centerLat = 55.1598
        val centerLon = 61.4026

        // Station 1: ~0.5 km away
        val s1 = createStation(1, "Close", "B", "A", 55.1600, 61.4100, 50.0, 1)
        // Station 2: ~10 km away
        val s2 = createStation(2, "Far", "B", "A", 55.2500, 61.4026, 50.0, 1)

        val nearby = filterAndSorter.getStationsNearLocation(centerLat, centerLon, radiusKm = 2.0, listOf(s1, s2))
        assertEquals(1, nearby.size)
        assertEquals(1, nearby.first().id)
    }
}
