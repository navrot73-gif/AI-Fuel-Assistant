package com.navrot.aifuelassistant.domain.predictive.usecase

import com.navrot.aifuelassistant.domain.personal.PersonalConsumptionCalculator
import com.navrot.aifuelassistant.domain.personal.PersonalFuelEvent
import com.navrot.aifuelassistant.domain.predictive.ConsumptionPrediction
import com.navrot.aifuelassistant.domain.predictive.LearningPolicy
import com.navrot.aifuelassistant.domain.predictive.PersonalConsumptionModel
import com.navrot.aifuelassistant.domain.predictive.PersonalFuelFeatureExtractor
import com.navrot.aifuelassistant.domain.predictive.PersonalModelMetadata
import com.navrot.aifuelassistant.domain.predictive.PredictionConfidence
import com.navrot.aifuelassistant.domain.predictive.StatisticalPredictionModel
import java.util.Locale
import javax.inject.Inject

class PredictConsumptionUseCase @Inject constructor(
    private val policy: LearningPolicy
) {
    constructor() : this(LearningPolicy.DEFAULT)

    operator fun invoke(
        vehicleId: Long,
        fuelType: String,
        events: List<PersonalFuelEvent>,
        metadata: PersonalModelMetadata? = null,
        currentOdometerKm: Double? = null
    ): ConsumptionPrediction {
        val vehicleEvents = events.filter { it.vehicleId == vehicleId }
        val rawConsumptions = PersonalConsumptionCalculator.calculateConsumptions(vehicleEvents)

        if (rawConsumptions.size < policy.minimumSamples) {
            return ConsumptionPrediction(
                predictedConsumption = null,
                confidence = PredictionConfidence.UNKNOWN,
                sampleCount = rawConsumptions.size,
                basedOn = "Прогноз пока недоступен. Добавьте ещё несколько заправок."
            )
        }

        val baselineCalc = PersonalConsumptionModel.calculateBaseline(rawConsumptions, policy)
        val baseline = baselineCalc.baselineConsumption

        if (baseline == null || baselineCalc.confidence == PredictionConfidence.UNKNOWN) {
            return ConsumptionPrediction(
                predictedConsumption = null,
                confidence = PredictionConfidence.UNKNOWN,
                sampleCount = rawConsumptions.size,
                basedOn = "Прогноз пока недоступен. Добавьте ещё несколько заправок."
            )
        }

        val features = PersonalFuelFeatureExtractor.extractFeatures(
            vehicleId = vehicleId,
            fuelType = fuelType,
            events = vehicleEvents,
            currentOdometerKm = currentOdometerKm
        )

        val model = StatisticalPredictionModel(
            biasAdjustment = metadata?.biasAdjustment ?: 0.0,
            policy = policy
        )

        val predicted = (model.predict(features) ?: baseline).coerceAtLeast(1.0)
        val delta = predicted - baseline

        val explanation = buildExplanation(
            sampleCount = vehicleEvents.size,
            validCount = baselineCalc.validCount,
            baseline = baseline,
            outlierCount = baselineCalc.outlierCount
        )

        return ConsumptionPrediction(
            predictedConsumption = predicted,
            confidence = baselineCalc.confidence,
            sampleCount = baselineCalc.validCount,
            basedOn = explanation,
            baselineConsumption = baseline,
            deltaFromBaseline = delta
        )
    }

    private fun buildExplanation(
        sampleCount: Int,
        validCount: Int,
        baseline: Double,
        outlierCount: Int
    ): String {
        val formattedAvg = String.format(Locale.US, "%.1f", baseline)
        val parts = mutableListOf<String>()
        parts.add("$validCount последних заправках")
        parts.add("среднем расходе $formattedAvg л/100 км")
        if (outlierCount > 0) {
            val word = if (outlierCount == 1) "исключена 1 аномалия" else "исключено $outlierCount аномальных измерений"
            parts.add(word)
        }
        return "Прогноз основан на: " + parts.joinToString("; ") + "."
    }
}
