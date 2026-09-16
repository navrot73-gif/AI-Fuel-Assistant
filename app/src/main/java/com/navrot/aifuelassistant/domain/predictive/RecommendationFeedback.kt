package com.navrot.aifuelassistant.domain.predictive

import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus

enum class FeedbackSignal {
    ACCEPTED,
    REJECTED,
    UNKNOWN
}

enum class UserAction {
    VIEWED,
    ROUTE_STARTED,
    ARRIVED,
    REFUELLED,
    SKIPPED
}

enum class UserOutcome {
    SUCCESS,
    PARTIAL,
    FAILED,
    UNKNOWN
}

enum class EventSource {
    USER_CONFIRMED,
    AUTOMATIC,
    IMPORTED
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
    val signal: FeedbackSignal = if (recommendedStationId == chosenStationId) FeedbackSignal.ACCEPTED else FeedbackSignal.REJECTED,
    val fuelType: String? = null,
    val action: UserAction = if (refuelCompleted) UserAction.REFUELLED else if (routeStarted) UserAction.ROUTE_STARTED else UserAction.VIEWED,
    val outcome: UserOutcome = if (refuelCompleted) UserOutcome.SUCCESS else UserOutcome.UNKNOWN,
    val predictedAvailability: FuelAvailabilityStatus? = null,
    val actualAvailability: FuelAvailabilityStatus? = null,
    val predictedPrice: Double? = null,
    val actualPrice: Double? = null,
    val predictedQueue: Int? = null,
    val actualQueue: Int? = null,
    val dataConfidence: String? = null,
    val userConfirmed: Boolean = false,
    val source: EventSource = EventSource.USER_CONFIRMED,
    val notes: String? = null
)
