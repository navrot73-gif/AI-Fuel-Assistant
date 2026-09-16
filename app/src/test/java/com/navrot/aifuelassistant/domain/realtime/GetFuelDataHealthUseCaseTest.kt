package com.navrot.aifuelassistant.domain.realtime

import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import org.junit.Assert.assertEquals
import org.junit.Test

class GetFuelDataHealthUseCaseTest {

    private val useCase = GetFuelDataHealthUseCase()
    private val now = System.currentTimeMillis()

    private fun createStation(id: Int, avail: Boolean, price: Double, timestamp: Long): GasStation {
        return GasStation(
            id = id,
            name = "Station $id",
            brand = "Brand",
            address = "Address $id",
            latitude = 55.15,
            longitude = 61.40,
            queueTime = 0,
            reliability = 80,
            updatedAt = timestamp,
            fuelTypes = listOf(
                FuelPrice(
                    type = "АИ-95",
                    price = price,
                    available = avail,
                    updatedAt = timestamp
                )
            )
        )
    }

    @Test
    fun testAllConfirmedAvailable() {
        val stations = listOf(
            createStation(1, true, 55.0, now - 5 * 60 * 1000L),
            createStation(2, true, 56.0, now - 5 * 60 * 1000L)
        )

        val health = useCase.execute(stations, "АИ-95", now)

        assertEquals(1.0, health.availabilityKnownCoverage, 0.001)
        assertEquals(1.0, health.availabilityConfirmedCoverage, 0.001)
        assertEquals(0.0, health.availabilityUnavailableCoverage, 0.001)
        assertEquals(0.0, health.availabilityUnknownCoverage, 0.001)
        assertEquals(FuelDataQualityLevel.HIGH, health.overallQuality)
    }

    @Test
    fun testAllUnknownAvailabilityWhenExpired() {
        // Timestamps expired (> 8h ago) -> status becomes UNKNOWN
        val stations = listOf(
            createStation(1, true, 55.0, now - 10 * 3600 * 1000L),
            createStation(2, true, 56.0, now - 10 * 3600 * 1000L)
        )

        val health = useCase.execute(stations, "АИ-95", now)

        assertEquals(0.0, health.availabilityKnownCoverage, 0.001)
        assertEquals(0.0, health.availabilityConfirmedCoverage, 0.001)
        assertEquals(0.0, health.availabilityUnavailableCoverage, 0.001)
        assertEquals(1.0, health.availabilityUnknownCoverage, 0.001)
        assertEquals(FuelDataQualityLevel.LOW, health.overallQuality)
    }

    @Test
    fun testMixedAvailabilityCoverage() {
        val stations = listOf(
            createStation(1, true, 55.0, now - 5 * 60 * 1000L),  // AVAILABLE
            createStation(2, false, 56.0, now - 5 * 60 * 1000L), // UNAVAILABLE
            createStation(3, true, 57.0, now - 10 * 3600 * 1000L), // UNKNOWN (expired)
            createStation(4, true, 58.0, now - 10 * 3600 * 1000L)  // UNKNOWN (expired)
        )

        val health = useCase.execute(stations, "АИ-95", now)

        assertEquals(0.50, health.availabilityKnownCoverage, 0.001)
        assertEquals(0.25, health.availabilityConfirmedCoverage, 0.001)
        assertEquals(0.25, health.availabilityUnavailableCoverage, 0.001)
        assertEquals(0.50, health.availabilityUnknownCoverage, 0.001)
    }
}
