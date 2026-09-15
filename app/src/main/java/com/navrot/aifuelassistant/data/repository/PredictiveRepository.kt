package com.navrot.aifuelassistant.data.repository

import com.navrot.aifuelassistant.data.database.dao.PredictionDao
import com.navrot.aifuelassistant.data.database.entity.PersonalModelMetadataEntity
import com.navrot.aifuelassistant.data.database.entity.PredictionOutcomeEntity
import com.navrot.aifuelassistant.data.database.entity.RecommendationFeedbackEntity
import com.navrot.aifuelassistant.domain.predictive.PersonalModelMetadata
import com.navrot.aifuelassistant.domain.predictive.PredictionOutcome
import com.navrot.aifuelassistant.domain.predictive.RecommendationFeedback
import com.navrot.aifuelassistant.domain.predictive.FeedbackSignal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PredictiveRepository @Inject constructor(
    private val dao: PredictionDao
) {

    suspend fun getModelMetadata(vehicleId: Long): PersonalModelMetadata? {
        val entity = dao.getModelMetadata(vehicleId) ?: return null
        return PersonalModelMetadata(
            vehicleId = entity.vehicleId,
            modelVersion = entity.modelVersion,
            trainedSamples = entity.trainedSamples,
            biasAdjustment = entity.biasAdjustment,
            lastUpdatedAt = entity.lastUpdatedAt
        )
    }

    fun getModelMetadataFlow(vehicleId: Long): Flow<PersonalModelMetadata?> {
        return dao.getModelMetadataFlow(vehicleId).map { entity ->
            entity?.let {
                PersonalModelMetadata(
                    vehicleId = it.vehicleId,
                    modelVersion = it.modelVersion,
                    trainedSamples = it.trainedSamples,
                    biasAdjustment = it.biasAdjustment,
                    lastUpdatedAt = it.lastUpdatedAt
                )
            }
        }
    }

    suspend fun saveModelMetadata(metadata: PersonalModelMetadata) {
        val entity = PersonalModelMetadataEntity(
            vehicleId = metadata.vehicleId,
            modelVersion = metadata.modelVersion,
            trainedSamples = metadata.trainedSamples,
            biasAdjustment = metadata.biasAdjustment,
            lastUpdatedAt = metadata.lastUpdatedAt
        )
        dao.upsertModelMetadata(entity)
    }

    suspend fun recordOutcome(outcome: PredictionOutcome) {
        val entity = PredictionOutcomeEntity(
            predictionId = outcome.predictionId,
            vehicleId = outcome.vehicleId,
            predictedValue = outcome.predictedValue,
            actualValue = outcome.actualValue,
            absoluteError = outcome.absoluteError,
            relativeError = outcome.relativeError,
            timestamp = outcome.timestamp
        )
        dao.insertOutcome(entity)
    }

    suspend fun getOutcomesForVehicle(vehicleId: Long): List<PredictionOutcome> {
        return dao.getOutcomesForVehicle(vehicleId).map { entity ->
            PredictionOutcome(
                predictionId = entity.predictionId,
                vehicleId = entity.vehicleId,
                predictedValue = entity.predictedValue,
                actualValue = entity.actualValue,
                absoluteError = entity.absoluteError,
                relativeError = entity.relativeError,
                timestamp = entity.timestamp
            )
        }
    }

    suspend fun recordFeedback(feedback: RecommendationFeedback) {
        val entity = RecommendationFeedbackEntity(
            id = feedback.id,
            recommendationId = feedback.recommendationId,
            recommendedStationId = feedback.recommendedStationId,
            chosenStationId = feedback.chosenStationId,
            timestamp = feedback.timestamp,
            routeStarted = feedback.routeStarted,
            routeCompleted = feedback.routeCompleted,
            refuelCompleted = feedback.refuelCompleted,
            signal = feedback.signal.name
        )
        dao.insertFeedback(entity)
    }

    suspend fun getAllFeedbacks(): List<RecommendationFeedback> {
        return dao.getAllFeedbacks().map { entity ->
            val signal = try {
                FeedbackSignal.valueOf(entity.signal)
            } catch (e: Exception) {
                FeedbackSignal.UNKNOWN
            }
            RecommendationFeedback(
                id = entity.id,
                recommendationId = entity.recommendationId,
                recommendedStationId = entity.recommendedStationId,
                chosenStationId = entity.chosenStationId,
                timestamp = entity.timestamp,
                routeStarted = entity.routeStarted,
                routeCompleted = entity.routeCompleted,
                refuelCompleted = entity.refuelCompleted,
                signal = signal
            )
        }
    }

    suspend fun resetModel(vehicleId: Long) {
        dao.deleteModelMetadata(vehicleId)
        dao.deleteOutcomes(vehicleId)
    }
}
