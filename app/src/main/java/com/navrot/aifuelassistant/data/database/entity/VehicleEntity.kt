package com.navrot.aifuelassistant.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String = "",
    val brand: String = "",
    val model: String = "",
    val year: Int = 2026,
    val fuelType: String = "Бензин",
    val tankCapacity: Double = 50.0,
    val currentMileage: Double = 0.0
)