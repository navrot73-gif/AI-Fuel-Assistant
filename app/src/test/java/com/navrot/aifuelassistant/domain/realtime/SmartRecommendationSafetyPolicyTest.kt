package com.navrot.aifuelassistant.domain.realtime

import com.navrot.aifuelassistant.domain.intelligence.FuelFreshness
import com.navrot.aifuelassistant.domain.recommendation.RecommendationConfidence
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartRecommendationSafetyPolicyTest {

    @Test
    fun testExplicitlyUnavailableStationIsExcluded() {
        val dq = FuelDataQuality(
            stationId = 1,
            fuelType = "АИ-95",
            availability = FuelAvailabilityStatus.UNAVAILABLE,
            price = 55.0,
            lastUpdated = System.currentTimeMillis(),
            ageMinutes = 5,
            freshness = FuelFreshness.VERY_FRESH,
            availabilityFreshness = FuelFreshness.VERY_FRESH,
            priceFreshness = FuelFreshness.VERY_FRESH,
            sourceCount = 1,
            reliableSourceCount = 1,
            agreement = true,
            conflict = false,
            sourceReliability = 0.9,
            completeness = 1.0,
            qualityLevel = FuelDataQualityLevel.HIGH,
            confidence = RecommendationConfidence.HIGH,
            primarySource = com.navrot.aifuelassistant.data.model.FuelDataSource.BENZONAVT,
            supportingSources = emptyList()
        )

        assertTrue(SmartRecommendationSafetyPolicy.isExcludedFromRecommendation(dq))
    }

    @Test
    fun testUnknownAvailabilityStationIsNotExcluded() {
        val dq = FuelDataQuality(
            stationId = 1,
            fuelType = "АИ-95",
            availability = FuelAvailabilityStatus.UNKNOWN,
            price = 55.0,
            lastUpdated = System.currentTimeMillis(),
            ageMinutes = 5,
            freshness = FuelFreshness.VERY_FRESH,
            availabilityFreshness = FuelFreshness.UNKNOWN,
            priceFreshness = FuelFreshness.VERY_FRESH,
            sourceCount = 1,
            reliableSourceCount = 1,
            agreement = true,
            conflict = false,
            sourceReliability = 0.9,
            completeness = 0.6,
            qualityLevel = FuelDataQualityLevel.MEDIUM,
            confidence = RecommendationConfidence.MEDIUM,
            primarySource = com.navrot.aifuelassistant.data.model.FuelDataSource.BENZONAVT,
            supportingSources = emptyList()
        )

        assertFalse(SmartRecommendationSafetyPolicy.isExcludedFromRecommendation(dq))
    }

    @Test
    fun testSafetyWarningGeneratedForStaleData() {
        val dq = FuelDataQuality(
            stationId = 1,
            fuelType = "АИ-95",
            availability = FuelAvailabilityStatus.AVAILABLE,
            price = 55.0,
            lastUpdated = System.currentTimeMillis() - 7 * 60 * 60 * 1000L,
            ageMinutes = 420,
            freshness = FuelFreshness.STALE,
            availabilityFreshness = FuelFreshness.STALE,
            priceFreshness = FuelFreshness.STALE,
            sourceCount = 1,
            reliableSourceCount = 1,
            agreement = true,
            conflict = false,
            sourceReliability = 0.9,
            completeness = 0.8,
            qualityLevel = FuelDataQualityLevel.LOW,
            confidence = RecommendationConfidence.LOW,
            primarySource = com.navrot.aifuelassistant.data.model.FuelDataSource.BENZONAVT,
            supportingSources = emptyList(),
            warnings = listOf("Данные по наличию устарели (>6 ч)")
        )

        val warnings = SmartRecommendationSafetyPolicy.generateSafetyWarnings(dq)
        assertTrue(warnings.any { it.contains("устаревших данных") })
    }
}
