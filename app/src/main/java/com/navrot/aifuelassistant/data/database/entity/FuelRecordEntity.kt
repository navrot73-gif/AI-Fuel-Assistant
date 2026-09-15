package com.navrot.aifuelassistant.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.navrot.aifuelassistant.domain.personal.PersonalFuelEvent

@Entity(
    tableName = "fuel_records",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["vehicleId"])]
)
data class FuelRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val vehicleId: Long = 0L,
    val date: Long = System.currentTimeMillis(),
    val mileage: Double = 0.0,
    val fuelAmount: Double = 0.0,
    val pricePerLiter: Double = 0.0,
    val totalCost: Double = 0.0,
    val fuelType: String = "АИ-95",
    val stationName: String = "",
    val notes: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val stationId: Int? = null,
    val fullTank: Boolean = false
)

fun FuelRecordEntity.toPersonalFuelEvent(): PersonalFuelEvent {
    return PersonalFuelEvent(
        id = id,
        vehicleId = vehicleId,
        timestamp = date,
        stationId = stationId,
        fuelType = fuelType,
        liters = if (fuelAmount > 0) fuelAmount else null,
        pricePerLiter = if (pricePerLiter > 0) pricePerLiter else null,
        totalCost = if (totalCost > 0) totalCost else null,
        odometerKm = if (mileage > 0) mileage else null,
        latitude = latitude,
        longitude = longitude,
        source = "USER_LOG",
        fullTank = fullTank,
        notes = notes.ifBlank { null }
    )
}
