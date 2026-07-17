package com.navrot.aifuelassistant.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,           // Название авто, например "Моя Toyota"
    val brand: String,          // Марка
    val model: String,          // Модель
    val year: Int,              // Год выпуска
    val fuelType: String,       // Тип топлива: Бензин, Дизель, Газ и т.д.
    val tankCapacity: Double,   // Объём бака в литрах
    val currentMileage: Double  // Текущий пробег в км
)