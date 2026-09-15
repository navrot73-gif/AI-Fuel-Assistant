package com.navrot.aifuelassistant.domain.predictive

/**
 * Tracks historical user interaction with specific gas stations.
 * Distinguishes between objective quality and personal preference.
 */
data class PersonalStationLearning(
    val stationId: Int,
    val visitsCount: Int = 0,
    val refuelsCount: Int = 0,
    val chosenAfterRecommendationCount: Int = 0,
    val repeatVisitsCount: Int = 0,
    val averagePaidPrice: Double? = null,
    val priceDifferenceFromAverage: Double? = null,
    val personalPreferenceScore: Double = 0.0 // Layered over objectiveScore, does not alter objective score itself
)
