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
    val signal: String,
    val fuelType: String? = null,
    val action: String = "VIEWED",
    val outcome: String = "UNKNOWN",
    val predictedAvailability: String? = null,
    val actualAvailability: String? = null,
    val predictedPrice: Double? = null,
    val actualPrice: Double? = null,
    val predictedQueue: Int? = null,
    val actualQueue: Int? = null,
    val dataConfidence: String? = null,
    val userConfirmed: Boolean = false,
    val source: String = "USER_CONFIRMED",
    val notes: String? = null
)
