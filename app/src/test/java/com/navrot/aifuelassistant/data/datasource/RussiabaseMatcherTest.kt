package com.navrot.aifuelassistant.data.datasource

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import com.navrot.aifuelassistant.domain.reliability.PriceReliabilityCalculator
import com.navrot.aifuelassistant.ui.map.filterStationsForPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RussiabaseMatcherTest {

    @Test
    fun matchesBrandAndAddress_sverdlovskyCase_returnsTrue() {
        val station = GasStation(
            id = 1,
            name = "Газпромнефть №42",
            brand = "Газпромнефть",
            address = "Челябинск, Свердловский тракт, 12в",
            latitude = 55.18,
            longitude = 61.35,
            fuelTypes = listOf(FuelPrice("АИ-95", 52.0, available = true)),
            queueTime = 0,
            reliability = 80
        )

        val obs = FuelObservation(
            brand = "Газпромнефть",
            address = "Свердловский тракт, 12В",
            fuelType = "АИ-95",
            price = 0.0,
            available = false,
            statusText = "Отсутствует"
        )

        val isMatch = RussiabaseMatcher.matchesBrandAndAddress(station, obs)
        assertTrue("Expected station on Свердловский тракт, 12в to match observation Свердловский тракт, 12В", isMatch)
    }

    @Test
    fun regression_sverdlovsky12v_matchesAndGivesRedPinStatus() {
        val station = GasStation(
            id = 148,
            name = "Газпромнефть",
            brand = "Газпромнефть",
            address = "Свердловский тракт, 12в",
            latitude = 55.18,
            longitude = 61.35,
            fuelTypes = listOf(FuelPrice("АИ-95", 53.5, available = true)),
            queueTime = 0,
            reliability = 80
        )

        val obs = FuelObservation(
            brand = "Газпромнефть",
            address = "Свердловский тракт, 12В",
            fuelType = "АИ-95",
            price = 0.0,
            available = false,
            statusText = "Отсутствует"
        )

        val updatedStations = RussiabaseMatcher.applyObservations(listOf(station), listOf(obs))
        val updatedStation = updatedStations.first()

        val availability = PriceReliabilityCalculator.calculateFuelAvailability(updatedStation, "АИ-95")
        assertEquals(FuelAvailabilityStatus.NO_FUEL, availability)

        val markerColor = com.navrot.aifuelassistant.ui.map.getMarkerColor(updatedStation, setOf("АИ-95"))
        assertEquals(com.navrot.aifuelassistant.ui.theme.FueldeckColors.Coral, markerColor)
    }

    @Test
    fun applyObservations_attachesNoFuelStatusAndDataSource() {
        val station = GasStation(
            id = 10,
            name = "Газпромнефть",
            brand = "Газпромнефть",
            address = "Челябинск, Свердловский тракт, 12в",
            latitude = 55.18,
            longitude = 61.35,
            fuelTypes = listOf(FuelPrice("АИ-95", 52.0, available = true)),
            queueTime = 0,
            reliability = 80,
            dataSources = setOf(FuelDataSource.BENZONAVT)
        )

        val obs = FuelObservation(
            brand = "Газпромнефть",
            address = "Свердловский 12в",
            fuelType = "АИ-95",
            price = 0.0,
            available = false,
            statusText = "Отсутствует"
        )

        val updatedStations = RussiabaseMatcher.applyObservations(listOf(station), listOf(obs))
        assertEquals(1, updatedStations.size)

        val updated = updatedStations[0]
        assertTrue(updated.dataSources.contains(FuelDataSource.RUSSIABASE))

        val fuel = updated.fuelTypes.find { it.type == "АИ-95" }!!
        assertFalse(fuel.available)
        assertEquals(FuelDataSource.RUSSIABASE, fuel.source)

        val availability = PriceReliabilityCalculator.calculateFuelAvailability(updated, "АИ-95")
        assertEquals(FuelAvailabilityStatus.NO_FUEL, availability)
    }

    @Test
    fun applyObservations_unmatchedObservation_doesNotCreateNewStation() {
        val station = GasStation(
            id = 1,
            name = "Лукойл",
            brand = "Лукойл",
            address = "пр. Ленина, 50",
            latitude = 55.16,
            longitude = 61.40,
            fuelTypes = listOf(FuelPrice("АИ-95", 55.0, true)),
            queueTime = 0,
            reliability = 80
        )

        val obs = FuelObservation(
            brand = "Неизвестная АЗС",
            address = "улица Совершенно Другая, 999",
            fuelType = "АИ-95",
            price = 50.0,
            available = true
        )

        val result = RussiabaseMatcher.applyObservations(listOf(station), listOf(obs))
        assertEquals(1, result.size)
        assertEquals(1, result[0].id)
        assertFalse(result[0].dataSources.contains(FuelDataSource.RUSSIABASE))
    }

    @Test
    fun redStation_excludedFromNearbyPanel_butKeptOnMap() {
        val redStation = GasStation(
            id = 1,
            name = "Газпромнефть №12",
            brand = "Газпромнефть",
            address = "Свердловский тракт, 12в",
            latitude = 55.18,
            longitude = 61.35,
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

        val greenStation = GasStation(
            id = 2,
            name = "Лукойл №5",
            brand = "Лукойл",
            address = "пр. Победы, 300",
            latitude = 55.19,
            longitude = 61.36,
            fuelTypes = listOf(
                FuelPrice(
                    type = "АИ-95",
                    price = 56.90,
                    available = true,
                    source = FuelDataSource.BENZONAVT,
                    updatedAt = System.currentTimeMillis()
                )
            ),
            queueTime = 0,
            reliability = 80,
            dataSources = setOf(FuelDataSource.BENZONAVT)
        )

        val allStationsOnMap = listOf(redStation, greenStation)
        assertEquals(2, allStationsOnMap.size)

        // Verify map red pin availability calculation
        val redAvailability = PriceReliabilityCalculator.calculateFuelAvailability(redStation, "АИ-95")
        assertEquals(FuelAvailabilityStatus.NO_FUEL, redAvailability)

        // Verify bottom sheet panel filtering (filter #146)
        val panelStations = filterStationsForPanel(allStationsOnMap, setOf("АИ-95"))
        assertEquals(1, panelStations.size)
        assertEquals(2, panelStations[0].id)
        assertFalse(panelStations.any { it.id == 1 })
    }
}
