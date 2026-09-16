package com.navrot.aifuelassistant.domain.personal

data class PersonalStationProfile(
    val stationId: Int,
    val visitCount: Int = 0,
    val refuelCount: Int = 0,
    val totalLiters: Double = 0.0,
    val totalSpent: Double = 0.0,
    val averagePaidPrice: Double? = null,
    val averageObservedPrice: Double? = averagePaidPrice,
    val lastVisitAt: Long? = null,
    val averagePriceDeltaVsCity: Double? = null,
    val userPreferenceScore: Double = 0.0,
    val successfulRefuelCount: Int = refuelCount,
    val failedRefuelCount: Int = 0
)
