package com.navrot.aifuelassistant.domain.predictive.usecase

import com.navrot.aifuelassistant.domain.personal.PersonalFuelEvent
import com.navrot.aifuelassistant.domain.predictive.FeedbackSignal
import com.navrot.aifuelassistant.domain.predictive.PersonalStationLearning
import com.navrot.aifuelassistant.domain.predictive.RecommendationFeedback
import com.navrot.aifuelassistant.domain.predictive.UserAction
import com.navrot.aifuelassistant.domain.predictive.UserOutcome
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import javax.inject.Inject

class PersonalStationPreferenceUseCase @Inject constructor() {

    fun getStationLearning(
        stationId: Int,
        events: List<PersonalFuelEvent>,
        feedbacks: List<RecommendationFeedback>
    ): PersonalStationLearning {
        val stationEvents = events.filter { it.stationId == stationId }
        val refuelsCount = stationEvents.size
        val visitsCount = stationEvents.size // Each logged refuel is a visit

        val stationFeedbacks = feedbacks.filter {
            it.chosenStationId == stationId.toLong() || it.recommendedStationId == stationId.toLong()
        }

        // Filter valid confirmed feedbacks - ignore VIEWED, SKIPPED, ROUTE_STARTED without confirmation, or UNKNOWN
        val successfulFeedbacks = stationFeedbacks.filter { fb ->
            (fb.userConfirmed && fb.action == UserAction.REFUELLED && fb.outcome == UserOutcome.SUCCESS) ||
            (fb.userConfirmed && fb.actualAvailability == FuelAvailabilityStatus.AVAILABLE) ||
            (fb.refuelCompleted && fb.signal == FeedbackSignal.ACCEPTED)
        }

        val failedFeedbacks = stationFeedbacks.filter { fb ->
            fb.outcome == UserOutcome.FAILED ||
            fb.actualAvailability == FuelAvailabilityStatus.UNAVAILABLE ||
            fb.actualAvailability == FuelAvailabilityStatus.NO_FUEL
        }

        val successfulRefuelCount = successfulFeedbacks.size
        val failedRefuelCount = failedFeedbacks.size

        val chosenAfterRecCount = stationFeedbacks.count {
            it.signal == FeedbackSignal.ACCEPTED && (it.refuelCompleted || it.userConfirmed)
        }
        val repeatVisitsCount = if (refuelsCount > 1) refuelsCount - 1 else 0

        val paidPrices = stationEvents.mapNotNull { it.pricePerLiter }.filter { it > 0.0 }
            .plus(stationFeedbacks.mapNotNull { if (it.userConfirmed) it.actualPrice else null }.filter { it > 0.0 })

        val avgPaid = if (paidPrices.isNotEmpty()) paidPrices.average() else null

        val allPaidPrices = events.mapNotNull { it.pricePerLiter }.filter { it > 0.0 }
        val globalAvgPaid = if (allPaidPrices.isNotEmpty()) allPaidPrices.average() else null
        val priceDiff = if (avgPaid != null && globalAvgPaid != null) avgPaid - globalAvgPaid else null

        val lastEventTime = stationEvents.maxOfOrNull { it.timestamp }
        val lastFeedbackTime = stationFeedbacks.filter { it.userConfirmed }.maxOfOrNull { it.timestamp }
        val lastVisitedAt = listOfNotNull(lastEventTime, lastFeedbackTime).maxOrNull()

        // Compute personal preference score deterministically
        val prefScore = (refuelsCount * 1.0) +
                (successfulRefuelCount * 1.5) +
                (chosenAfterRecCount * 1.5) +
                (repeatVisitsCount * 0.5) -
                (failedRefuelCount * 1.5)

        return PersonalStationLearning(
            stationId = stationId,
            visitsCount = visitsCount,
            refuelsCount = refuelsCount,
            chosenAfterRecommendationCount = chosenAfterRecCount,
            repeatVisitsCount = repeatVisitsCount,
            averagePaidPrice = avgPaid,
            priceDifferenceFromAverage = priceDiff,
            personalPreferenceScore = prefScore,
            successfulRefuelCount = successfulRefuelCount,
            failedRefuelCount = failedRefuelCount,
            lastVisitedAt = lastVisitedAt
        )
    }

    fun getAllStationLearnings(
        events: List<PersonalFuelEvent>,
        feedbacks: List<RecommendationFeedback>
    ): Map<Int, PersonalStationLearning> {
        val eventStationIds = events.mapNotNull { it.stationId }
        val feedbackStationIds = feedbacks.map { it.chosenStationId.toInt() }
        val stationIds = (eventStationIds + feedbackStationIds).toSet()

        return stationIds.associateWith { getStationLearning(it, events, feedbacks) }
    }
}
