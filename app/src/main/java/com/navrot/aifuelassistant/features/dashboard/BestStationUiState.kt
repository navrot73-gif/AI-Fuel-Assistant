package com.navrot.aifuelassistant.features.dashboard

import com.navrot.aifuelassistant.domain.predictive.TripCostPrediction
import com.navrot.aifuelassistant.domain.recommendation.StationRecommendation
import com.navrot.aifuelassistant.domain.smart.SmartStationRecommendation

data class BestStationUiState(
    val isLoading: Boolean = false,
    val recommendation: StationRecommendation? = null,
    val alternatives: List<StationRecommendation> = emptyList(),
    val smartRecommendation: SmartStationRecommendation? = null,
    val smartAlternatives: List<SmartStationRecommendation> = emptyList(),
    val error: String? = null,
    val tripCostPrediction: TripCostPrediction? = null,
    val personalVisitCount: Int? = null
)
