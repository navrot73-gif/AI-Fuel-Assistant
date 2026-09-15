package com.navrot.aifuelassistant.domain.personal

import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.domain.recommendation.RecommendationConfidence
import com.navrot.aifuelassistant.domain.recommendation.StationRecommendation
import com.navrot.aifuelassistant.domain.recommendation.StationRecommendationReason
import org.junit.Assert.*
import org.junit.Test

class PersonalUseCasesTest {

    @Test
    fun `CalculatePersonalFuelStatisticsUseCase computes statistics correctly`() {
        val useCase = CalculatePersonalFuelStatisticsUseCase()
        val events = listOf(
            PersonalFuelEvent(
                id = 1L,
                vehicleId = 1L,
                timestamp = 1000L,
                fuelType = "AI-95",
                liters = 40.0,
                pricePerLiter = 60.0,
                totalCost = 2400.0,
                odometerKm = 10000.0,
                fullTank = true
            ),
            PersonalFuelEvent(
                id = 2L,
                vehicleId = 1L,
                timestamp = 2000L,
                fuelType = "AI-95",
                liters = 40.0,
                pricePerLiter = 60.0,
                totalCost = 2400.0,
                odometerKm = 10500.0, // 500 km -> 8.0 L/100km
                fullTank = true
            )
        )

        val stats = useCase.execute(1L, events, Period.ALL_TIME, nowMillis = 3000L)
        assertEquals(2, stats.refuelCount)
        assertEquals(80.0, stats.totalLiters!!, 0.001)
        assertEquals(4800.0, stats.totalFuelCost!!, 0.001)
        assertEquals(500.0, stats.totalDistanceKm!!, 0.001)
        assertEquals(8.0, stats.averageConsumption!!, 0.001)
    }

    @Test
    fun `CalculatePersonalStationProfileUseCase accumulates visits and preference score`() {
        val useCase = CalculatePersonalStationProfileUseCase()
        val events = listOf(
            PersonalFuelEvent(
                id = 1L,
                vehicleId = 1L,
                timestamp = 1000L,
                stationId = 101,
                fuelType = "AI-95",
                liters = 30.0,
                totalCost = 1800.0,
                pricePerLiter = 60.0
            ),
            PersonalFuelEvent(
                id = 2L,
                vehicleId = 1L,
                timestamp = 2000L,
                stationId = 101,
                fuelType = "AI-95",
                liters = 30.0,
                totalCost = 1800.0,
                pricePerLiter = 60.0
            )
        )

        val profile = useCase.execute(101, events)
        assertEquals(101, profile.stationId)
        assertEquals(2, profile.visitCount)
        assertEquals(2, profile.refuelCount)
        assertEquals(60.0, profile.totalLiters, 0.001)
        assertEquals(3600.0, profile.totalSpent, 0.001)
        assertTrue(profile.userPreferenceScore > 0.0)
    }

    @Test
    fun `FuelAnomalyDetector triggers when current consumption exceeds baseline threshold`() {
        val detector = FuelAnomalyDetector(thresholdPercentage = 15.0)
        val baseline = PersonalFuelBaseline(vehicleId = 1L, normalConsumption = 8.0)

        val normalResult = detector.checkAnomaly(8.5, baseline)
        assertFalse(normalResult.isAnomaly)

        val anomalyResult = detector.checkAnomaly(10.0, baseline) // +25%
        assertTrue(anomalyResult.isAnomaly)
        assertNotNull(anomalyResult.message)
    }

    @Test
    fun `PersonalPreferenceAdjustment preserves objective base recommendation while layering user history`() {
        val adjustment = PersonalPreferenceAdjustment()
        val dummyStation = GasStation(
            id = 1,
            name = "Test Station",
            brand = "Test",
            address = "Test St",
            latitude = 55.0,
            longitude = 61.0,
            fuelTypes = emptyList(),
            queueTime = 0,
            reliability = 100
        )
        val baseRec = StationRecommendation(
            station = dummyStation,
            score = 80.0,
            estimatedTotalCost = 2400.0,
            reasons = listOf(StationRecommendationReason.LOW_PRICE),
            confidence = RecommendationConfidence.HIGH
        )

        val events = listOf(
            PersonalFuelEvent(
                id = 1L, vehicleId = 1L, timestamp = 1000L, stationId = 1, fuelType = "AI-95"
            )
        )

        val personalizedList = adjustment.personalizeRecommendations(listOf(baseRec), events)
        assertEquals(1, personalizedList.size)
        val pRec = personalizedList.first()
        assertEquals(baseRec.station.id, pRec.baseRecommendation.station.id)
        assertTrue(pRec.isPersonalized)
        assertTrue(pRec.personalizedReasons.any { it.contains("заправлялись здесь 1 раз") })
    }
}
