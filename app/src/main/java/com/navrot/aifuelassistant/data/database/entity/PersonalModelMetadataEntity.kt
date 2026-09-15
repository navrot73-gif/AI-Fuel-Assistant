package com.navrot.aifuelassistant.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "personal_model_metadata")
data class PersonalModelMetadataEntity(
    @PrimaryKey
    val vehicleId: Long,
    val modelVersion: Int,
    val trainedSamples: Int,
    val biasAdjustment: Double,
    val lastUpdatedAt: Long
)
