package com.navrot.aifuelassistant.di

import android.content.Context
import timber.log.Timber
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
import com.navrot.aifuelassistant.data.datasource.StationCache
import com.navrot.aifuelassistant.data.datasource.StationCacheImpl
import com.navrot.aifuelassistant.data.datasource.StationFilterAndSorter
import com.navrot.aifuelassistant.data.datasource.StationFilterAndSorterImpl
import com.navrot.aifuelassistant.data.datasource.StationJsonParser
import com.navrot.aifuelassistant.data.datasource.StationJsonParserImpl
import com.navrot.aifuelassistant.data.datasource.StationLoader
import com.navrot.aifuelassistant.data.datasource.StationLoaderImpl
import com.navrot.aifuelassistant.data.datasource.StationPriceApplier
import com.navrot.aifuelassistant.data.datasource.StationPriceApplierImpl
import com.navrot.aifuelassistant.data.providers.BenzonavtProvider
import com.navrot.aifuelassistant.data.database.AppDatabase
import com.navrot.aifuelassistant.data.database.DatabaseMigrations
import com.navrot.aifuelassistant.data.database.dao.FuelRecordDao
import com.navrot.aifuelassistant.data.database.dao.VehicleDao
import com.navrot.aifuelassistant.domain.usecase.GetBestStationsUseCase
import com.navrot.aifuelassistant.geo.GeocodingProvider
import com.navrot.aifuelassistant.geo.NominatimGeocodingProvider
import com.navrot.aifuelassistant.network.NetworkMonitor
import com.navrot.aifuelassistant.network.NetworkMonitorImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import com.navrot.aifuelassistant.network.RetryInterceptor
import com.navrot.aifuelassistant.ui.map.TileWarmupService
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Квалификатор для [CoroutineScope], привязанного к жизненному циклу приложения.
 *
 * Используется в репозиториях и других @Singleton-компонентах, которым нужно
 * запускать фоновые корутины, не привязанные к конкретной ViewModel/Activity.
 *
 * Важно: такой scope должен быть создан с [SupervisorJob] (чтобы одна упавшая
 * корутина не отменяла сестер) и [CoroutineExceptionHandler] (чтобы неупавшая
 * ошибка не роняла процесс).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        val handler = CoroutineExceptionHandler { _, throwable ->
            Timber.tag("ApplicationScope").e(throwable, "Uncaught coroutine exception")
        }
        return CoroutineScope(SupervisorJob() + Dispatchers.IO + handler)
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "ai_fuel_assistant_db"
        )
            // Все явные миграции между версиями схемы.
            .addMigrations(*DatabaseMigrations.ALL)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                    Timber.tag("AppDatabase").w("⚠️ Деструктивная миграция БД! Все данные пользователя удалены.")
                }
            })
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
    fun provideStationJsonParser(): StationJsonParser {
        return StationJsonParserImpl()
    }

    @Provides
    @Singleton
    fun provideStationCache(
        @ApplicationContext context: Context,
        jsonParser: StationJsonParser
    ): StationCache {
        return StationCacheImpl(context, jsonParser)
    }

    @Provides
    @Singleton
    fun provideStationLoader(
        okHttpClient: OkHttpClient,
        stationCache: StationCache,
        jsonParser: StationJsonParser,
        @ApplicationContext context: Context
    ): StationLoader {
        return StationLoaderImpl(okHttpClient, stationCache, jsonParser, context)
    }

    @Provides
    @Singleton
    fun provideStationPriceApplier(
        userPriceRepository: UserPriceRepository,
        benzonavtProvider: BenzonavtProvider
    ): StationPriceApplier {
        return StationPriceApplierImpl(userPriceRepository, benzonavtProvider)
    }

    @Provides
    @Singleton
    fun provideStationFilterAndSorter(): StationFilterAndSorter {
        return StationFilterAndSorterImpl()
    }

    @Provides
    @Singleton
    fun provideGasStationRepository(
        stationLoader: StationLoader,
        stationCache: StationCache,
        stationPriceApplier: StationPriceApplier,
        stationFilterAndSorter: StationFilterAndSorter,
        userPriceRepository: UserPriceRepository,
        benzonavtProvider: BenzonavtProvider,
        getBestStationsUseCase: GetBestStationsUseCase,
        @ApplicationScope appScope: CoroutineScope
    ): GasStationRepositoryInterface {
        return GasStationRepository(
            stationLoader = stationLoader,
            stationCache = stationCache,
            stationPriceApplier = stationPriceApplier,
            stationFilterAndSorter = stationFilterAndSorter,
            userPrices = userPriceRepository,
            benzonavtProvider = benzonavtProvider,
            getBestStationsUseCase = getBestStationsUseCase,
            appScope = appScope
        )
    }

    @Provides
    @Singleton
    fun provideAiRouter(okHttpClient: OkHttpClient): AiRouter = AiRouterFactory.create(okHttpClient)

    @Provides
    @Singleton
    fun provideTileWarmupService(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): TileWarmupService = TileWarmupService(context, okHttpClient)

    @Provides
    @Singleton
    fun provideGeocodingProvider(okHttpClient: OkHttpClient): GeocodingProvider =
        NominatimGeocodingProvider(httpClient = okHttpClient)

    @Provides
    @Singleton
    fun provideFuelApi(okHttpClient: OkHttpClient): com.navrot.aifuelassistant.network.FuelApi =
        com.navrot.aifuelassistant.network.FuelApiImpl(okHttpClient)

    @Provides
    @Singleton
    fun provideNetworkMonitor(@ApplicationContext context: Context): NetworkMonitor =
        NetworkMonitorImpl(context)
}
