package com.navrot.aifuelassistant.domain.personal

enum class Period(val days: Int?) {
    SEVEN_DAYS(7),
    THIRTY_DAYS(30),
    NINETY_DAYS(90),
    ALL_TIME(null)
}

data class PersonalFuelStatistics(
    val vehicleId: Long,
    val period: Period,
    val averageConsumption: Double? = null,
    val medianConsumption: Double? = null,
    val minConsumption: Double? = null,
    val maxConsumption: Double? = null,
    val totalLiters: Double? = null,
    val totalFuelCost: Double? = null,
    val totalDistanceKm: Double? = null,
    val averagePricePerLiter: Double? = null,
    val refuelCount: Int = 0
)
