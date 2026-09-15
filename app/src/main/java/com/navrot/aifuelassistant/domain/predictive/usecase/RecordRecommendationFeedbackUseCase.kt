package com.navrot.aifuelassistant.domain.predictive.usecase

import com.navrot.aifuelassistant.domain.predictive.FeedbackSignal
import com.navrot.aifuelassistant.domain.predictive.RecommendationFeedback
import java.util.UUID
import javax.inject.Inject

class RecordRecommendationFeedbackUseCase @Inject constructor() {

    fun createFeedback(
        recommendationId: String,
        recommendedStationId: Long,
        chosenStationId: Long?,
        routeStarted: Boolean = false,
        routeCompleted: Boolean = false,
        refuelCompleted: Boolean = false
    ): RecommendationFeedback {
        val signal = when {
            chosenStationId == null -> FeedbackSignal.UNKNOWN
            chosenStationId == recommendedStationId -> FeedbackSignal.ACCEPTED
            else -> FeedbackSignal.REJECTED
        }

        return RecommendationFeedback(
            id = UUID.randomUUID().toString(),
            recommendationId = recommendationId,
            recommendedStationId = recommendedStationId,
            chosenStationId = chosenStationId ?: 0L,
            timestamp = System.currentTimeMillis(),
            routeStarted = routeStarted,
            routeCompleted = routeCompleted,
            refuelCompleted = refuelCompleted,
            signal = signal
        )
    }
}
