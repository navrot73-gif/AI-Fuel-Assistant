package com.navrot.aifuelassistant.domain.personal

/**
 * Normalized domain event for vehicle refuels and fuel usage.
 */
data class PersonalFuelEvent(
    val id: Long = 0L,
    val vehicleId: Long,
    val timestamp: Long,
    val stationId: Int? = null,
    val fuelType: String,
    val liters: Double? = null,
    val pricePerLiter: Double? = null,
    val totalCost: Double? = null,
    val odometerKm: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val source: String = "USER_LOG",
    val distanceSincePreviousRefuelKm: Double? = null,
    val fullTank: Boolean = false,
    val notes: String? = null
)
