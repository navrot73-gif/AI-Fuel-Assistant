package com.navrot.aifuelassistant.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.navrot.aifuelassistant.data.database.dao.FuelRecordDao
import com.navrot.aifuelassistant.data.database.dao.PredictionDao
import com.navrot.aifuelassistant.data.database.dao.VehicleDao
import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import com.navrot.aifuelassistant.data.database.entity.PersonalModelMetadataEntity
import com.navrot.aifuelassistant.data.database.entity.PredictionOutcomeEntity
import com.navrot.aifuelassistant.data.database.entity.RecommendationFeedbackEntity
import com.navrot.aifuelassistant.data.database.entity.VehicleEntity

@Database(
    entities = [
        VehicleEntity::class,
        FuelRecordEntity::class,
        PredictionOutcomeEntity::class,
        RecommendationFeedbackEntity::class,
        PersonalModelMetadataEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun fuelRecordDao(): FuelRecordDao
    abstract fun predictionDao(): PredictionDao
}
