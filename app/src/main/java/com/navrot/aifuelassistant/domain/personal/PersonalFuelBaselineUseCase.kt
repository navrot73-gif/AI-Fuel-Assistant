package com.navrot.aifuelassistant.domain.personal

class PersonalFuelBaselineUseCase {

    fun calculateBaseline(
        vehicleId: Long,
        events: List<PersonalFuelEvent>
    ): PersonalFuelBaseline {
        val statsUseCase = CalculatePersonalFuelStatisticsUseCase()
        val stats = statsUseCase.execute(vehicleId, events, Period.ALL_TIME)

        val consumptions = PersonalConsumptionCalculator.calculateConsumptions(events.filter { it.vehicleId == vehicleId })
        val normalConsumption = if (consumptions.size >= 2) consumptions.average() else null

        return PersonalFuelBaseline(
            vehicleId = vehicleId,
            normalConsumption = normalConsumption,
            normalPricePerLiter = stats.averagePricePerLiter
        )
    }
}
