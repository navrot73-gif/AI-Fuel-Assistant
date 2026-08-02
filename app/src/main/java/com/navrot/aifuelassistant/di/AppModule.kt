package com.navrot.aifuelassistant.di

import android.content.Context
import com.navrot.aifuelassistant.data.FuelRecordRepository
import com.navrot.aifuelassistant.data.FuelRecordRepositoryImpl
import com.navrot.aifuelassistant.data.VehicleRepository
import com.navrot.aifuelassistant.data.VehicleRepositoryImpl
import com.navrot.aifuelassistant.data.database.AppDatabase
import com.navrot.aifuelassistant.data.database.dao.FuelRecordDao
import com.navrot.aifuelassistant.data.database.dao.VehicleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    fun provideVehicleDao(db: AppDatabase): VehicleDao = db.vehicleDao()

    @Provides
    fun provideFuelRecordDao(db: AppDatabase): FuelRecordDao = db.fuelRecordDao()

    @Provides
    @Singleton
    fun provideVehicleRepository(dao: VehicleDao): VehicleRepository {
        return VehicleRepositoryImpl(dao)
    }

    @Provides
    @Singleton
    fun provideFuelRecordRepository(dao: FuelRecordDao): FuelRecordRepository {
        return FuelRecordRepositoryImpl(dao)
    }
}