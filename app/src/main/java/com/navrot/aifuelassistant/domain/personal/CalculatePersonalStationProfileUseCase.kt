package com.navrot.aifuelassistant.domain.personal

class CalculatePersonalStationProfileUseCase {

    fun execute(
        stationId: Int,
        events: List<PersonalFuelEvent>
    ): PersonalStationProfile {
        val stationEvents = events.filter { it.stationId == stationId }
        if (stationEvents.isEmpty()) {
            return PersonalStationProfile(stationId = stationId)
        }

        val visitCount = stationEvents.size
        val refuelsWithLiters = stationEvents.filter { (it.liters ?: 0.0) > 0.0 }
        val refuelCount = refuelsWithLiters.size
        val totalLiters = refuelsWithLiters.sumOf { it.liters ?: 0.0 }
        val totalSpent = stationEvents.sumOf { it.totalCost ?: 0.0 }

        val paidPrices = stationEvents.mapNotNull { it.pricePerLiter }.filter { it > 0.0 }
        val averagePaidPrice = if (paidPrices.isNotEmpty()) paidPrices.average() else null

        val lastVisitAt = stationEvents.maxOfOrNull { it.timestamp }

        // User preference score based on repeated visits (deterministic log scale or frequency boost)
        val userPreferenceScore = when {
            visitCount >= 10 -> 1.0
            visitCount >= 5 -> 0.8
            visitCount >= 3 -> 0.6
            visitCount >= 1 -> 0.3
            else -> 0.0
        }

        return PersonalStationProfile(
            stationId = stationId,
            visitCount = visitCount,
            refuelCount = refuelCount,
            totalLiters = totalLiters,
            totalSpent = totalSpent,
            averagePaidPrice = averagePaidPrice,
            lastVisitAt = lastVisitAt,
            userPreferenceScore = userPreferenceScore
        )
    }
}
