package com.navrot.aifuelassistant.domain.personal

data class PersonalFuelBaseline(
    val vehicleId: Long,
    val normalConsumption: Double? = null,
    val normalPricePerLiter: Double? = null,
    val normalRefuelIntervalKm: Double? = null,
    val normalRefuelIntervalDays: Double? = null
)
