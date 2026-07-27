package com.navrot.aifuelassistant.domain.fuel

/**
 * Тестовые данные для проверки диспетчера до подключения реального источника данных.
 */
object DemoFuelStations {

    val stations = listOf(
        FuelStation(
            id = "station-1",
            name = "АЗС Северная",
            address = "Челябинск",
            latitude = 55.1600,
            longitude = 61.4000,
            distanceKm = 3.2,
            driveTimeMinutes = 6,
            dataUpdatedMinutesAgo = 3,
            fuel = mapOf(
                "АИ-92" to FuelAvailability(
                    status = FuelAvailabilityStatus.AVAILABLE,
                    pricePerLiter = 62.50,
                    queueCars = 2,
                    estimatedWaitMinutes = 5
                ),
                "АИ-95" to FuelAvailability(
                    status = FuelAvailabilityStatus.AVAILABLE,
                    pricePerLiter = 69.08,
                    queueCars = 2,
                    estimatedWaitMinutes = 5
                )
            )
        ),
        FuelStation(
            id = "station-2",
            name = "АЗС Центральная",
            address = "Челябинск",
            latitude = 55.1700,
            longitude = 61.4100,
            distanceKm = 1.8,
            driveTimeMinutes = 4,
            dataUpdatedMinutesAgo = 7,
            fuel = mapOf(
                "АИ-92" to FuelAvailability(
                    status = FuelAvailabilityStatus.LIMITED,
                    pricePerLiter = 61.90,
                    queueCars = 18,
                    estimatedWaitMinutes = 25
                ),
                "АИ-95" to FuelAvailability(
                    status = FuelAvailabilityStatus.OUT_OF_STOCK,
                    queueCars = 0,
                    estimatedWaitMinutes = 0
                )
            )
        ),
        FuelStation(
            id = "station-3",
            name = "АЗС Южная",
            address = "Челябинск",
            latitude = 55.1200,
            longitude = 61.3900,
            distanceKm = 5.4,
            driveTimeMinutes = 9,
            dataUpdatedMinutesAgo = 2,
            fuel = mapOf(
                "АИ-95" to FuelAvailability(
                    status = FuelAvailabilityStatus.AVAILABLE,
                    pricePerLiter = 68.70,
                    queueCars = 5,
                    estimatedWaitMinutes = 12
                )
            )
        )
    )
}
