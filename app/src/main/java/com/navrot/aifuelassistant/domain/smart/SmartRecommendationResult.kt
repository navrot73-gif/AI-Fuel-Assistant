package com.navrot.aifuelassistant.domain.smart

data class SmartRecommendationResult(
    val topRecommendation: SmartStationRecommendation?,
    val alternatives: List<SmartStationRecommendation>,
    val evaluatedCount: Int
)
