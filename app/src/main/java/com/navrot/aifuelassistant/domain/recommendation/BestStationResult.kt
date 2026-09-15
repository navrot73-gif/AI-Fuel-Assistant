package com.navrot.aifuelassistant.domain.recommendation

data class BestStationResult(
    val best: StationRecommendation?,
    val alternatives: List<StationRecommendation>,
    val evaluatedCount: Int
)
