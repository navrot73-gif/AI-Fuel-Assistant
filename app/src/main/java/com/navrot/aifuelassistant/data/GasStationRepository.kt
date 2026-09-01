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
import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.data.model.matchesBrand
import com.navrot.aifuelassistant.data.model.stationListSignature
import com.navrot.aifuelassistant.data.providers.BenzonavtProvider
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import com.navrot.aifuelassistant.domain.reliability.PriceReliabilityCalculator
import com.navrot.aifuelassistant.domain.usecase.GetBestStationsUseCase
import com.navrot.aifuelassistant.geo.GeoUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
     * Base stations take precedence. Overpass stations within DEDUPLICATION_RADIUS_KM of any base station
     * (with matching brand) are merged into a SINGLE GasStation object retaining base ID and base metadata,
     * while storing osmId and dataSources.
     * OSM-only stations retain their stable ID.
     */
    fun mergeStations(baseStations: List<GasStation>, overpassStations: List<GasStation>): List<GasStation> {
        if (overpassStations.isEmpty()) return baseStations
        if (baseStations.isEmpty()) return overpassStations

        return try {
            val mergedBaseMap = HashMap<Int, GasStation>(baseStations.size)
            val baseOrderList = ArrayList<Int>(baseStations.size)

            for (base in baseStations) {
                mergedBaseMap[base.id] = base
                baseOrderList.add(base.id)
            }

            val standaloneOsmStations = ArrayList<GasStation>()

            for (overpass in overpassStations) {
                val matchingBaseId = baseOrderList.firstOrNull { baseId ->
                    val base = mergedBaseMap[baseId] ?: return@firstOrNull false
                    val distKm = GeoUtils.calculateDistance(base.latitude, base.longitude, overpass.latitude, overpass.longitude)
                    val distMeters = distKm * 1000.0
                    val isWithinRadius = distMeters <= 120.0
                    val brandMatches = base.matchesBrand(overpass.brand) || overpass.matchesBrand(base.brand) ||
                            (base.brand.isNotBlank() && overpass.brand.isNotBlank() &&
                                    (base.brand.contains(overpass.brand, ignoreCase = true) || overpass.brand.contains(base.brand, ignoreCase = true)))
                    isWithinRadius && brandMatches
                }

                if (matchingBaseId != null) {
                    val existingBase = mergedBaseMap[matchingBaseId]!!
                    val updatedSources = existingBase.dataSources + FuelDataSource.OVERPASS
                    val updatedOsmId = existingBase.osmId ?: overpass.osmId
                    mergedBaseMap[matchingBaseId] = existingBase.copy(
                        dataSources = updatedSources,
                        osmId = updatedOsmId
                    )
                } else {
                    standaloneOsmStations.add(overpass)
                }
            }

            standaloneOsmStations.sortBy { it.id }

            val result = ArrayList<GasStation>(baseOrderList.size + standaloneOsmStations.size)
            for (baseId in baseOrderList) {
                result.add(mergedBaseMap[baseId]!!)
            }
            result.addAll(standaloneOsmStations)
            result
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Error merging stations, returning base list")
            baseStations
        }
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

    override fun getNearbyStationsFlow(lat: Double, lon: Double, radiusKm: Double): Flow<List<GasStation>> = flow {
        val baseStations = ensureLoaded()
        val baseWithPrices = stationPriceApplier.applyAllPrices(baseStations)
        val baseNearby = stationFilterAndSorter.getStationsNearLocation(lat, lon, radiusKm, baseWithPrices)

        Timber.tag(TAG).i("static emitted %d", baseNearby.size)
        logStationStatusSummary(baseNearby)
        emit(baseNearby)

        val overpassStations = try {
            withTimeoutOrNull(10_000L) {
                overpassFuelProvider.fetchStations(lat, lon, radiusKm * 1000.0)
            } ?: emptyList()
        } catch (e: Exception) {
            Timber.tag(TAG).w("Overpass fetch failed in getNearbyStationsFlow: %s", e.message)
            emptyList()
        }
        Timber.tag(TAG).i("overpass result %d", overpassStations.size)

        if (overpassStations.isNotEmpty()) {
            val merged = mergeStations(baseStations, overpassStations)
            val withPrices = stationPriceApplier.applyAllPrices(merged)
            val nearbyEnriched = stationFilterAndSorter.getStationsNearLocation(lat, lon, radiusKm, withPrices)

            Timber.tag(TAG).i("merged %d", nearbyEnriched.size)
            logStationStatusSummary(nearbyEnriched)

            if (nearbyEnriched != baseNearby) {
                emit(nearbyEnriched)
            }
        } else {
            Timber.tag(TAG).i("merged %d", baseNearby.size)
        }
    }.distinctUntilChangedBy { list ->
        list.stationListSignature()
    }.flowOn(Dispatchers.IO)

    private fun logStationStatusSummary(stations: List<GasStation>) {
        var greenCount = 0
        var redCount = 0
        var grayCount = 0
        val now = System.currentTimeMillis()
        for (st in stations) {
            when (PriceReliabilityCalculator.calculateFuelAvailability(st, currentTimeMs = now)) {
                FuelAvailabilityStatus.AVAILABLE -> greenCount++
                FuelAvailabilityStatus.NO_FUEL -> redCount++
                FuelAvailabilityStatus.UNKNOWN -> grayCount++
            }
        }
        Timber.tag(TAG).i("merged: %d stations, %d green, %d red, %d gray", stations.size, greenCount, redCount, grayCount)
    }

    override suspend fun getNearbyStations(lat: Double, lon: Double, radiusKm: Double): List<GasStation> = withContext(Dispatchers.IO) {
        getNearbyStationsFlow(lat, lon, radiusKm).first()
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
