package com.navrot.aifuelassistant.data

import android.content.Context
import android.util.Log
import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.data.providers.BenzonavtProvider
import com.navrot.aifuelassistant.data.providers.FuelPriceInfo
import com.navrot.aifuelassistant.domain.usecase.GetBestStationsUseCase
import com.navrot.aifuelassistant.geo.GeoUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Singleton

/**
 * Репозиторий АЗС.
 *
 * Приоритет источников цен:
 *  1) Benzonavt (реальные медианы по городу через прокси, свежее по updatedAt);
 *  2) пользовательские цены (SharedPreferences, через UserPriceRepository);
 *  3) удалённый stations.json (GitHub raw);
 *  4) локальный кеш последнего успешного ответа;
 *  5) assets/stations.json — офлайн-фолбэк.
 *
 * Управляется Hilt (синглтон), потокобезопасен через [Mutex].
 * OkHttpClient и UserPriceRepository приходят из DI (AppModule) — единый клиент
 * на всё приложение, без отдельных инстансов на каждый репозиторий.
 *
 * ВАЖНО: конструктор намеренно БЕЗ @Inject — экземпляр собирается явно через
 * @Provides в AppModule (нужно смешивание @ApplicationContext + двух зависимостей
 * без квалификатора). Если добавить @Inject constructor здесь, Dagger увидит два
 * конфликтующих binding'а для одного и того же конкретного типа GasStationRepository
 * и сборка сломается ("GasStationRepository is bound multiple times").
 */
