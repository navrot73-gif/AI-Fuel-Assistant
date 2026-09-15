package com.navrot.aifuelassistant.domain.personal

import java.util.concurrent.TimeUnit

class CalculatePersonalFuelStatisticsUseCase {

    fun execute(
        vehicleId: Long,
        events: List<PersonalFuelEvent>,
        period: Period,
        nowMillis: Long = System.currentTimeMillis()
    ): PersonalFuelStatistics {
        val vehicleEvents = events.filter { it.vehicleId == vehicleId }
        val periodEvents = if (period.days != null) {
            val cutoff = nowMillis - TimeUnit.DAYS.toMillis(period.days.toLong())
            vehicleEvents.filter { it.timestamp >= cutoff }
        } else {
            vehicleEvents
        }

        if (periodEvents.isEmpty()) {
            return PersonalFuelStatistics(vehicleId = vehicleId, period = period)
        }

        val refuelCount = periodEvents.size
        val totalLitersSum = periodEvents.mapNotNull { it.liters }.filter { it > 0 }.sum()
        val totalLiters = if (totalLitersSum > 0) totalLitersSum else null

        val totalCostSum = periodEvents.mapNotNull { it.totalCost }.filter { it > 0 }.sum()
        val totalCost = if (totalCostSum > 0) totalCostSum else null

        val validPrices = periodEvents.mapNotNull { it.pricePerLiter }.filter { it > 0 }
        val avgPricePerLiter = if (validPrices.isNotEmpty()) validPrices.average() else null

        val odometers = periodEvents.mapNotNull { it.odometerKm }.filter { it > 0 }.sorted()
        val totalDistanceKm = if (odometers.size >= 2) {
            odometers.last() - odometers.first()
        } else null

        val consumptions = PersonalConsumptionCalculator.calculateConsumptions(periodEvents)

        val averageConsumption = if (consumptions.isNotEmpty()) consumptions.average() else null
        val minConsumption = if (consumptions.isNotEmpty()) consumptions.minOrNull() else null
        val maxConsumption = if (consumptions.isNotEmpty()) consumptions.maxOrNull() else null
        val medianConsumption = if (consumptions.isNotEmpty()) {
            val sorted = consumptions.sorted()
            if (sorted.size % 2 == 1) {
                sorted[sorted.size / 2]
            } else {
                (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
            }
        } else null

        return PersonalFuelStatistics(
            vehicleId = vehicleId,
            period = period,
            averageConsumption = averageConsumption,
            medianConsumption = medianConsumption,
            minConsumption = minConsumption,
            maxConsumption = maxConsumption,
            totalLiters = totalLiters,
            totalFuelCost = totalCost,
            totalDistanceKm = if (totalDistanceKm != null && totalDistanceKm > 0) totalDistanceKm else null,
            averagePricePerLiter = avgPricePerLiter,
            refuelCount = refuelCount
        )
    }
}
