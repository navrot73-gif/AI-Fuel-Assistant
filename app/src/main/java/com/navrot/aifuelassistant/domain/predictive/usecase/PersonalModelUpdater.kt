package com.navrot.aifuelassistant.domain.predictive.usecase

import com.navrot.aifuelassistant.domain.predictive.LearningPolicy
import com.navrot.aifuelassistant.domain.predictive.PersonalFuelFeatures
import com.navrot.aifuelassistant.domain.predictive.PersonalModelMetadata
import com.navrot.aifuelassistant.domain.predictive.PredictionOutcome
import com.navrot.aifuelassistant.domain.predictive.StatisticalPredictionModel
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

class PersonalModelUpdater @Inject constructor(
    private val policy: LearningPolicy
) {
    constructor() : this(LearningPolicy.DEFAULT)

    data class UpdateResult(
        val updatedMetadata: PersonalModelMetadata,
        val outcome: PredictionOutcome
    )

    fun evaluateAndUpdate(
        vehicleId: Long,
        features: PersonalFuelFeatures,
        predictedConsumption: Double,
        actualConsumption: Double,
        currentMetadata: PersonalModelMetadata? = null,
        sampleCount: Int = 0
    ): UpdateResult {
        val predictionId = UUID.randomUUID().toString()
        val absoluteError = abs(actualConsumption - predictedConsumption)
        val relativeError = if (actualConsumption > 0) absoluteError / actualConsumption else 0.0

        val outcome = PredictionOutcome(
            predictionId = predictionId,
            vehicleId = vehicleId,
            predictedValue = predictedConsumption,
            actualValue = actualConsumption,
            absoluteError = absoluteError,
            relativeError = relativeError,
            timestamp = System.currentTimeMillis()
        )

        val metadata = currentMetadata ?: PersonalModelMetadata(vehicleId = vehicleId)

        // Minimum data gate: if samples < minimumSamples, do not perform self-learning update
        if (sampleCount < policy.minimumSamples) {
            return UpdateResult(
                updatedMetadata = metadata.copy(lastUpdatedAt = System.currentTimeMillis()),
                outcome = outcome
            )
        }

        val currentModel = StatisticalPredictionModel(
            biasAdjustment = metadata.biasAdjustment,
            policy = policy
        )

        // Incremental model update
        val updatedModel = currentModel.update(features, actualConsumption)

        val newMetadata = metadata.copy(
            trainedSamples = metadata.trainedSamples + 1,
            biasAdjustment = updatedModel.biasAdjustment,
            lastUpdatedAt = System.currentTimeMillis()
        )

        return UpdateResult(
            updatedMetadata = newMetadata,
            outcome = outcome
        )
    }
}
