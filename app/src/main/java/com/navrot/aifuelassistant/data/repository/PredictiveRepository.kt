package com.navrot.aifuelassistant.data.repository

import com.navrot.aifuelassistant.data.database.dao.PredictionDao
import com.navrot.aifuelassistant.data.database.entity.PersonalModelMetadataEntity
import com.navrot.aifuelassistant.data.database.entity.PredictionOutcomeEntity
import com.navrot.aifuelassistant.data.database.entity.RecommendationFeedbackEntity
import com.navrot.aifuelassistant.domain.predictive.EventSource
import com.navrot.aifuelassistant.domain.predictive.FeedbackSignal
import com.navrot.aifuelassistant.domain.predictive.PersonalModelMetadata
import com.navrot.aifuelassistant.domain.predictive.PredictionOutcome
import com.navrot.aifuelassistant.domain.predictive.RecommendationFeedback
import com.navrot.aifuelassistant.domain.predictive.UserAction
import com.navrot.aifuelassistant.domain.predictive.UserOutcome
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
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
            signal = feedback.signal.name,
            fuelType = feedback.fuelType,
            action = feedback.action.name,
            outcome = feedback.outcome.name,
            predictedAvailability = feedback.predictedAvailability?.name,
            actualAvailability = feedback.actualAvailability?.name,
            predictedPrice = feedback.predictedPrice,
            actualPrice = feedback.actualPrice,
            predictedQueue = feedback.predictedQueue,
            actualQueue = feedback.actualQueue,
            dataConfidence = feedback.dataConfidence,
            userConfirmed = feedback.userConfirmed,
            source = feedback.source.name,
            notes = feedback.notes
        )
        dao.insertFeedback(entity)
    }

    suspend fun getAllFeedbacks(): List<RecommendationFeedback> {
        return dao.getAllFeedbacks().map { entityToFeedback(it) }
    }

    suspend fun getFeedbacksForRecommendation(recommendationId: String): List<RecommendationFeedback> {
        return dao.getFeedbacksForRecommendation(recommendationId).map { entityToFeedback(it) }
    }

    suspend fun resetModel(vehicleId: Long) {
        dao.deleteModelMetadata(vehicleId)
        dao.deleteOutcomes(vehicleId)
    }

    private fun entityToFeedback(entity: RecommendationFeedbackEntity): RecommendationFeedback {
        val signal = try {
            FeedbackSignal.valueOf(entity.signal)
        } catch (e: Exception) {
            FeedbackSignal.UNKNOWN
        }
        val action = try {
            UserAction.valueOf(entity.action)
        } catch (e: Exception) {
            if (entity.refuelCompleted) UserAction.REFUELLED
            else if (entity.routeStarted) UserAction.ROUTE_STARTED
            else UserAction.VIEWED
        }
        val outcome = try {
            UserOutcome.valueOf(entity.outcome)
        } catch (e: Exception) {
            if (entity.refuelCompleted) UserOutcome.SUCCESS else UserOutcome.UNKNOWN
        }
        val predAvail = entity.predictedAvailability?.let {
            try { FuelAvailabilityStatus.valueOf(it) } catch (e: Exception) { null }
        }
        val actAvail = entity.actualAvailability?.let {
            try { FuelAvailabilityStatus.valueOf(it) } catch (e: Exception) { null }
        }
        val source = try {
            EventSource.valueOf(entity.source)
        } catch (e: Exception) {
            EventSource.USER_CONFIRMED
        }

        return RecommendationFeedback(
            id = entity.id,
            recommendationId = entity.recommendationId,
            recommendedStationId = entity.recommendedStationId,
            chosenStationId = entity.chosenStationId,
            timestamp = entity.timestamp,
            routeStarted = entity.routeStarted,
            routeCompleted = entity.routeCompleted,
            refuelCompleted = entity.refuelCompleted,
            signal = signal,
            fuelType = entity.fuelType,
            action = action,
            outcome = outcome,
            predictedAvailability = predAvail,
            actualAvailability = actAvail,
            predictedPrice = entity.predictedPrice,
            actualPrice = entity.actualPrice,
            predictedQueue = entity.predictedQueue,
            actualQueue = entity.actualQueue,
            dataConfidence = entity.dataConfidence,
            userConfirmed = entity.userConfirmed,
            source = source,
            notes = entity.notes
        )
    }
}
