package com.navrot.aifuelassistant.domain.predictive

data class PersonalStationLearning(
    val stationId: Int,
    val visitsCount: Int = 0,
    val refuelsCount: Int = 0,
    val chosenAfterRecommendationCount: Int = 0,
    val repeatVisitsCount: Int = 0,
    val averagePaidPrice: Double? = null,
    val priceDifferenceFromAverage: Double? = null,
    val personalPreferenceScore: Double = 0.0,
    val successfulRefuelCount: Int = 0,
    val failedRefuelCount: Int = 0,
    val lastVisitedAt: Long? = null
)
