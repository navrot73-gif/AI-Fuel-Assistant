package com.navrot.aifuelassistant.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.navrot.aifuelassistant.data.database.entity.PersonalModelMetadataEntity
import com.navrot.aifuelassistant.data.database.entity.PredictionOutcomeEntity
import com.navrot.aifuelassistant.data.database.entity.RecommendationFeedbackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PredictionDao {

    @Query("SELECT * FROM personal_model_metadata WHERE vehicleId = :vehicleId")
    suspend fun getModelMetadata(vehicleId: Long): PersonalModelMetadataEntity?

    @Query("SELECT * FROM personal_model_metadata WHERE vehicleId = :vehicleId")
    fun getModelMetadataFlow(vehicleId: Long): Flow<PersonalModelMetadataEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModelMetadata(metadata: PersonalModelMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutcome(outcome: PredictionOutcomeEntity)

    @Query("SELECT * FROM prediction_outcomes WHERE vehicleId = :vehicleId ORDER BY timestamp DESC")
    suspend fun getOutcomesForVehicle(vehicleId: Long): List<PredictionOutcomeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedback(feedback: RecommendationFeedbackEntity)

    @Query("SELECT * FROM recommendation_feedbacks ORDER BY timestamp DESC")
    suspend fun getAllFeedbacks(): List<RecommendationFeedbackEntity>

    @Query("SELECT * FROM recommendation_feedbacks WHERE recommendationId = :recommendationId ORDER BY timestamp DESC")
    suspend fun getFeedbacksForRecommendation(recommendationId: String): List<RecommendationFeedbackEntity>

    @Query("DELETE FROM personal_model_metadata WHERE vehicleId = :vehicleId")
    suspend fun deleteModelMetadata(vehicleId: Long)

    @Query("DELETE FROM prediction_outcomes WHERE vehicleId = :vehicleId")
    suspend fun deleteOutcomes(vehicleId: Long)
}
