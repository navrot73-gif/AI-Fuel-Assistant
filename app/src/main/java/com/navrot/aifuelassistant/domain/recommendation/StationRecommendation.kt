package com.navrot.aifuelassistant.domain.recommendation

import com.navrot.aifuelassistant.data.model.GasStation

data class StationRecommendation(
    val station: GasStation,
    val score: Double,
    val estimatedTotalCost: Double?,
    val reasons: List<StationRecommendationReason>,
    val confidence: RecommendationConfidence
)
