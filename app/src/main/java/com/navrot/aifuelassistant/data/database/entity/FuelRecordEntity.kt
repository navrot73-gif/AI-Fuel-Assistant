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
    val vehicleId: Long,           // ID автомобиля
    val date: Long,                // Дата заправки (timestamp)
    val mileage: Double,           // Пробег на момент заправки
    val fuelAmount: Double,        // Количество литров
    val pricePerLiter: Double,     // Цена за литр
    val totalCost: Double,         // Общая стоимость
    val fuelType: String,          // Тип топлива
    val stationName: String = "",  // Название АЗС
    val notes: String = ""         // Примечания
)