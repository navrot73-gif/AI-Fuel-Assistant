package com.navrot.aifuelassistant.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.navrot.aifuelassistant.ai.AiRouterFactory
import com.navrot.aifuelassistant.ai.router.AiRouter
import com.navrot.aifuelassistant.data.FuelRecordRepository
import com.navrot.aifuelassistant.data.FuelRecordRepositoryImpl
import com.navrot.aifuelassistant.data.GasStationRepository
import com.navrot.aifuelassistant.data.GasStationRepositoryInterface
import com.navrot.aifuelassistant.data.UserPriceRepository
import com.navrot.aifuelassistant.data.VehicleRepository
import com.navrot.aifuelassistant.data.VehicleRepositoryImpl
import com.navrot.aifuelassistant.data.providers.BenzonavtProvider
import com.navrot.aifuelassistant.data.database.AppDatabase
import com.navrot.aifuelassistant.data.database.DatabaseMigrations
import com.navrot.aifuelassistant.data.database.dao.FuelRecordDao
import com.navrot.aifuelassistant.data.database.dao.VehicleDao
import com.navrot.aifuelassistant.domain.usecase.GetBestStationsUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import com.navrot.aifuelassistant.network.RetryInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "ai_fuel_assistant_db"
        )
            .addMigrations(DatabaseMigrations.MIGRATION_2_3)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                    Log.w("AppDatabase", "⚠️ Деструктивная миграция БД! Все данные пользователя удалены.")
                }
            })
            .fallbackToDestructiveMigrationOnDowngrade()
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
    fun provideVehicleRepository(dao: VehicleDao): VehicleRepository {
        return VehicleRepositoryImpl(dao)
    }

    @Provides
    @Singleton
    fun provideFuelRecordRepository(dao: FuelRecordDao): FuelRecordRepository {
        return FuelRecordRepositoryImpl(dao)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(RetryInterceptor())
            .build()
    }

    // UserPriceRepository не нуждается в отдельном @Provides — у неё уже есть
    // @Inject constructor, Hilt резолвит её автоматически.

    @Provides
    @Singleton
    fun provideGasStationRepository(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        userPriceRepository: UserPriceRepository,
        getBestStationsUseCase: GetBestStationsUseCase,
        benzonavtProvider: BenzonavtProvider
    ): GasStationRepositoryInterface {
        return GasStationRepository(
            context, okHttpClient, userPriceRepository, getBestStationsUseCase, benzonavtProvider
        )
    }

    @Provides
    @Singleton
    fun provideAiRouter(okHttpClient: OkHttpClient): AiRouter = AiRouterFactory.create(okHttpClient)
}