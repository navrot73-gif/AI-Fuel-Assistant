package com.navrot.aifuelassistant.domain.fuel

/**
 * АЗС, отображаемая в диспетчере топлива.
 *
 * Это доменная модель MVP. Источник данных может быть заменён без изменения
 * главного экрана: API, парсер, пользовательский сигнал или агрегатор данных.
 */
data class FuelStation(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val distanceKm: Double? = null,
    val driveTimeMinutes: Int? = null,
    val fuel: Map<String, FuelAvailability> = emptyMap(),
    val dataUpdatedMinutesAgo: Int? = null
)

data class FuelAvailability(
    val status: FuelAvailabilityStatus,
    val pricePerLiter: Double? = null,
    val queueCars: Int? = null,
    val estimatedWaitMinutes: Int? = null
)
