package com.navrot.aifuelassistant.domain.predictive

/**
 * Extracted feature representation from vehicle fuel history and context.
 */
data class PersonalFuelFeatures(
    val vehicleId: Long,
    val fuelType: String,
    val timestamp: Long,
    val dayOfWeek: Int, // 1 (Mon) .. 7 (Sun)
    val hour: Int, // 0 .. 23
    val distanceSinceRefuel: Double?,
    val recentConsumption: Double?,
    val averageConsumption: Double?,
    val medianConsumption: Double?,
    val recentPrice: Double?,
    val averagePrice: Double?,
    val refuelIntervalKm: Double?,
    val refuelIntervalDays: Double?,
    val stationId: Int?,
    val routeDistance: Double? = null,
    val season: String? = null // "WINTER", "SPRING", "SUMMER", "AUTUMN"
)
