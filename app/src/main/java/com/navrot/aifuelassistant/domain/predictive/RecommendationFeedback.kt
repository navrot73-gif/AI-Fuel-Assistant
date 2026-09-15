package com.navrot.aifuelassistant.domain.predictive

enum class FeedbackSignal {
    ACCEPTED,
    REJECTED,
    UNKNOWN
}

/**
 * Recorded user action signal for a station recommendation.
 */
data class RecommendationFeedback(
    val id: String,
    val recommendationId: String,
    val recommendedStationId: Long,
    val chosenStationId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val routeStarted: Boolean = false,
    val routeCompleted: Boolean = false,
    val refuelCompleted: Boolean = false,
    val signal: FeedbackSignal = if (recommendedStationId == chosenStationId) FeedbackSignal.ACCEPTED else FeedbackSignal.REJECTED
)
