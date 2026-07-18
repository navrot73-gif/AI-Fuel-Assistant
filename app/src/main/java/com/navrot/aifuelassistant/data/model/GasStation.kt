package com.navrot.aifuelassistant.data.model

data class GasStation(
    val id: Long,
    val name: String,
    val brand: String,           // Лукойл, Газпромнефть и т.д.
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val fuelTypes: List<FuelPrice>, // Типы топлива и цены
    val queueTime: Int,          // Время очереди в минутах
    val reliability: Int,          // Достоверность 0-100%
    val isOpen: Boolean = true
)

data class FuelPrice(
    val type: String,            // АИ-95, АИ-92, Дизель
    val price: Double,
    val available: Boolean = true
)