@Singleton
class GasStationRepository constructor(
    // Оставлен без @Inject — собирается через @Provides в AppModule
    // (смешивание @ApplicationContext + двух зависимостей без квалификатора).

    private val context: Context,
    private val httpClient: OkHttpClient,
    private val userPrices: UserPriceRepository,
    private val getBestStationsUseCase: GetBestStationsUseCase,
    private val benzonavtProvider: BenzonavtProvider
) : GasStationRepositoryInterface {

    companion object {
        private const val TAG = "GasStationRepo"
        private const val REMOTE_URL =
            "https://raw.githubusercontent.com/navrot73-gif/AI-Fuel-Assistant/main/app/src/main/assets/stations.json"
        private const val CACHE_FILE = "stations_cache.json"
        private const val REFRESH_INTERVAL_MS = 10 * 60 * 1000L
    }

    private val loadMutex = Mutex()
    private var cachedStations: List<GasStation>? = null
    private var lastRemoteCheckMs = 0L

    // Job для фонового обновления цен, чтобы отменять при новом вызове
    private var priceRefreshJob: kotlinx.coroutines.Job? = null

    private suspend fun ensureLoaded(): List<GasStation> = loadMutex.withLock {
        cachedStations?.let { return@withLock it }

        val now = System.currentTimeMillis()
        val stations: List<GasStation> =
            if (now - lastRemoteCheckMs >= REFRESH_INTERVAL_MS) {
                lastRemoteCheckMs = now
                loadFromRemote() ?: loadFromCache() ?: loadFromAssets()
            } else {
                loadFromCache() ?: loadFromAssets()
            }

        // СНАЧАЛА применяем user prices — НЕМЕДЛЕННО возвращаем станции на карту
        val withUser = applyUserPrices(stations)
        cachedStations = withUser
        Log.d(TAG, "stations shown (${withUser.size}), prices pending")

        // ЗАТЕМ фоновая coroutine: fetchCityPrices + graceful swap
        priceRefreshJob?.cancel()
        priceRefreshJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val city = benzonavtProvider.currentCity()
                val benzonavt = benzonavtProvider.fetchCityPrices(city)
                if (benzonavt.isNotEmpty()) {
                    loadMutex.withLock {
                        val swapped = withUser.map { station -> applyBenzonavtToStation(station, benzonavt, city) }
                        cachedStations = swapped
                        Log.d(TAG, "prices swapped from BENZONAVT")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background price refresh failed: ${e.message}")
            }
        }

        withUser
    }

    /** Принудительное обновление из сети + применение пользовательских цен. */
    override suspend fun refresh(): List<GasStation> = loadMutex.withLock {
        lastRemoteCheckMs = System.currentTimeMillis()
        val stations = loadFromRemote() ?: loadFromCache() ?: loadFromAssets()

        // СНАЧАЛА user prices — НЕМЕДЛЕННО возвращаем
        val withUser = applyUserPrices(stations)
        cachedStations = withUser
        Log.d(TAG, "stations shown (${withUser.size}), prices pending")

        // ЗАТЕМ фоновый Benzonavt swap
        priceRefreshJob?.cancel()
        priceRefreshJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val city = benzonavtProvider.currentCity()
                val benzonavt = benzonavtProvider.fetchCityPrices(city)
                if (benzonavt.isNotEmpty()) {
                    loadMutex.withLock {
                        val swapped = withUser.map { station -> applyBenzonavtToStation(station, benzonavt, city) }
                        cachedStations = swapped
                        Log.d(TAG, "prices swapped from BENZONAVT")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background price refresh failed: ${e.message}")
            }
        }

        withUser
    }

    /**
     * Пересчитать цены на уже загруженных станциях (после определения города):
     * Benzonavt (кеш 30 мин) → user prices → stations.json. Без сетевого запроса станций.
     * Timeout 5 сек — не ждать дольше.
     */
    override suspend fun refreshPrices(): List<GasStation> = loadMutex.withLock {
        val base = cachedStations ?: loadFromRemote() ?: loadFromCache() ?: loadFromAssets()

        // СНАЧАЛА user prices — НЕМЕДЛЕННО возвращаем
        val withUser = applyUserPrices(base)
        cachedStations = withUser
        Log.d(TAG, "stations shown (${withUser.size}), prices pending")

        // ЗАТЕМ фоновый Benzonavt swap с timeout 5 сек
        priceRefreshJob?.cancel()
        priceRefreshJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val city = benzonavtProvider.currentCity()
                val benzonavt = kotlinx.coroutines.withTimeoutOrNull(5_000) {
                    benzonavtProvider.fetchCityPrices(city)
                }
                if (benzonavt != null && benzonavt.isNotEmpty()) {
                    loadMutex.withLock {
                        val swapped = withUser.map { station -> applyBenzonavtToStation(station, benzonavt, city) }
                        cachedStations = swapped
                        Log.d(TAG, "prices swapped from BENZONAVT")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background price refresh failed: ${e.message}")
            }
        }

        withUser
    }

    override suspend fun getStationById(stationId: Int): GasStation? = withContext(Dispatchers.IO) {
        val stations = ensureLoaded()
        stations.find { it.id == stationId }
    }

    /**
     * Сообщить пользовательскую цену. Цена немедленно применяется к кешу.
     */
    override suspend fun reportUserPrice(stationId: Int, fuelType: String, price: Double): List<GasStation> =
        loadMutex.withLock {
            userPrices.report(stationId, fuelType, price)
            val updated = applyAllPrices(cachedStations ?: emptyList())
            cachedStations = updated
            updated
        }

    /**
     * Очистить пользовательскую цену (когда пришёл новый json с актуальной ценой).
     */
    override suspend fun clearUserPrice(stationId: Int, fuelType: String): List<GasStation> =
        loadMutex.withLock {
            userPrices.clear(stationId, fuelType)
            val updated = applyAllPrices(cachedStations ?: emptyList())
            cachedStations = updated
            updated
        }

    // ==================== ПУБЛИЧНЫЕ МЕТОДЫ ====================

    override suspend fun getAllStations(): List<GasStation> = withContext(Dispatchers.IO) {
        ensureLoaded()
    }

    override suspend fun getNearbyStations(lat: Double, lon: Double, radiusKm: Double): List<GasStation> = withContext(Dispatchers.IO) {
        val stations = ensureLoaded()
        getStationsNearLocation(lat, lon, radiusKm, stations)
    }

    override suspend fun getStationsByCity(city: String): List<GasStation> = withContext(Dispatchers.IO) {
        val stations = ensureLoaded()
        stations.filter { it.address.contains(city, ignoreCase = true) }
    }

    override suspend fun searchStations(query: String): List<GasStation> = withContext(Dispatchers.IO) {
        val stations = ensureLoaded()
        val q = query.lowercase()
        stations.filter {
            it.name.lowercase().contains(q) ||
                    it.brand.lowercase().contains(q) ||
                    it.address.lowercase().contains(q)
        }
    }

    override suspend fun getBestStations(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): List<GasStation> = withContext(Dispatchers.IO) {
        val stations = ensureLoaded()
        val nearby = if (lat != null && lon != null) {
            getStationsNearLocation(lat, lon, radiusKm, stations)
        } else stations

        getBestStationsUseCase.execute(nearby, fuelType)
    }

    override suspend fun getCheapestStation(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): GasStation? = withContext(Dispatchers.IO) {
        val stations = ensureLoaded()
        val nearby = if (lat != null && lon != null) {
            getStationsNearLocation(lat, lon, radiusKm, stations)
        } else stations

        nearby
            .filter { s -> s.fuelTypes.any { it.type == fuelType && it.available } }
            .minByOrNull { s -> s.fuelTypes.find { it.type == fuelType }?.price ?: Double.MAX_VALUE }
    }

    override suspend fun getStationsSortedByPriceAsc(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): List<GasStation> = withContext(Dispatchers.IO) {
        val stations = ensureLoaded()
        val nearby = if (lat != null && lon != null) {
            getStationsNearLocation(lat, lon, radiusKm, stations)
        } else stations

        nearby.filter { s -> s.fuelTypes.any { it.type == fuelType && it.available } }
            .sortedBy { s -> s.fuelTypes.find { it.type == fuelType }?.price ?: Double.MAX_VALUE }
    }

    override suspend fun getStationsSortedByPriceDesc(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): List<GasStation> = withContext(Dispatchers.IO) {
        val stations = ensureLoaded()
        val nearby = if (lat != null && lon != null) {
            getStationsNearLocation(lat, lon, radiusKm, stations)
        } else stations

        nearby.filter { s -> s.fuelTypes.any { it.type == fuelType && it.available } }
            .sortedByDescending { s -> s.fuelTypes.find { it.type == fuelType }?.price ?: Double.MAX_VALUE }
    }

    override suspend fun getStationsByQueue(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): List<GasStation> = withContext(Dispatchers.IO) {
        val stations = ensureLoaded()
        val nearby = if (lat != null && lon != null) {
            getStationsNearLocation(lat, lon, radiusKm, stations)
        } else stations

        nearby.filter { s -> s.fuelTypes.any { it.type == fuelType && it.available } }
            .sortedBy { it.queueTime }
    }

    // ==================== ЗАГРУЗКА И ПРИМЕНЕНИЕ ЦЕН ====================

    private fun getStationsNearLocation(lat: Double, lon: Double, radiusKm: Double, stations: List<GasStation>): List<GasStation> {
        return stations.filter { station ->
            val distance = GeoUtils.calculateDistance(lat, lon, station.latitude, station.longitude)
            distance <= radiusKm
        }.sortedBy { station ->
            GeoUtils.calculateDistance(lat, lon, station.latitude, station.longitude)
        }
    }

    /** Применяет пользовательские цены поверх базовых станций. */
    private fun applyUserPrices(stations: List<GasStation>): List<GasStation> {
        val overrides = userPrices.getAll()
        if (overrides.isEmpty()) return stations
        return stations.map { station ->
            val newFuelTypes = station.fuelTypes.map { fuel ->
                val override = overrides[Pair(station.id, fuel.type)]
                if (override != null && override > 0) {
                    fuel.copy(
                        price = override,
                        updatedAt = System.currentTimeMillis()
                    )
                } else {
                    fuel
                }
            }
            station.copy(fuelTypes = newFuelTypes)
        }
    }

    /**
     * Полная цепочка цен: user prices поверх stations.json, затем Benzonavt
     * поверх всего (если его updatedAt свежее текущей цены).
     */
    private suspend fun applyAllPrices(stations: List<GasStation>): List<GasStation> {
        val withUser = applyUserPrices(stations)
        val city = benzonavtProvider.currentCity()
        val benzonavt = benzonavtProvider.fetchCityPrices(city)
        if (benzonavt.isEmpty()) return withUser
        return withUser.map { station -> applyBenzonavtToStation(station, benzonavt, city) }
    }

    private fun applyBenzonavtToStation(
        station: GasStation,
        benzonavt: Map<String, FuelPriceInfo>,
        city: String
    ): GasStation {
        var changed = false
        val newFuelTypes = station.fuelTypes.map { fuel ->
            val info = benzonavt[fuel.type]
                ?: benzonavt.entries.firstOrNull { it.key.equals(fuel.type, ignoreCase = true) }?.value
                ?: return@map fuel
            val benzonavtTs = parseUpdatedAt(info.updatedAt)
            // Benzonavt свежее текущего источника (stations.json=0, user price=время отчёта)
            if (benzonavtTs > fuel.updatedAt) {
                changed = true
                fuel.copy(
                    price = info.median,
                    available = true,
                    source = FuelDataSource.BENZONAVT,
                    sourceCount = info.sourceCount,
                    updatedAt = benzonavtTs
                )
            } else {
                fuel
            }
        }
        if (!changed) return station
        Log.d(
            TAG,
            "station ${station.id} (${station.brand}): цены обновлены из BENZONAVT, город=$city"
        )
        return station.copy(
            fuelTypes = newFuelTypes,
            dataSources = station.dataSources + FuelDataSource.BENZONAVT
        )
    }

    /** ISO-строка updatedAt → epoch ms. При ошибке парсинга — 0 (устаревший источник). */
    private fun parseUpdatedAt(value: String): Long {
        if (value.isBlank()) return 0L
        return try {
            java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                java.time.Instant.parse(value).toEpochMilli()
            } catch (_: Exception) {
                0L
            }
        }
    }

    private suspend fun loadFromRemote(): List<GasStation>? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(REMOTE_URL).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val stations = parseJson(body)
                if (stations.isNotEmpty()) {
                    cacheFile().writeText(body)
                }
                stations.takeIf { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось загрузить станции из сети: ${e.message}")
            null
        }
    }

    private suspend fun loadFromCache(): List<GasStation>? = withContext(Dispatchers.IO) {
        try {
            val file = cacheFile()
            if (!file.exists()) return@withContext null
            parseJson(file.readText()).takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось загрузить станции из кеша: ${e.message}")
            null
        }
    }

    private fun loadFromAssets(): List<GasStation> {
        val jsonString = context.assets.open("stations.json").bufferedReader().use { it.readText() }
        return parseJson(jsonString)
    }

    private fun cacheFile(): File = File(context.filesDir, CACHE_FILE)

    private fun parseJson(jsonString: String): List<GasStation> {
        val jsonArray = JSONArray(jsonString)
        return (0 until jsonArray.length()).map { i ->
            parseStation(jsonArray.getJSONObject(i))
        }
    }

    private fun parseStation(json: JSONObject): GasStation {
        val fuelArray = json.getJSONArray("fuelTypes")
        val fuelTypes = (0 until fuelArray.length()).map { j ->
            val fuel = fuelArray.getJSONObject(j)
            FuelPrice(
                type = fuel.getString("type"),
                price = fuel.getDouble("price"),
                available = fuel.getBoolean("available")
            )
        }
        return GasStation(
            id = json.getInt("id"),
            name = json.getString("name"),
            brand = json.getString("brand"),
            address = json.getString("address"),
            latitude = json.getDouble("latitude"),
            longitude = json.getDouble("longitude"),
            fuelTypes = fuelTypes,
            queueTime = json.getInt("queueTime"),
            reliability = json.getInt("reliability"),
            monumentPhotoUrl = if (json.has("monumentPhotoUrl")) json.getString("monumentPhotoUrl") else null,
            entrancePhotoUrl = if (json.has("entrancePhotoUrl")) json.getString("entrancePhotoUrl") else null
        )
    }
}
