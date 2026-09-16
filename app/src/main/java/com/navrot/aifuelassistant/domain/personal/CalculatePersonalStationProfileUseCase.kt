package com.navrot.aifuelassistant.domain.personal

import com.navrot.aifuelassistant.domain.predictive.RecommendationFeedback
import com.navrot.aifuelassistant.domain.predictive.UserAction
import com.navrot.aifuelassistant.domain.predictive.UserOutcome
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus

class CalculatePersonalStationProfileUseCase {

    fun execute(
        stationId: Int,
        events: List<PersonalFuelEvent>,
        feedbacks: List<RecommendationFeedback> = emptyList()
    ): PersonalStationProfile {
        val stationEvents = events.filter { it.stationId == stationId }
        val stationFeedbacks = feedbacks.filter {
            it.chosenStationId == stationId.toLong() || it.recommendedStationId == stationId.toLong()
        }

        if (stationEvents.isEmpty() && stationFeedbacks.isEmpty()) {
            return PersonalStationProfile(stationId = stationId)
        }

        val refuelsWithLiters = stationEvents.filter { (it.liters ?: 0.0) > 0.0 }
        val refuelCount = stationEvents.size
        val totalLiters = refuelsWithLiters.sumOf { it.liters ?: 0.0 }
        val totalSpent = stationEvents.sumOf { it.totalCost ?: 0.0 }

        val successfulFeedbacks = stationFeedbacks.filter { fb ->
            (fb.userConfirmed && fb.action == UserAction.REFUELLED && fb.outcome == UserOutcome.SUCCESS) ||
            (fb.userConfirmed && fb.actualAvailability == FuelAvailabilityStatus.AVAILABLE) ||
            (fb.refuelCompleted)
        }

        val failedFeedbacks = stationFeedbacks.filter { fb ->
            fb.outcome == UserOutcome.FAILED ||
            fb.actualAvailability == FuelAvailabilityStatus.UNAVAILABLE ||
            fb.actualAvailability == FuelAvailabilityStatus.NO_FUEL
        }

        val successfulRefuelCount = refuelCount + successfulFeedbacks.size
        val failedRefuelCount = failedFeedbacks.size
        val visitCount = refuelCount + stationFeedbacks.count { it.userConfirmed || it.routeStarted }

        val paidPrices = stationEvents.mapNotNull { it.pricePerLiter }.filter { it > 0.0 }
            .plus(stationFeedbacks.mapNotNull { if (it.userConfirmed) it.actualPrice else null }.filter { it > 0.0 })

        val averagePaidPrice = if (paidPrices.isNotEmpty()) paidPrices.average() else null

        val eventLastVisit = stationEvents.maxOfOrNull { it.timestamp }
        val feedbackLastVisit = stationFeedbacks.filter { it.userConfirmed }.maxOfOrNull { it.timestamp }
        val lastVisitAt = listOfNotNull(eventLastVisit, feedbackLastVisit).maxOrNull()

        val userPreferenceScore = when {
            successfulRefuelCount - failedRefuelCount >= 10 -> 1.0
            successfulRefuelCount - failedRefuelCount >= 5 -> 0.8
            successfulRefuelCount - failedRefuelCount >= 3 -> 0.6
            successfulRefuelCount - failedRefuelCount >= 1 -> 0.3
            else -> 0.0
        }

        return PersonalStationProfile(
            stationId = stationId,
            visitCount = visitCount,
            refuelCount = refuelCount,
            totalLiters = totalLiters,
            totalSpent = totalSpent,
            averagePaidPrice = averagePaidPrice,
            averageObservedPrice = averagePaidPrice,
            lastVisitAt = lastVisitAt,
            userPreferenceScore = userPreferenceScore,
            successfulRefuelCount = successfulRefuelCount,
            failedRefuelCount = failedRefuelCount
        )
    }
}
