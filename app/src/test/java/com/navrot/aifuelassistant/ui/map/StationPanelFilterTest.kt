package com.navrot.aifuelassistant.ui.map

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StationPanelFilterTest {

    @Test
    fun filterStationsForPanel_includesAvailableWithPrice_excludesNoFuelUnknownAndZeroPrice() {
        val now = System.currentTimeMillis()

        // Station 1: AVAILABLE with price 55.0 for АИ-95
        val availableStation = GasStation(
            id = 1,
            name = "Available Station",
            brand = "BrandA",
            address = "Street 1",
            latitude = 55.0,
            longitude = 61.0,
            fuelTypes = listOf(
                FuelPrice(type = "АИ-95", price = 55.0, available = true, updatedAt = now)
            ),
            queueTime = 0,
            reliability = 90,
            dataSources = setOf(FuelDataSource.BENZONAVT),
            updatedAt = now
        )

        // Station 2: NO_FUEL for АИ-95
        val noFuelStation = GasStation(
            id = 2,
            name = "No Fuel Station",
            brand = "BrandB",
            address = "Street 2",
            latitude = 55.1,
            longitude = 61.1,
            fuelTypes = listOf(
                FuelPrice(type = "АИ-95", price = 54.0, available = false, updatedAt = now)
            ),
            queueTime = 0,
            reliability = 90,
            dataSources = setOf(FuelDataSource.BENZONAVT),
            updatedAt = now
        )

        // Station 3: UNKNOWN (updatedAt = 0)
        val unknownStation = GasStation(
            id = 3,
            name = "Unknown Station",
            brand = "BrandC",
            address = "Street 3",
            latitude = 55.2,
            longitude = 61.2,
            fuelTypes = listOf(
                FuelPrice(type = "АИ-95", price = 53.0, available = true, updatedAt = 0L)
            ),
            queueTime = 0,
            reliability = 40,
            dataSources = setOf(FuelDataSource.OVERPASS),
            updatedAt = 0L
        )

        // Station 4: Price 0.0
        val zeroPriceStation = GasStation(
            id = 4,
            name = "Zero Price Station",
            brand = "BrandD",
            address = "Street 4",
            latitude = 55.3,
            longitude = 61.3,
            fuelTypes = listOf(
                FuelPrice(type = "АИ-95", price = 0.0, available = true, updatedAt = now)
            ),
            queueTime = 0,
            reliability = 50,
            dataSources = setOf(FuelDataSource.OVERPASS),
            updatedAt = now
        )

        val stations = listOf(availableStation, noFuelStation, unknownStation, zeroPriceStation)
        val filtered = filterStationsForPanel(stations, setOf("АИ-95"))

        assertEquals(1, filtered.size)
        assertEquals(1, filtered[0].id)
        assertTrue(filtered.contains(availableStation))
        assertFalse(filtered.contains(noFuelStation))
        assertFalse(filtered.contains(unknownStation))
        assertFalse(filtered.contains(zeroPriceStation))
    }
}
