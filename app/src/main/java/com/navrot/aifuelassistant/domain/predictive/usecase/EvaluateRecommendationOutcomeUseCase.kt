package com.navrot.aifuelassistant.domain.predictive.usecase

import com.navrot.aifuelassistant.domain.predictive.RecommendationFeedback
import com.navrot.aifuelassistant.domain.predictive.RecommendationOutcome
import com.navrot.aifuelassistant.domain.predictive.UserAction
import com.navrot.aifuelassistant.domain.predictive.UserOutcome
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import javax.inject.Inject
import kotlin.math.abs

class EvaluateRecommendationOutcomeUseCase @Inject constructor() {

    fun execute(feedback: RecommendationFeedback): RecommendationOutcome {
        val actualAvail = feedback.actualAvailability
        val predAvail = feedback.predictedAvailability

        val availabilityCorrect = when {
            actualAvail == null || actualAvail == FuelAvailabilityStatus.UNKNOWN -> null
            predAvail == null || predAvail == FuelAvailabilityStatus.UNKNOWN -> null
            else -> actualAvail == predAvail
        }

        val actualPrice = feedback.actualPrice
        val predPrice = feedback.predictedPrice

        val priceError = if (actualPrice != null && predPrice != null && actualPrice > 0.0 && predPrice > 0.0) {
            actualPrice - predPrice
        } else null

        val absPriceError = priceError?.let { abs(it) }

        val actualQueue = feedback.actualQueue
        val predQueue = feedback.predictedQueue

        val queueError = if (actualQueue != null && predQueue != null) {
            actualQueue - predQueue
        } else null

        val isConfirmedFact = feedback.userConfirmed ||
                (feedback.action == UserAction.REFUELLED && feedback.outcome == UserOutcome.SUCCESS)

        return RecommendationOutcome(
            feedbackId = feedback.id,
            recommendationId = feedback.recommendationId,
            stationId = feedback.chosenStationId,
            fuelType = feedback.fuelType,
            action = feedback.action,
            outcome = feedback.outcome,
            predictedAvailability = predAvail,
            actualAvailability = actualAvail,
            availabilityCorrect = availabilityCorrect,
            predictedPrice = predPrice,
            actualPrice = actualPrice,
            priceError = priceError,
            absolutePriceError = absPriceError,
            predictedQueue = predQueue,
            actualQueue = actualQueue,
            queueError = queueError,
            isConfirmedFact = isConfirmedFact,
            timestamp = feedback.timestamp
        )
    }
}
