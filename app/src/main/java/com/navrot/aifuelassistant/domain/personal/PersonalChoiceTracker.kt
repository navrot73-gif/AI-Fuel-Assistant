package com.navrot.aifuelassistant.domain.personal

import com.navrot.aifuelassistant.domain.recommendation.StationRecommendation

data class UserStationChoice(
    val recommendedStationId: Int,
    val chosenStationId: Int,
    val timestamp: Long = System.currentTimeMillis()
)

class PersonalChoiceTracker {
    private val choices = mutableListOf<UserStationChoice>()

    fun recordChoice(recommendedStationId: Int, chosenStationId: Int) {
        choices.add(UserStationChoice(recommendedStationId, chosenStationId))
    }

    fun getChoices(): List<UserStationChoice> = choices.toList()
}
