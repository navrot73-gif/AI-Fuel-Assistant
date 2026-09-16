package com.navrot.aifuelassistant.domain.smart

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.domain.personal.PersonalFuelEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GetSmartFuelRecommendationUseCaseTest {

    private val useCase = GetSmartFuelRecommendationUseCase()

    private val now = System.currentTimeMillis()

    private val station1 = GasStation(
        id = 1,
        name = "Газпромнефть №1",
        brand = "Газпромнефть",
        address = "Свердловский тракт 12В",
        latitude = 55.2243,
        longitude = 61.3747,
        fuelTypes = listOf(FuelPrice("АИ-95", 52.0, true, source = FuelDataSource.RUSSIABASE, updatedAt = now)),
        reliability = 95,
        queueTime = 2,
        dataSources = setOf(FuelDataSource.RUSSIABASE)
    )

    private val station2 = GasStation(
        id = 2,
        name = "Лукойл №2",
        brand = "Лукойл",
        address = "пр. Ленина 40",
        latitude = 55.1600,
        longitude = 61.4000,
        fuelTypes = listOf(FuelPrice("АИ-95", 54.0, true, source = FuelDataSource.RUSSIABASE, updatedAt = now)),
        reliability = 90,
        queueTime = 0,
        dataSources = setOf(FuelDataSource.RUSSIABASE)
    )

    private val stationUnavailable = GasStation(
        id = 3,
        name = "Башнефть №3",
        brand = "Башнефть",
        address = "ул. Труда 100",
        latitude = 55.1700,
        longitude = 61.3500,
        fuelTypes = listOf(FuelPrice("АИ-95", 50.0, false, source = FuelDataSource.RUSSIABASE, updatedAt = now)),
        reliability = 80,
        queueTime = 1,
        dataSources = setOf(FuelDataSource.RUSSIABASE)
    )

    @Test
    fun testExecute_returnsTopAndAlternatives_excludingUnavailable() {
        val result = useCase.execute(
            stations = listOf(station1, station2, stationUnavailable),
            fuelType = "АИ-95",
            userLat = 55.2200,
            userLon = 61.3700,
            currentTimeMs = now
        )

        assertNotNull(result.topRecommendation)
        assertEquals(1, result.topRecommendation!!.stationId)
        assertEquals(true, result.topRecommendation!!.recommended)
        assertEquals(1, result.alternatives.size)
        assertEquals(2, result.alternatives.first().stationId)
    }

    @Test
    fun testExecute_incorporatesPersonalPreference() {
        val personalEvents = listOf(
            PersonalFuelEvent(id = 10, vehicleId = 1, stationId = 2, fuelType = "АИ-95", pricePerLiter = 54.0, liters = 30.0, timestamp = now - 100000),
            PersonalFuelEvent(id = 11, vehicleId = 1, stationId = 2, fuelType = "АИ-95", pricePerLiter = 54.0, liters = 30.0, timestamp = now - 50000)
        )

        val result = useCase.execute(
            stations = listOf(station1, station2),
            fuelType = "АИ-95",
            userLat = 55.2200,
            userLon = 61.3700,
            vehicleId = 1,
            personalEvents = personalEvents,
            currentTimeMs = now
        )

        assertNotNull(result.topRecommendation)
        val rec2 = if (result.topRecommendation!!.stationId == 2) result.topRecommendation else result.alternatives.firstOrNull { it.stationId == 2 }
        assertNotNull(rec2)
        assertEquals(2, rec2!!.personalVisitCount)
        assertTrue(rec2.reasons.contains(SmartRecommendationReason.PERSONAL_PREFERENCE))
    }

    @Test
    fun testExecute_returnsEmptyWhenNoMatchingFuelOrEmptyStations() {
        val resultNoFuel = useCase.execute(
            stations = listOf(station1, station2),
            fuelType = "ДТ",
            currentTimeMs = now
        )
        assertNull(resultNoFuel.topRecommendation)
        assertTrue(resultNoFuel.alternatives.isEmpty())

        val resultEmpty = useCase.execute(
            stations = emptyList(),
            fuelType = "АИ-95",
            currentTimeMs = now
        )
        assertNull(resultEmpty.topRecommendation)
        assertTrue(resultEmpty.alternatives.isEmpty())
    }
}
