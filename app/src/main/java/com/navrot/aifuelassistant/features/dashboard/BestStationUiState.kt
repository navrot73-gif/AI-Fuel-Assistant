package com.navrot.aifuelassistant.features.dashboard

import com.navrot.aifuelassistant.domain.predictive.TripCostPrediction
import com.navrot.aifuelassistant.domain.recommendation.StationRecommendation

data class BestStationUiState(
    val isLoading: Boolean = false,
    val recommendation: StationRecommendation? = null,
    val alternatives: List<StationRecommendation> = emptyList(),
    val error: String? = null,
    val tripCostPrediction: TripCostPrediction? = null,
    val personalVisitCount: Int? = null
)
