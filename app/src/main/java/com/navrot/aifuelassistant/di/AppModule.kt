package com.navrot.aifuelassistant.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.navrot.aifuelassistant.ai.AiRouterFactory
import com.navrot.aifuelassistant.ai.router.AiRouter
import com.navrot.aifuelassistant.data.FuelRecordRepository
import com.navrot.aifuelassistant.data.FuelRecordRepositoryImpl
import com.navrot.aifuelassistant.data.GasStationRepository
import com.navrot.aifuelassistant.data.VehicleRepository
import com.navrot.aifuelassistant.data.VehicleRepositoryImpl
import com.navrot.aifuelassistant.data.database.AppDatabase
import com.navrot.aifuelassistant.data.database.dao.FuelRecordDao
import com.navrot.aifuelassistant.data.database.dao.VehicleDao
import com.navrot.aifuelassistant.geo.NominatimGeocodingProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Миграция 2 -> 3: добавление ForeignKey к fuel_records.vehicleId.
     * Без FK (версия 2) существующие данные остаются, но с этого момента
     * новые записи будут ссылаться на существующие автомобили.
     */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // SQLite не поддерживает ALTER TABLE ADD CONSTRAINT FOREIGN KEY.
            // Создаём новую таблицу с FK, копируем данные, удаляем старую, переименовываем.
            database.execSQL(""
                + "CREATE TABLE IF NOT EXISTS fuel_records_new ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                + "vehicleId INTEGER NOT NULL, "
                + "date INTEGER NOT NULL, "
                + "mileage REAL NOT NULL, "
                + "fuelAmount REAL NOT NULL, "
                + "pricePerLiter REAL NOT NULL, "
                + "totalCost REAL NOT NULL, "
                + "fuelType TEXT NOT NULL, "
                + "stationName TEXT NOT NULL, "
                + "notes TEXT NOT NULL, "
                + "latitude REAL, "
                + "longitude REAL, "
                + "FOREIGN KEY(vehicleId) REFERENCES vehicles(id) ON DELETE CASCADE"
                + ")"")
            database.execSQL(""
                + "INSERT INTO fuel_records_new (id, vehicleId, date, mileage, fuelAmount, "
                + "pricePerLiter, totalCost, fuelType, stationName, notes, latitude, longitude) "
                + "SELECT id, vehicleId, date, mileage, fuelAmount, pricePerLiter, "
                + "totalCost, fuelType, stationName, notes, latitude, longitude "
                + "FROM fuel_records"")
            database.execSQL("DROP TABLE fuel_records")
            database.execSQL("ALTER TABLE fuel_records_new RENAME TO fuel_records")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "ai_fuel_assistant_db"
        )
            .addMigrations(MIGRATION_2_3)
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
    fun provideGasStationRepository(@ApplicationContext context: Context): GasStationRepository {
        return GasStationRepository(context)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideNominatimGeocodingProvider(okHttpClient: OkHttpClient): NominatimGeocodingProvider {
        return NominatimGeocodingProvider(httpClient = okHttpClient)
    }

    @Provides
    @Singleton
    fun provideAiRouter(): AiRouter = AiRouterFactory.create()
}
