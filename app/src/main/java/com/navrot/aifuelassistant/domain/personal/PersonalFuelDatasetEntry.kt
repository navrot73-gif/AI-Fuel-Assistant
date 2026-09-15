package com.navrot.aifuelassistant.domain.personal

data class PersonalFuelDatasetEntry(
    val vehicleId: Long,
    val timestamp: Long,
    val distanceKm: Double?,
    val fuelType: String,
    val liters: Double?,
    val pricePerLiter: Double?,
    val consumptionL100km: Double?,
    val stationId: Int?,
    val hourOfDay: Int,
    val dayOfWeek: Int
)
