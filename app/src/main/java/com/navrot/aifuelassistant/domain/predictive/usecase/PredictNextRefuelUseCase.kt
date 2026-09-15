package com.navrot.aifuelassistant.domain.predictive.usecase

import com.navrot.aifuelassistant.domain.personal.PersonalFuelEvent
import com.navrot.aifuelassistant.domain.predictive.LearningPolicy
import com.navrot.aifuelassistant.domain.predictive.NextRefuelPrediction
import com.navrot.aifuelassistant.domain.predictive.PersonalConsumptionModel
import com.navrot.aifuelassistant.domain.predictive.PersonalFuelFeatureExtractor
import com.navrot.aifuelassistant.domain.predictive.PredictionConfidence
import java.util.Locale
import javax.inject.Inject

class PredictNextRefuelUseCase @Inject constructor(
    private val policy: LearningPolicy
) {
    constructor() : this(LearningPolicy.DEFAULT)

    operator fun invoke(
        vehicleId: Long,
        fuelType: String,
        events: List<PersonalFuelEvent>,
        currentOdometerKm: Double? = null
    ): NextRefuelPrediction {
        val features = PersonalFuelFeatureExtractor.extractFeatures(
            vehicleId = vehicleId,
            fuelType = fuelType,
            events = events,
            currentOdometerKm = currentOdometerKm
        )

        val intervalKm = features.refuelIntervalKm
        val distanceDone = features.distanceSinceRefuel

        val vehicleEvents = events.filter { it.vehicleId == vehicleId }
        val confidence = PersonalConsumptionModel.evaluateConfidence(vehicleEvents.size, policy)

        if (intervalKm == null || intervalKm <= 0.0 || confidence == PredictionConfidence.UNKNOWN) {
            return NextRefuelPrediction(
                predictedKmRemaining = null,
                predictedDaysRemaining = features.refuelIntervalDays,
                confidence = PredictionConfidence.UNKNOWN,
                explanation = "До заправки: недостаточно данных истории заправок."
            )
        }

        val remainingKm = if (distanceDone != null) {
            (intervalKm - distanceDone).coerceAtLeast(0.0)
        } else {
            intervalKm
        }

        val explanation = String.format(
            Locale.US,
            "До следующей заправки: примерно %.0f км",
            remainingKm
        )

        return NextRefuelPrediction(
            predictedKmRemaining = remainingKm,
            predictedDaysRemaining = features.refuelIntervalDays,
            confidence = confidence,
            explanation = explanation
        )
    }
}
