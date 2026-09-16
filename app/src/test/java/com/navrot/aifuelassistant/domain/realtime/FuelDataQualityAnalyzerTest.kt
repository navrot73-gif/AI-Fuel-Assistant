package com.navrot.aifuelassistant.domain.realtime

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.domain.intelligence.FuelFreshness
import com.navrot.aifuelassistant.domain.intelligence.FuelSourceObservation
import com.navrot.aifuelassistant.domain.intelligence.StationFuelSnapshot
import com.navrot.aifuelassistant.domain.recommendation.RecommendationConfidence
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelDataQualityAnalyzerTest {

    private val now = System.currentTimeMillis()

    @Test
    fun testVeryFreshSingleReliableSourceHighQuality() {
        val obs = listOf(
            FuelSourceObservation(
                stationId = 1,
                fuelType = "АИ-95",
                availability = FuelAvailabilityStatus.AVAILABLE,
                price = 55.0,
                observedAt = now - 5 * 60 * 1000L, // 5 min ago
                source = FuelDataSource.BENZONAVT
            )
        )
        val snapshot = StationFuelSnapshot(
            stationId = 1,
            fuelType = "АИ-95",
            availability = FuelAvailabilityStatus.AVAILABLE,
            price = 55.0,
            freshness = FuelFreshness.VERY_FRESH,
            confidence = RecommendationConfidence.HIGH,
            sourceCount = 1,
            confirmingSourceCount = 1,
            conflictingSourceCount = 0,
            isConflict = false,
            conflictReason = null,
            lastUpdatedAt = now - 5 * 60 * 1000L,
            sources = obs
        )

        val quality = FuelDataQualityAnalyzer.analyze(snapshot, obs, now)

        assertEquals(FuelDataQualityLevel.HIGH, quality.qualityLevel)
        assertEquals(FuelFreshness.VERY_FRESH, quality.freshness)
        assertFalse(quality.conflict)
        assertEquals(55.0, quality.price!!, 0.01)
        assertEquals(1, quality.sourceCount)
        assertEquals(0, quality.conflicts.size)
        assertTrue(quality.warnings.isEmpty())
    }

    @Test
    fun testAvailabilityConflictTriggersLowOrMediumQualityAndWarnings() {
        val obs = listOf(
            FuelSourceObservation(
                stationId = 1,
                fuelType = "АИ-95",
                availability = FuelAvailabilityStatus.AVAILABLE,
                price = 55.0,
                observedAt = now - 10 * 60 * 1000L,
                source = FuelDataSource.BENZONAVT
            ),
            FuelSourceObservation(
                stationId = 1,
                fuelType = "АИ-95",
                availability = FuelAvailabilityStatus.UNAVAILABLE,
                price = 55.0,
                observedAt = now - 12 * 60 * 1000L,
                source = FuelDataSource.RUSSIABASE
            )
        )
        val snapshot = StationFuelSnapshot(
            stationId = 1,
            fuelType = "АИ-95",
            availability = FuelAvailabilityStatus.AVAILABLE,
            price = 55.0,
            freshness = FuelFreshness.VERY_FRESH,
            confidence = RecommendationConfidence.MEDIUM,
            sourceCount = 2,
            confirmingSourceCount = 1,
            conflictingSourceCount = 1,
            isConflict = true,
            conflictReason = "Источники расходятся",
            lastUpdatedAt = now - 10 * 60 * 1000L,
            sources = obs
        )

        val quality = FuelDataQualityAnalyzer.analyze(snapshot, obs, now)

        assertTrue(quality.conflict)
        assertEquals(FuelDataQualityLevel.LOW, quality.qualityLevel)
        assertTrue(quality.warnings.any { it.contains("расходятся") })
    }

    @Test
    fun testPriceConflictDetectedWhenPricesDiverge() {
        val obs = listOf(
            FuelSourceObservation(
                stationId = 1,
                fuelType = "АИ-95",
                availability = FuelAvailabilityStatus.AVAILABLE,
                price = 50.0,
                observedAt = now - 5 * 60 * 1000L,
                source = FuelDataSource.BENZONAVT
            ),
            FuelSourceObservation(
                stationId = 1,
                fuelType = "АИ-95",
                availability = FuelAvailabilityStatus.AVAILABLE,
                price = 60.0, // 20% diff > 2% tolerance
                observedAt = now - 5 * 60 * 1000L,
                source = FuelDataSource.RUSSIABASE
            )
        )
        val snapshot = StationFuelSnapshot(
            stationId = 1,
            fuelType = "АИ-95",
            availability = FuelAvailabilityStatus.AVAILABLE,
            price = 50.0,
            freshness = FuelFreshness.VERY_FRESH,
            confidence = RecommendationConfidence.HIGH,
            sourceCount = 2,
            confirmingSourceCount = 2,
            conflictingSourceCount = 0,
            isConflict = false,
            conflictReason = null,
            lastUpdatedAt = now - 5 * 60 * 1000L,
            sources = obs
        )

        val quality = FuelDataQualityAnalyzer.analyze(snapshot, obs, now)

        assertTrue(quality.conflict)
        assertTrue(quality.conflicts.any { it.conflictType == ConflictType.PRICE_CONFLICT })
    }

    @Test
    fun testUnknownAvailabilityAndMissingPriceYieldsUnknownQuality() {
        val snapshot = StationFuelSnapshot(
            stationId = 1,
            fuelType = "АИ-95",
            availability = FuelAvailabilityStatus.UNKNOWN,
            price = null,
            freshness = FuelFreshness.UNKNOWN,
            confidence = RecommendationConfidence.UNKNOWN,
            sourceCount = 0,
            confirmingSourceCount = 0,
            conflictingSourceCount = 0,
            isConflict = false,
            conflictReason = null,
            lastUpdatedAt = null,
            sources = emptyList()
        )

        val quality = FuelDataQualityAnalyzer.analyze(snapshot, emptyList(), now)

        assertEquals(FuelDataQualityLevel.UNKNOWN, quality.qualityLevel)
        assertEquals(FuelAvailabilityStatus.UNKNOWN, quality.availability)
        assertNull(quality.price)
        assertNull(quality.lastUpdated)
        assertNull(quality.ageMinutes)
    }
}
