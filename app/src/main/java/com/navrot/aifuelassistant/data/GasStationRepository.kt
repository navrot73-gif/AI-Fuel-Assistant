package com.navrot.aifuelassistant.data

import android.content.Context
import com.navrot.aifuelassistant.data.datasource.OverpassFuelProvider
import com.navrot.aifuelassistant.data.datasource.OverpassFuelProviderImpl
import com.navrot.aifuelassistant.data.datasource.StationCache
import com.navrot.aifuelassistant.data.datasource.StationCacheImpl
import com.navrot.aifuelassistant.data.datasource.StationFilterAndSorter
import com.navrot.aifuelassistant.data.datasource.StationFilterAndSorterImpl
import com.navrot.aifuelassistant.data.datasource.StationJsonParserImpl
import com.navrot.aifuelassistant.data.datasource.StationLoader
import com.navrot.aifuelassistant.data.datasource.StationLoaderImpl
import com.navrot.aifuelassistant.data.datasource.StationPriceApplier
import com.navrot.aifuelassistant.data.datasource.StationPriceApplierImpl
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.data.providers.BenzonavtProvider
import com.navrot.aifuelassistant.domain.usecase.GetBestStationsUseCase
import com.navrot.aifuelassistant.geo.GeoUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Singleton

/**
 * Репозиторий АЗС (Фасад над компонентами загрузки, кэша, цен и фильтрации).
 *
 * Делегирует задачи специализированным компонентам:
 *  - [StationLoader]: загрузка данных (remote -> cache -> assets)
 *  - [StationCache]: работа с файл-кэшем
 *  - [StationPriceApplier]: применение цен (user report, Benzonavt)
 *  - [StationFilterAndSorter]: поиск, фильтрация по городу/расстоянию и сортировка
 */
