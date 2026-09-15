package com.navrot.aifuelassistant.domain.personal

import com.navrot.aifuelassistant.domain.recommendation.StationRecommendation

data class PersonalRecommendation(
    val baseRecommendation: StationRecommendation,
    val personalScore: Double,
    val isPersonalized: Boolean,
    val personalizedReasons: List<String>
)
