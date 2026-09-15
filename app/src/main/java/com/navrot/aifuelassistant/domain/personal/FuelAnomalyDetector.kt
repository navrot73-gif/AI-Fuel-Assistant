package com.navrot.aifuelassistant.domain.personal

data class AnomalyResult(
    val isAnomaly: Boolean,
    val currentConsumption: Double?,
    val baselineConsumption: Double?,
    val elevationPercentage: Double?,
    val message: String?
)

class FuelAnomalyDetector(
    private val thresholdPercentage: Double = 15.0 // 15% increase threshold
) {

    fun checkAnomaly(
        currentConsumption: Double,
        baseline: PersonalFuelBaseline
    ): AnomalyResult {
        val normal = baseline.normalConsumption ?: return AnomalyResult(
            isAnomaly = false,
            currentConsumption = currentConsumption,
            baselineConsumption = null,
            elevationPercentage = null,
            message = null
        )

        if (normal <= 0) return AnomalyResult(false, currentConsumption, normal, null, null)

        val elevation = ((currentConsumption - normal) / normal) * 100.0

        return if (elevation >= thresholdPercentage) {
            val formattedCurrent = String.format("%.1f", currentConsumption)
            val formattedNormal = String.format("%.1f", normal)
            AnomalyResult(
                isAnomaly = true,
                currentConsumption = currentConsumption,
                baselineConsumption = normal,
                elevationPercentage = elevation,
                message = "Расход выше вашего обычного: $formattedCurrent л/100 км (обычно $formattedNormal л/100 км)"
            )
        } else {
            AnomalyResult(
                isAnomaly = false,
                currentConsumption = currentConsumption,
                baselineConsumption = normal,
                elevationPercentage = elevation,
                message = null
            )
        }
    }
}
