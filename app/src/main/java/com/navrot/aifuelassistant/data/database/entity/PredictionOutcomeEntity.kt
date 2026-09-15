package com.navrot.aifuelassistant.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prediction_outcomes")
data class PredictionOutcomeEntity(
    @PrimaryKey
    val predictionId: String,
    val vehicleId: Long,
    val predictedValue: Double,
    val actualValue: Double,
    val absoluteError: Double,
    val relativeError: Double,
    val timestamp: Long
)
