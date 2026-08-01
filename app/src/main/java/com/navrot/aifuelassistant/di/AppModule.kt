package com.navrot.aifuelassistant.di

import android.content.Context
import androidx.room.Room
import com.navrot.aifuelassistant.ai.AiRouterFactory
import com.navrot.aifuelassistant.ai.router.AiRouter
import com.navrot.aifuelassistant.data.FuelRecordRepository
import com.navrot.aifuelassistant.data.FuelRecordRepositoryImpl
import com.navrot.aifuelassistant.data.VehicleRepository
import com.navrot.aifuelassistant.data.VehicleRepositoryImpl
import com.navrot.aifuelassistant.data.database.AppDatabase
import com.navrot.aifuelassistant.data.database.dao.FuelRecordDao
import com.navrot.aifuelassistant.data.database.dao.VehicleDao
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindVehicleRepository(impl: VehicleRepositoryImpl): VehicleRepository

    @Binds
    @Singleton
    abstract fun bindFuelRecordRepository(impl: FuelRecordRepositoryImpl): FuelRecordRepository

    companion object {

        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "ai_fuel_assistant_db"
            )
                .fallbackToDestructiveMigration()
                .build()
        }

        @Provides
        fun provideVehicleDao(database: AppDatabase): VehicleDao =
            database.vehicleDao()

        @Provides
        fun provideFuelRecordDao(database: AppDatabase): FuelRecordDao =
            database.fuelRecordDao()

        @Provides
        @Singleton
        fun provideAiRouter(): AiRouter = AiRouterFactory.create()
    }
}