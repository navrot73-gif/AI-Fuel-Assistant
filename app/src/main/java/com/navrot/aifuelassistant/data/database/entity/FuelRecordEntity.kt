package com.navrot.aifuelassistant.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "fuel_records",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class FuelRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val vehicleId: Long,
    val date: Long,
    val mileage: Double,
    val fuelAmount: Double,
    val pricePerLiter: Double,
    val totalCost: Double,
    val fuelType: String,
    val stationName: String = "",
    val notes: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null
)