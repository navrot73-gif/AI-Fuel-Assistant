package com.navrot.aifuelassistant.domain.personal

object PersonalConsumptionCalculator {

    /**
     * Calculates real fuel consumption (L/100km) between two consecutive full tank refuels.
     *
     * Returns null if:
     * - Either previous or current event is not a full tank refuel (`fullTank != true`).
     * - Valid odometer readings are missing on either event.
     * - Current odometer <= previous odometer.
     * - Current refuel liters is null or <= 0.
     */
    fun calculateFullTankConsumption(
        previous: PersonalFuelEvent,
        current: PersonalFuelEvent
    ): Double? {
        if (!previous.fullTank || !current.fullTank) return null

        val prevOdo = previous.odometerKm ?: return null
        val currOdo = current.odometerKm ?: return null

        val distanceKm = currOdo - prevOdo
        if (distanceKm <= 0.0) return null

        val liters = current.liters ?: return null
        if (liters <= 0.0) return null

        return (liters / distanceKm) * 100.0
    }

    /**
     * Calculates a list of consumption records (L/100km) from a chronologically sorted or raw list of fuel events for a single vehicle.
     */
    fun calculateConsumptions(events: List<PersonalFuelEvent>): List<Double> {
        val sorted = events
            .filter { it.odometerKm != null && it.odometerKm > 0 }
            .sortedBy { it.odometerKm }

        val consumptions = mutableListOf<Double>()
        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val curr = sorted[i]
            val c = calculateFullTankConsumption(prev, curr)
            if (c != null && c.isFinite()) {
                consumptions.add(c)
            }
        }
        return consumptions
    }
}
