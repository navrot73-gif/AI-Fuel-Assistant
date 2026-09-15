package com.navrot.aifuelassistant.ui.map.renderer

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.ui.theme.FueldeckColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StationMapMapperTest {

    private val mapper = StationMapMapper()

    @Test
    fun mapToMapItem_availableFuel_returnsMintColorAndMappedFields() {
        val station = GasStation(
            id = 101,
            name = "Газпромнефть №201",
            brand = "Газпромнефть",
            address = "Свердловский тракт, 12в",
            latitude = 55.2243443,
            longitude = 61.3747471,
            fuelTypes = listOf(
                FuelPrice(
                    type = "АИ-95",
                    price = 53.5,
                    available = true,
                    updatedAt = System.currentTimeMillis()
                )
            ),
            queueTime = 2,
            reliability = 90
        )

        val mapItem = mapper.mapToMapItem(
            station = station,
            selectedFuelTypes = setOf("АИ-95"),
            isSelected = true
        )

        assertEquals(101, mapItem.stationId)
        assertEquals(55.2243443, mapItem.latitude, 0.0000001)
        assertEquals(61.3747471, mapItem.longitude, 0.0000001)
        assertEquals("Газпромнефть №201", mapItem.title)
        assertTrue("Snippet must contain fuel type and formatted price", mapItem.snippet.contains("АИ-95") && mapItem.snippet.contains("54₽"))
        assertEquals(FueldeckColors.Mint, mapItem.markerColor)
        assertTrue(mapItem.isSelected)
    }

    @Test
    fun mapToMapItem_noFuel_returnsCoralColor() {
        val station = GasStation(
            id = 102,
            name = "Газпромнефть №201",
            brand = "Газпромнефть",
            address = "Свердловский тракт, 12в",
            latitude = 55.2243443,
            longitude = 61.3747471,
            fuelTypes = listOf(
                FuelPrice(
                    type = "АИ-95",
                    price = 0.0,
                    available = false,
                    source = FuelDataSource.RUSSIABASE,
                    updatedAt = System.currentTimeMillis()
                )
            ),
            queueTime = 0,
            reliability = 80,
            dataSources = setOf(FuelDataSource.RUSSIABASE)
        )

        val mapItem = mapper.mapToMapItem(
            station = station,
            selectedFuelTypes = setOf("АИ-95")
        )

        assertEquals(FueldeckColors.Coral, mapItem.markerColor)
        assertFalse(mapItem.isSelected)
    }

    @Test
    fun mapToMapItems_mapsEntireListAndSetsSelectedStationId() {
        val now = System.currentTimeMillis()
        val station1 = GasStation(
            id = 1,
            name = "Station 1",
            brand = "Brand",
            address = "Address 1",
            latitude = 55.1,
            longitude = 61.1,
            fuelTypes = listOf(FuelPrice("АИ-95", 50.0, true, updatedAt = now)),
            queueTime = 0,
            reliability = 80
        )
        val station2 = GasStation(
            id = 2,
            name = "Station 2",
            brand = "Brand",
            address = "Address 2",
            latitude = 55.2,
            longitude = 61.2,
            fuelTypes = listOf(FuelPrice("АИ-95", 51.0, true, updatedAt = now)),
            queueTime = 0,
            reliability = 80
        )

        val items = mapper.mapToMapItems(
            stations = listOf(station1, station2),
            selectedFuelTypes = setOf("АИ-95"),
            selectedStationId = 2
        )

        assertEquals(2, items.size)
        assertFalse(items[0].isSelected)
        assertTrue(items[1].isSelected)
        assertEquals(2, items[1].stationId)
    }
}
