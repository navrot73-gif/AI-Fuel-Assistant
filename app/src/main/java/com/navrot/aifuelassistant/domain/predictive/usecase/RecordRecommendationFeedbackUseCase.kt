package com.navrot.aifuelassistant.domain.predictive.usecase

import com.navrot.aifuelassistant.domain.predictive.EventSource
import com.navrot.aifuelassistant.domain.predictive.FeedbackSignal
import com.navrot.aifuelassistant.domain.predictive.RecommendationFeedback
import com.navrot.aifuelassistant.domain.predictive.UserAction
import com.navrot.aifuelassistant.domain.predictive.UserOutcome
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import java.util.UUID
import javax.inject.Inject

class RecordRecommendationFeedbackUseCase @Inject constructor() {

    fun createFeedback(
        recommendationId: String,
        recommendedStationId: Long,
        chosenStationId: Long?,
        routeStarted: Boolean = false,
        routeCompleted: Boolean = false,
        refuelCompleted: Boolean = false,
        fuelType: String? = null,
        action: UserAction? = null,
        outcome: UserOutcome? = null,
        predictedAvailability: FuelAvailabilityStatus? = null,
        actualAvailability: FuelAvailabilityStatus? = null,
        predictedPrice: Double? = null,
        actualPrice: Double? = null,
        predictedQueue: Int? = null,
        actualQueue: Int? = null,
        dataConfidence: String? = null,
        userConfirmed: Boolean = false,
        source: EventSource = EventSource.USER_CONFIRMED,
        notes: String? = null
    ): RecommendationFeedback {
        val targetChosenId = chosenStationId ?: recommendedStationId

        val resolvedAction = action ?: when {
            refuelCompleted -> UserAction.REFUELLED
            routeStarted -> UserAction.ROUTE_STARTED
            else -> UserAction.VIEWED
        }

        val resolvedOutcome = outcome ?: when {
            refuelCompleted -> UserOutcome.SUCCESS
            else -> UserOutcome.UNKNOWN
        }

        val signal = when {
            chosenStationId == null && !userConfirmed -> FeedbackSignal.UNKNOWN
            resolvedOutcome == UserOutcome.FAILED || actualAvailability == FuelAvailabilityStatus.UNAVAILABLE || actualAvailability == FuelAvailabilityStatus.NO_FUEL -> FeedbackSignal.REJECTED
            targetChosenId == recommendedStationId -> FeedbackSignal.ACCEPTED
            else -> FeedbackSignal.REJECTED
        }

        return RecommendationFeedback(
            id = UUID.randomUUID().toString(),
            recommendationId = recommendationId,
            recommendedStationId = recommendedStationId,
            chosenStationId = targetChosenId,
            timestamp = System.currentTimeMillis(),
            routeStarted = routeStarted || resolvedAction == UserAction.ROUTE_STARTED,
            routeCompleted = routeCompleted,
            refuelCompleted = refuelCompleted || (resolvedAction == UserAction.REFUELLED && resolvedOutcome == UserOutcome.SUCCESS),
            signal = signal,
            fuelType = fuelType,
            action = resolvedAction,
            outcome = resolvedOutcome,
            predictedAvailability = predictedAvailability,
            actualAvailability = actualAvailability,
            predictedPrice = predictedPrice,
            actualPrice = actualPrice,
            predictedQueue = predictedQueue,
            actualQueue = actualQueue,
            dataConfidence = dataConfidence,
            userConfirmed = userConfirmed,
            source = source,
            notes = notes
        )
    }
}
