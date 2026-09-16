package com.navrot.aifuelassistant.domain.smart

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.domain.intelligence.FuelFreshness
import com.navrot.aifuelassistant.domain.intelligence.StationFuelSnapshot
import com.navrot.aifuelassistant.domain.recommendation.RecommendationConfidence
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartRecommendationPolicyTest {

    private val sampleStation = GasStation(
        id = 1,
        name = "Газпромнефть",
        brand = "Газпромнефть",
        address = "Свердловский тракт 12В",
        latitude = 55.2243,
        longitude = 61.3747,
        fuelTypes = listOf(FuelPrice("АИ-95", 55.0, true, source = FuelDataSource.RUSSIABASE, updatedAt = System.currentTimeMillis())),
        reliability = 90,
        queueTime = 2,
        dataSources = setOf(FuelDataSource.RUSSIABASE)
    )

    @Test
    fun testHardFilter_excludesUnavailableAndNoFuel() {
        val unavailableSnapshot = StationFuelSnapshot(
            stationId = 1,
            fuelType = "АИ-95",
            availability = FuelAvailabilityStatus.UNAVAILABLE,
            price = 55.0,
            freshness = FuelFreshness.VERY_FRESH,
            confidence = RecommendationConfidence.HIGH,
            sourceCount = 1,
            confirmingSourceCount = 1,
            conflictingSourceCount = 0,
            isConflict = false,
            conflictReason = null,
            lastUpdatedAt = System.currentTimeMillis(),
            sources = emptyList()
        )

        val noFuelSnapshot = unavailableSnapshot.copy(availability = FuelAvailabilityStatus.NO_FUEL)
        val availableSnapshot = unavailableSnapshot.copy(availability = FuelAvailabilityStatus.AVAILABLE)
        val unknownSnapshot = unavailableSnapshot.copy(availability = FuelAvailabilityStatus.UNKNOWN)

        assertTrue(SmartRecommendationPolicy.isHardFilteredOut(unavailableSnapshot))
        assertTrue(SmartRecommendationPolicy.isHardFilteredOut(noFuelSnapshot))
        assertFalse(SmartRecommendationPolicy.isHardFilteredOut(availableSnapshot))
        assertFalse(SmartRecommendationPolicy.isHardFilteredOut(unknownSnapshot))
    }

    @Test
    fun testCombinedScore_reducesScoreForHighPersonalPreference() {
        val objScore = 100.0
        val lowPersonal = 0.0
        val highPersonal = 5.0

        val scoreDefault = SmartRecommendationPolicy.calculateCombinedScore(objScore, lowPersonal)
        val scorePersonal = SmartRecommendationPolicy.calculateCombinedScore(objScore, highPersonal)

        assertEquals(100.0, scoreDefault, 0.001)
        assertTrue("High personal score must lower (improve) the combined score", scorePersonal < scoreDefault)
        assertEquals(90.0, scorePersonal, 0.001)
    }

    @Test
    fun testTripCostCalculation_returnsNullWhenParametersMissing() {
        assertNull(SmartRecommendationPolicy.calculateTripCost(null, 55.0, 8.0))
        assertNull(SmartRecommendationPolicy.calculateTripCost(10.0, null, 8.0))
        assertNull(SmartRecommendationPolicy.calculateTripCost(10.0, 55.0, null))
        assertNull(SmartRecommendationPolicy.calculateTripCost(0.0, 55.0, 8.0))

        val cost = SmartRecommendationPolicy.calculateTripCost(10.0, 55.0, 8.0)
        assertNotNull(cost)
        // 10 km * 8 L / 100 km = 0.8 L * 55 RUB/L = 44 RUB
        assertEquals(44.0, cost!!, 0.001)
    }

    @Test
    fun testDeterministicTieBreak() {
        val rec1 = SmartStationRecommendation(
            station = sampleStation,
            stationId = 1,
            stationName = "АЗС 1",
            address = "Тест 1",
            fuelType = "АИ-95",
            availability = FuelAvailabilityStatus.AVAILABLE,
            availabilityFreshness = FuelFreshness.VERY_FRESH,
            price = 55.0,
            priceFreshness = FuelFreshness.VERY_FRESH,
            distanceKm = 2.0,
            estimatedTravelMinutes = 4,
            queueTimeMinutes = 2,
            objectiveScore = 50.0,
            personalScore = 0.0,
            estimatedFuelCost = 1650.0,
            estimatedTripCost = 10.0,
            estimatedTotalCost = 1660.0,
            confidence = RecommendationConfidence.HIGH,
            reasons = listOf(SmartRecommendationReason.CHEAPER),
            personalVisitCount = null,
            recommended = true
        )

        val rec2 = rec1.copy(stationId = 2, stationName = "АЗС 2")

        // Identical recs compare by stationId ascending
        val tieComp = SmartRecommendationPolicy.compareRecommendations(rec1, rec2)
        assertTrue(tieComp < 0)

        // Higher confidence wins regardless of stationId
        val rec2HighConf = rec2.copy(confidence = RecommendationConfidence.HIGH)
        val rec1LowConf = rec1.copy(confidence = RecommendationConfidence.LOW)
        val confComp = SmartRecommendationPolicy.compareRecommendations(rec1LowConf, rec2HighConf)
        assertTrue(confComp > 0)
    }
}
