package com.navrot.aifuelassistant.domain.predictive

import com.navrot.aifuelassistant.domain.personal.PersonalConsumptionCalculator
import com.navrot.aifuelassistant.domain.personal.PersonalFuelEvent
import java.util.Calendar
import java.util.TimeZone

object PersonalFuelFeatureExtractor {

    fun extractFeatures(
        vehicleId: Long,
        fuelType: String,
        events: List<PersonalFuelEvent>,
        currentOdometerKm: Double? = null,
        routeDistanceKm: Double? = null,
        targetStationId: Int? = null,
        nowTimestamp: Long = System.currentTimeMillis()
    ): PersonalFuelFeatures {
        val vehicleEvents = events.filter { it.vehicleId == vehicleId }
            .sortedBy { it.timestamp }

        val cal = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = nowTimestamp
        }
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK).let { dow ->
            // Convert Calendar (Sunday=1, Monday=2) to ISO 1 (Mon) .. 7 (Sun)
            if (dow == Calendar.SUNDAY) 7 else dow - 1
        }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val month = cal.get(Calendar.MONTH) // 0..11
        val season = when (month) {
            Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> "WINTER"
            Calendar.MARCH, Calendar.APRIL, Calendar.MAY -> "SPRING"
            Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> "SUMMER"
            else -> "AUTUMN"
        }

        val consumptions = PersonalConsumptionCalculator.calculateConsumptions(vehicleEvents)
        val recentConsumption = consumptions.lastOrNull()
        val averageConsumption = if (consumptions.isNotEmpty()) consumptions.average() else null
        val medianConsumption = if (consumptions.isNotEmpty()) {
            val sorted = consumptions.sorted()
            val mid = sorted.size / 2
            if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
        } else null

        val prices = vehicleEvents.mapNotNull { it.pricePerLiter }.filter { it > 0 }
        val recentPrice = prices.lastOrNull()
        val averagePrice = if (prices.isNotEmpty()) prices.average() else null

        // Odometer distance since last refuel
        val lastEventWithOdo = vehicleEvents.lastOrNull { it.odometerKm != null && it.odometerKm > 0 }
        val distanceSinceRefuel = if (currentOdometerKm != null && lastEventWithOdo?.odometerKm != null) {
            (currentOdometerKm - lastEventWithOdo.odometerKm).coerceAtLeast(0.0)
        } else null

        // Average refuel intervals (km and days)
        val odoEvents = vehicleEvents.mapNotNull { it.odometerKm }.filter { it > 0 }
        val refuelIntervalKm = if (odoEvents.size >= 2) {
            val totalDistance = odoEvents.last() - odoEvents.first()
            val intervals = odoEvents.size - 1
            if (intervals > 0 && totalDistance > 0) totalDistance / intervals else null
        } else null

        val timestamps = vehicleEvents.map { it.timestamp }
        val refuelIntervalDays = if (timestamps.size >= 2) {
            val millisDiff = timestamps.last() - timestamps.first()
            val intervals = timestamps.size - 1
            val days = millisDiff / (1000.0 * 60.0 * 60.0 * 24.0)
            if (intervals > 0 && days > 0) days / intervals else null
        } else null

        return PersonalFuelFeatures(
            vehicleId = vehicleId,
            fuelType = fuelType,
            timestamp = nowTimestamp,
            dayOfWeek = dayOfWeek,
            hour = hour,
            distanceSinceRefuel = distanceSinceRefuel,
            recentConsumption = recentConsumption,
            averageConsumption = averageConsumption,
            medianConsumption = medianConsumption,
            recentPrice = recentPrice,
            averagePrice = averagePrice,
            refuelIntervalKm = refuelIntervalKm,
            refuelIntervalDays = refuelIntervalDays,
            stationId = targetStationId ?: vehicleEvents.lastOrNull()?.stationId,
            routeDistance = routeDistanceKm,
            season = season
        )
    }
}
