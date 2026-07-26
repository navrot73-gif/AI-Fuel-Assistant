package com.navrot.aifuelassistant.domain.fuel

object DemoFuelStations {
    val stations = listOf(
        FuelStation(
            id = "1",
            name = "АЗС №1 (Лукойл)",
            address = "ул. Ленина, 10",
            latitude = 55.7558,
            longitude = 37.6173,
            distanceKm = 1.2,
            driveTimeMinutes = 5,
            fuel = mapOf(
                "АИ-95" to FuelAvailability(
                    status = FuelAvailabilityStatus.AVAILABLE,
                    pricePerLiter = 48.5,
                    queueCars = 2,
                    estimatedWaitMinutes = 3
                ),
                "АИ-92" to FuelAvailability(
                    status = FuelAvailabilityStatus.AVAILABLE,
                    pricePerLiter = 45.2,
                    queueCars = 1,
                    estimatedWaitMinutes = 2
                ),
                "ДТ" to FuelAvailability(
                    status = FuelAvailabilityStatus.LIMITED,
                    pricePerLiter = 46.8,
                    queueCars = 5,
                    estimatedWaitMinutes = 10
                )
            ),
            dataUpdatedMinutesAgo = 5
        ),
        FuelStation(
            id = "2",
            name = "АЗС №2 (Газпром)",
            address = "ул. Пушкина, 20",
            latitude = 55.7658,
            longitude = 37.6273,
            distanceKm = 3.0,
            driveTimeMinutes = 8,
            fuel = mapOf(
                "АИ-95" to FuelAvailability(
                    status = FuelAvailabilityStatus.AVAILABLE,
                    pricePerLiter = 47.2,
                    queueCars = 0,
                    estimatedWaitMinutes = 0
                ),
                "АИ-92" to FuelAvailability(
                    status = FuelAvailabilityStatus.AVAILABLE,
                    pricePerLiter = 44.5,
                    queueCars = 0,
                    estimatedWaitMinutes = 0
                )
            ),
            dataUpdatedMinutesAgo = 2
        ),
        FuelStation(
            id = "3",
            name = "АЗС №3 (Роснефть)",
            address = "ул. Мира, 5",
            latitude = 55.7458,
            longitude = 37.6073,
            distanceKm = 2.5,
            driveTimeMinutes = 7,
            fuel = mapOf(
                "АИ-95" to FuelAvailability(
                    status = FuelAvailabilityStatus.UNAVAILABLE,
                    pricePerLiter = null,
                    queueCars = null,
                    estimatedWaitMinutes = null
                ),
                "ДТ" to FuelAvailability(
                    status = FuelAvailabilityStatus.AVAILABLE,
                    pricePerLiter = 47.0,
                    queueCars = 3,
                    estimatedWaitMinutes = 5
                )
            ),
            dataUpdatedMinutesAgo = 10
        )
    )
}