package com.navrot.aifuelassistant.data.model

data class FuelReport(
    val totalRefuels: Int,
    val totalLiters: Double,
    val totalCost: Double,
    val totalDistanceKm: Double,
    val averageConsumptionPer100Km: Double,
    val averagePricePerLiter: Double,
    val costPerKm: Double,
    val periodStartEpochMillis: Long,
    val periodEndEpochMillis: Long
)
