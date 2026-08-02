package com.navrot.aifuelassistant.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fuel_records")
data class FuelRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val vehicleId: Long = 0L,
    val date: Long = System.currentTimeMillis(),
    val mileage: Double = 0.0,
    val fuelAmount: Double = 0.0,
    val pricePerLiter: Double = 0.0,
    val totalCost: Double = 0.0,
    val fuelType: String = "Бензин",
    val stationName: String = "",
    val notes: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null
)