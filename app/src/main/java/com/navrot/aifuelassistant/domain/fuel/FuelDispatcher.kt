package com.navrot.aifuelassistant.domain.fuel

/**
 * Прозрачный MVP-алгоритм выбора лучшей АЗС.
 *
 * Приоритеты:
 * 1. Нужное топливо должно быть доступно.
 * 2. Меньше время ожидания.
 * 3. Меньше время в пути.
 * 4. Ниже цена.
 */
object FuelDispatcher {

    fun rank(
        stations: List<FuelStation>,
        fuelType: String
    ): List<FuelStation> {
        return stations
            .filter { station ->
                station.fuel[fuelType]?.status == FuelAvailabilityStatus.AVAILABLE ||
                    station.fuel[fuelType]?.status == FuelAvailabilityStatus.LIMITED
            }
            .sortedWith(
                compareBy<FuelStation> {
                    station ->
                    station.fuel[fuelType]?.estimatedWaitMinutes ?: Int.MAX_VALUE
                }.thenBy {
                    station ->
                    station.driveTimeMinutes ?: Int.MAX_VALUE
                }.thenBy {
                    station ->
                    station.fuel[fuelType]?.pricePerLiter ?: Double.MAX_VALUE
                }
            )
    }

    fun best(
        stations: List<FuelStation>,
        fuelType: String
    ): FuelStation? = rank(stations, fuelType).firstOrNull()
}
