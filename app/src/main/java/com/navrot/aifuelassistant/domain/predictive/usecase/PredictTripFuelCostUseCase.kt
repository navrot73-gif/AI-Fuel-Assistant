package com.navrot.aifuelassistant.domain.predictive.usecase

import com.navrot.aifuelassistant.domain.predictive.ConsumptionPrediction
import com.navrot.aifuelassistant.domain.predictive.PredictionConfidence
import com.navrot.aifuelassistant.domain.predictive.TripCostPrediction
import java.util.Locale
import javax.inject.Inject

class PredictTripFuelCostUseCase @Inject constructor() {

    operator fun invoke(
        distanceKm: Double?,
        consumptionPrediction: ConsumptionPrediction?,
        expectedPricePerLiter: Double?
    ): TripCostPrediction {
        if (distanceKm == null || distanceKm <= 0.0) {
            return TripCostPrediction(
                distanceKm = distanceKm ?: 0.0,
                predictedLiters = null,
                predictedCost = null,
                expectedPricePerLiter = expectedPricePerLiter,
                confidence = PredictionConfidence.UNKNOWN,
                explanation = "Дистанция поездки не указана."
            )
        }

        val consumption = consumptionPrediction?.predictedConsumption
        if (consumption == null || consumption <= 0.0 || consumptionPrediction.confidence == PredictionConfidence.UNKNOWN) {
            return TripCostPrediction(
                distanceKm = distanceKm,
                predictedLiters = null,
                predictedCost = null,
                expectedPricePerLiter = expectedPricePerLiter,
                confidence = PredictionConfidence.UNKNOWN,
                explanation = "Недостаточно данных о расходе топлива."
            )
        }

        val fuelLiters = (distanceKm * consumption) / 100.0

        if (expectedPricePerLiter == null || expectedPricePerLiter <= 0.0) {
            return TripCostPrediction(
                distanceKm = distanceKm,
                predictedLiters = fuelLiters,
                predictedCost = null,
                expectedPricePerLiter = null,
                confidence = consumptionPrediction.confidence,
                explanation = String.format(Locale.US, "Поездка %.1f км ≈ %.1f л (цена топлива неизвестна)", distanceKm, fuelLiters)
            )
        }

        val totalCost = fuelLiters * expectedPricePerLiter
        val explanation = String.format(
            Locale.US,
            "Поездка %.1f км ≈ %.1f л ≈ %.0f ₽",
            distanceKm,
            fuelLiters,
            totalCost
        )

        return TripCostPrediction(
            distanceKm = distanceKm,
            predictedLiters = fuelLiters,
            predictedCost = totalCost,
            expectedPricePerLiter = expectedPricePerLiter,
            confidence = consumptionPrediction.confidence,
            explanation = explanation
        )
    }
}
