package com.navrot.aifuelassistant.features.dashboard

import com.navrot.aifuelassistant.domain.predictive.TripCostPrediction
import com.navrot.aifuelassistant.domain.recommendation.StationRecommendation
import com.navrot.aifuelassistant.domain.smart.SmartStationRecommendation

enum class FeedbackUiStatus {
    NOT_AVAILABLE,
    PROMPT,
    DETAILS,
    SUBMITTED,
    CANCELLED
}

data class FeedbackUiState(
    val status: FeedbackUiStatus = FeedbackUiStatus.NOT_AVAILABLE,
    val isRefuelConfirmed: Boolean? = null,
    val fuelAvailable: Boolean = true,
    val priceMatched: Boolean = true,
    val actualPriceInput: String = "",
    val hasQueue: Boolean = false,
    val actualQueueMinutesInput: String = "",
    val submittedFeedbackId: String? = null
)

data class BestStationUiState(
    val isLoading: Boolean = false,
    val recommendation: StationRecommendation? = null,
    val alternatives: List<StationRecommendation> = emptyList(),
    val smartRecommendation: SmartStationRecommendation? = null,
    val smartAlternatives: List<SmartStationRecommendation> = emptyList(),
    val error: String? = null,
    val tripCostPrediction: TripCostPrediction? = null,
    val personalVisitCount: Int? = null,
    val feedbackUiState: FeedbackUiState = FeedbackUiState()
)