@Singleton
class GasStationRepository constructor(
    private val stationLoader: StationLoader,
    private val stationCache: StationCache,
    private val stationPriceApplier: StationPriceApplier,
    private val stationFilterAndSorter: StationFilterAndSorter,
    private val userPrices: UserPriceRepository,
    private val benzonavtProvider: BenzonavtProvider,
    private val overpassFuelProvider: OverpassFuelProvider,
    private val getBestStationsUseCase: GetBestStationsUseCase,
    private val appScope: CoroutineScope
) : GasStationRepositoryInterface {

    /**
     * Конструктор для обратной совместимости с тестами и местами старого вызова.
     */
    constructor(
        context: Context,
        httpClient: OkHttpClient,
        userPrices: UserPriceRepository,
        getBestStationsUseCase: GetBestStationsUseCase,
        benzonavtProvider: BenzonavtProvider,
        appScope: CoroutineScope,
        overpassFuelProvider: OverpassFuelProvider = OverpassFuelProviderImpl(httpClient)
    ) : this(
        stationLoader = StationLoaderImpl(
            httpClient = httpClient,
            stationCache = StationCacheImpl(context, StationJsonParserImpl()),
            jsonParser = StationJsonParserImpl(),
            context = context
        ),
        stationCache = StationCacheImpl(context, StationJsonParserImpl()),
        stationPriceApplier = StationPriceApplierImpl(userPrices, benzonavtProvider),
        stationFilterAndSorter = StationFilterAndSorterImpl(),
        userPrices = userPrices,
        benzonavtProvider = benzonavtProvider,
        overpassFuelProvider = overpassFuelProvider,
        getBestStationsUseCase = getBestStationsUseCase,
        appScope = appScope
    )

    /**
     * Конструктор для тестов без параметра overpassFuelProvider.
     */
    constructor(
        context: Context,
        httpClient: OkHttpClient,
        userPrices: UserPriceRepository,
        getBestStationsUseCase: GetBestStationsUseCase,
        benzonavtProvider: BenzonavtProvider,
        appScope: CoroutineScope
    ) : this(
        context = context,
        httpClient = httpClient,
        userPrices = userPrices,
        getBestStationsUseCase = getBestStationsUseCase,
        benzonavtProvider = benzonavtProvider,
        appScope = appScope,
        overpassFuelProvider = OverpassFuelProviderImpl(httpClient)
    )

    companion object {
        private const val TAG = "GasStationRepo"
        private const val REFRESH_INTERVAL_MS = 10 * 60 * 1000L
        private const val DEDUPLICATION_RADIUS_KM = 0.1 // 100 meters
    }

    private val loadMutex = Mutex()
    private var cachedStations: List<GasStation>? = null
    private var lastRemoteCheckMs = 0L

    private var priceRefreshJob: kotlinx.coroutines.Job? = null

    /**
     * Deduplicates and merges base stations (static/cached/remote) with Overpass stations.
     * Base stations take precedence. Overpass stations within DEDUPLICATION_RADIUS_KM of any base station are dropped.
     */
    fun mergeStations(baseStations: List<GasStation>, overpassStations: List<GasStation>): List<GasStation> {
        if (overpassStations.isEmpty()) return baseStations
        if (baseStations.isEmpty()) return overpassStations

        val merged = ArrayList<GasStation>(baseStations.size + overpassStations.size)
        merged.addAll(baseStations)

        for (overpass in overpassStations) {
            val isDuplicate = baseStations.any { base ->
                GeoUtils.calculateDistance(base.latitude, base.longitude, overpass.latitude, overpass.longitude) <= DEDUPLICATION_RADIUS_KM
            }
            if (!isDuplicate) {
                merged.add(overpass)
            }
        }
        return merged
    }

    private suspend fun ensureLoaded(): List<GasStation> = loadMutex.withLock {
        cachedStations?.let { return@withLock it }

        val now = System.currentTimeMillis()
        val stations: List<GasStation> =
            if (now - lastRemoteCheckMs >= REFRESH_INTERVAL_MS) {
                lastRemoteCheckMs = now
                stationLoader.loadStations()
            } else {
                stationLoader.loadFromCache() ?: stationLoader.loadFromAssets()
            }

        val withUser = stationPriceApplier.applyUserPrices(stations)
        cachedStations = withUser
        Timber.tag(TAG).d("stations shown (%d), prices pending", withUser.size)

        priceRefreshJob?.cancel()
        priceRefreshJob = appScope.launch {
            try {
                val city = benzonavtProvider.currentCity()
                val benzonavt = benzonavtProvider.fetchCityPrices(city)
                if (benzonavt.isNotEmpty()) {
                    loadMutex.withLock {
                        val swapped = withUser.map { station ->
                            stationPriceApplier.applyBenzonavtToStation(station, benzonavt, city)
                        }
                        cachedStations = swapped
                        Timber.tag(TAG).d("prices swapped from BENZONAVT")
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w("Background price refresh failed: %s", e.message)
            }
        }

        withUser
    }

    override suspend fun refresh(): List<GasStation> = loadMutex.withLock {
        lastRemoteCheckMs = System.currentTimeMillis()
        val stations = stationLoader.loadStations()

        val withUser = stationPriceApplier.applyUserPrices(stations)
        cachedStations = withUser
        Timber.tag(TAG).d("stations shown (%d), prices pending", withUser.size)

        priceRefreshJob?.cancel()
        priceRefreshJob = appScope.launch {
            try {
                val city = benzonavtProvider.currentCity()
                val benzonavt = benzonavtProvider.fetchCityPrices(city)
                if (benzonavt.isNotEmpty()) {
                    loadMutex.withLock {
                        val swapped = withUser.map { station ->
                            stationPriceApplier.applyBenzonavtToStation(station, benzonavt, city)
                        }
                        cachedStations = swapped
                        Timber.tag(TAG).d("prices swapped from BENZONAVT")
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w("Background price refresh failed: %s", e.message)
            }
        }

        withUser
    }

    override suspend fun refreshPrices(): List<GasStation> = loadMutex.withLock {
        val base = cachedStations ?: stationLoader.loadStations()

        val withUser = stationPriceApplier.applyUserPrices(base)
        cachedStations = withUser
        Timber.tag(TAG).d("stations shown (%d), prices pending", withUser.size)

        priceRefreshJob?.cancel()
        priceRefreshJob = appScope.launch {
            try {
                val city = benzonavtProvider.currentCity()
                val benzonavt = withTimeoutOrNull(5_000) {
                    benzonavtProvider.fetchCityPrices(city)
                }
                if (benzonavt != null && benzonavt.isNotEmpty()) {
                    loadMutex.withLock {
                        val swapped = withUser.map { station ->
                            stationPriceApplier.applyBenzonavtToStation(station, benzonavt, city)
                        }
                        cachedStations = swapped
                        Timber.tag(TAG).d("prices swapped from BENZONAVT")
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w("Background price refresh failed: %s", e.message)
            }
        }

        withUser
    }

    override suspend fun getStationById(stationId: Int): GasStation? = withContext(Dispatchers.IO) {
        val stations = ensureLoaded()
        stations.find { it.id == stationId }
    }

    override fun getLastCacheUpdateTime(): Long? {
        return stationCache.getLastCacheUpdateTime()
    }

    override suspend fun reportUserPrice(stationId: Int, fuelType: String, price: Double): List<GasStation> =
        loadMutex.withLock {
            userPrices.report(stationId, fuelType, price)
            val updated = stationPriceApplier.applyAllPrices(cachedStations ?: emptyList())
            cachedStations = updated
            updated
        }

    override suspend fun clearUserPrice(stationId: Int, fuelType: String): List<GasStation> =
        loadMutex.withLock {
            userPrices.clear(stationId, fuelType)
            val updated = stationPriceApplier.applyAllPrices(cachedStations ?: emptyList())
            cachedStations = updated
            updated
        }

    // ==================== ПУБЛИЧНЫЕ МЕТОДЫ ====================

    override suspend fun getAllStations(): List<GasStation> = withContext(Dispatchers.IO) {
        ensureLoaded()
    }

    override suspend fun getNearbyStations(lat: Double, lon: Double, radiusKm: Double): List<GasStation> = withContext(Dispatchers.IO) {
        val baseStations = ensureLoaded()
        val overpassStations = try {
            overpassFuelProvider.fetchStations(lat, lon, radiusKm * 1000.0)
        } catch (e: Exception) {
            Timber.tag(TAG).w("Overpass fetch failed in getNearbyStations: %s", e.message)
            emptyList()
        }
        val merged = mergeStations(baseStations, overpassStations)
        val withPrices = stationPriceApplier.applyUserPrices(merged)
        stationFilterAndSorter.getStationsNearLocation(lat, lon, radiusKm, withPrices)
    }

    override suspend fun getStationsByCity(city: String): List<GasStation> = withContext(Dispatchers.IO) {
        val stations = ensureLoaded()
        stationFilterAndSorter.filterByCity(stations, city)
    }

    override suspend fun searchStations(query: String): List<GasStation> = withContext(Dispatchers.IO) {
        val stations = ensureLoaded()
        stationFilterAndSorter.search(stations, query)
    }

    override suspend fun getBestStations(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): List<GasStation> = withContext(Dispatchers.IO) {
        val stations = ensureLoaded()
        val nearby = if (lat != null && lon != null) {
            stationFilterAndSorter.getStationsNearLocation(lat, lon, radiusKm, stations)
        } else stations

        getBestStationsUseCase.execute(nearby, fuelType)
    }

    override suspend fun getCheapestStation(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): GasStation? = withContext(Dispatchers.IO) {
        val stations = ensureLoaded()
        val nearby = if (lat != null && lon != null) {
            stationFilterAndSorter.getStationsNearLocation(lat, lon, radiusKm, stations)
        } else stations

        stationFilterAndSorter.getCheapestStation(nearby, fuelType)
    }

    override suspend fun getStationsSortedByPriceAsc(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): List<GasStation> = withContext(Dispatchers.IO) {
        val stations = ensureLoaded()
        val nearby = if (lat != null && lon != null) {
            stationFilterAndSorter.getStationsNearLocation(lat, lon, radiusKm, stations)
        } else stations

        stationFilterAndSorter.sortPriceAscending(nearby, fuelType)
    }

    override suspend fun getStationsSortedByPriceDesc(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): List<GasStation> = withContext(Dispatchers.IO) {
        val stations = ensureLoaded()
        val nearby = if (lat != null && lon != null) {
            stationFilterAndSorter.getStationsNearLocation(lat, lon, radiusKm, stations)
        } else stations

        stationFilterAndSorter.sortPriceDescending(nearby, fuelType)
    }

    override suspend fun getStationsByQueue(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): List<GasStation> = withContext(Dispatchers.IO) {
        val stations = ensureLoaded()
        val nearby = if (lat != null && lon != null) {
            stationFilterAndSorter.getStationsNearLocation(lat, lon, radiusKm, stations)
        } else stations

        stationFilterAndSorter.sortByQueue(nearby, fuelType)
    }
}
