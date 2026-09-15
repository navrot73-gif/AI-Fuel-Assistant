package com.navrot.aifuelassistant.domain.predictive.usecase

import com.navrot.aifuelassistant.domain.personal.PersonalFuelEvent
import com.navrot.aifuelassistant.domain.predictive.FeedbackSignal
import com.navrot.aifuelassistant.domain.predictive.PersonalStationLearning
import com.navrot.aifuelassistant.domain.predictive.RecommendationFeedback
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

        val stationFeedbacks = feedbacks.filter { it.chosenStationId == stationId.toLong() }
        val chosenAfterRecCount = stationFeedbacks.count { it.signal == FeedbackSignal.ACCEPTED }
        val repeatVisitsCount = if (refuelsCount > 1) refuelsCount - 1 else 0

        val paidPrices = stationEvents.mapNotNull { it.pricePerLiter }.filter { it > 0 }
        val avgPaid = if (paidPrices.isNotEmpty()) paidPrices.average() else null

        val allPaidPrices = events.mapNotNull { it.pricePerLiter }.filter { it > 0 }
        val globalAvgPaid = if (allPaidPrices.isNotEmpty()) allPaidPrices.average() else null
        val priceDiff = if (avgPaid != null && globalAvgPaid != null) avgPaid - globalAvgPaid else null

        // Personal preference score computation (layered separately from D1 objective score)
        // visits (weight 1.0) + chosen after recommendation (weight 1.5) + repeat visits (weight 0.5)
        val prefScore = (refuelsCount * 1.0) + (chosenAfterRecCount * 1.5) + (repeatVisitsCount * 0.5)

        return PersonalStationLearning(
            stationId = stationId,
            visitsCount = visitsCount,
            refuelsCount = refuelsCount,
            chosenAfterRecommendationCount = chosenAfterRecCount,
            repeatVisitsCount = repeatVisitsCount,
            averagePaidPrice = avgPaid,
            priceDifferenceFromAverage = priceDiff,
            personalPreferenceScore = prefScore
        )
    }

    fun getAllStationLearnings(
        events: List<PersonalFuelEvent>,
        feedbacks: List<RecommendationFeedback>
    ): Map<Int, PersonalStationLearning> {
        val stationIds = events.mapNotNull { it.stationId }.toSet()
        return stationIds.associateWith { getStationLearning(it, events, feedbacks) }
    }
}
