package com.navrot.aifuelassistant.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recommendation_feedbacks")
data class RecommendationFeedbackEntity(
    @PrimaryKey
    val id: String,
    val recommendationId: String,
    val recommendedStationId: Long,
    val chosenStationId: Long,
    val timestamp: Long,
    val routeStarted: Boolean,
    val routeCompleted: Boolean,
    val refuelCompleted: Boolean,
    val signal: String
)
