package com.navrot.aifuelassistant.data.model

data class GasStation(
    val id: Int,
    val name: String,
    val brand: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val fuelTypes: List<FuelPrice>,
    val queueTime: Int,
    val reliability: Int,
    val dataSources: Set<FuelDataSource> = emptySet(),
    val updatedAt: Long = 0L,
    val confidence: Int = 0,
    val photoEvidence: List<PhotoEvidence> = emptyList()
)
