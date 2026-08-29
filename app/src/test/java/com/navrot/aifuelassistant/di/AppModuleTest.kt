package com.navrot.aifuelassistant.di

import com.navrot.aifuelassistant.ai.router.AiRouter
import com.navrot.aifuelassistant.data.FuelRecordRepository
import com.navrot.aifuelassistant.data.GasStationRepositoryInterface
import com.navrot.aifuelassistant.data.VehicleRepository
import com.navrot.aifuelassistant.data.database.AppDatabase
import com.navrot.aifuelassistant.data.database.dao.FuelRecordDao
import com.navrot.aifuelassistant.data.database.dao.VehicleDao
import com.navrot.aifuelassistant.data.datasource.StationCache
import com.navrot.aifuelassistant.data.datasource.StationFilterAndSorter
import com.navrot.aifuelassistant.data.datasource.StationJsonParser
import com.navrot.aifuelassistant.data.datasource.StationLoader
import com.navrot.aifuelassistant.data.datasource.StationPriceApplier
import com.navrot.aifuelassistant.geo.GeocodingProvider
import com.navrot.aifuelassistant.network.FuelApi
import com.navrot.aifuelassistant.network.NetworkMonitor
import com.navrot.aifuelassistant.ui.map.TileWarmupService
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject

@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class AppModuleTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var vehicleDao: VehicleDao

    @Inject
    lateinit var fuelRecordDao: FuelRecordDao

    @Inject
    lateinit var vehicleRepository: VehicleRepository

    @Inject
    lateinit var fuelRecordRepository: FuelRecordRepository

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var stationJsonParser: StationJsonParser

    @Inject
    lateinit var stationCache: StationCache

    @Inject
    lateinit var stationLoader: StationLoader

    @Inject
    lateinit var stationPriceApplier: StationPriceApplier

    @Inject
    lateinit var stationFilterAndSorter: StationFilterAndSorter

    @Inject
    lateinit var gasStationRepository: GasStationRepositoryInterface

    @Inject
    lateinit var aiRouter: AiRouter

    @Inject
    lateinit var tileWarmupService: TileWarmupService

    @Inject
    lateinit var geocodingProvider: GeocodingProvider

    @Inject
    lateinit var fuelApi: FuelApi

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun testAppModuleProvidesAllDependencies() {
        assertNotNull(database)
        assertNotNull(vehicleDao)
        assertNotNull(fuelRecordDao)
        assertNotNull(vehicleRepository)
        assertNotNull(fuelRecordRepository)
        assertNotNull(okHttpClient)
        assertNotNull(stationJsonParser)
        assertNotNull(stationCache)
        assertNotNull(stationLoader)
        assertNotNull(stationPriceApplier)
        assertNotNull(stationFilterAndSorter)
        assertNotNull(gasStationRepository)
        assertNotNull(aiRouter)
        assertNotNull(tileWarmupService)
        assertNotNull(geocodingProvider)
        assertNotNull(fuelApi)
        assertNotNull(networkMonitor)
        assertNotNull(appScope)
    }
}
