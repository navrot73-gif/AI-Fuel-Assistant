package com.navrot.aifuelassistant.domain.realtime

import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.domain.intelligence.FuelFreshness
import com.navrot.aifuelassistant.domain.intelligence.FuelIntelligenceResolver
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import javax.inject.Inject

class GetFuelDataHealthUseCase @Inject constructor() {

    fun execute(
        stations: List<GasStation>,
        fuelType: String,
        currentTimeMs: Long = System.currentTimeMillis()
    ): FuelDataHealth {
        val total = stations.size
        if (total == 0) {
            return FuelDataHealth(
                evaluatedStationsCount = 0,
                overallQuality = FuelDataQualityLevel.UNKNOWN,
                availabilityCoverage = 0.0,
                priceCoverage = 0.0,
                freshCount = 0,
                agingCount = 0,
                staleCount = 0,
                unknownCount = 0,
                conflictCount = 0,
                reliableSourceCount = 0
            )
        }

        var availableCount = 0
        var priceCount = 0
        var freshCount = 0
        var agingCount = 0
        var staleCount = 0
        var unknownCount = 0
        var conflictCount = 0
        var reliableSourceCount = 0

        for (station in stations) {
            val snapshot = FuelIntelligenceResolver.resolveSnapshot(station, fuelType, currentTimeMs)
            val quality = FuelDataQualityAnalyzer.analyze(snapshot, now = currentTimeMs)

            if (quality.availability != FuelAvailabilityStatus.UNKNOWN) {
                availableCount++
            }
            if (quality.price != null && quality.price > 0.0) {
                priceCount++
            }
            when (quality.freshness) {
                FuelFreshness.VERY_FRESH, FuelFreshness.FRESH -> freshCount++
                FuelFreshness.AGING -> agingCount++
                FuelFreshness.STALE -> staleCount++
                FuelFreshness.UNKNOWN -> unknownCount++
            }
            if (quality.conflict) {
                conflictCount++
            }
            if (quality.reliableSourceCount > 0) {
                reliableSourceCount++
            }
        }

        val availabilityCoverage = availableCount.toDouble() / total
        val priceCoverage = priceCount.toDouble() / total

        val freshRatio = freshCount.toDouble() / total
        val conflictRatio = conflictCount.toDouble() / total

        val overallQuality = when {
            freshRatio >= 0.70 && conflictRatio <= 0.05 && availabilityCoverage >= 0.80 -> FuelDataQualityLevel.HIGH
            freshRatio >= 0.40 && conflictRatio <= 0.15 && availabilityCoverage >= 0.50 -> FuelDataQualityLevel.MEDIUM
            total > 0 && freshCount == 0 && unknownCount == total -> FuelDataQualityLevel.UNKNOWN
            else -> FuelDataQualityLevel.LOW
        }

        return FuelDataHealth(
            evaluatedStationsCount = total,
            overallQuality = overallQuality,
            availabilityCoverage = availabilityCoverage,
            priceCoverage = priceCoverage,
            freshCount = freshCount,
            agingCount = agingCount,
            staleCount = staleCount,
            unknownCount = unknownCount,
            conflictCount = conflictCount,
            reliableSourceCount = reliableSourceCount
        )
    }
}
