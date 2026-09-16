package com.navrot.aifuelassistant.domain.realtime

data class FuelDataHealth(
    val evaluatedStationsCount: Int,
    val overallQuality: FuelDataQualityLevel,
    val availabilityCoverage: Double, // Deprecated or alias to availabilityKnownCoverage
    val availabilityKnownCoverage: Double,
    val availabilityConfirmedCoverage: Double,
    val availabilityUnavailableCoverage: Double,
    val availabilityUnknownCoverage: Double,
    val priceCoverage: Double,
    val freshCount: Int,
    val agingCount: Int,
    val staleCount: Int,
    val unknownCount: Int,
    val conflictCount: Int,
    val reliableSourceCount: Int
)
