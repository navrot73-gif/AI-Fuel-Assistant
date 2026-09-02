package com.navrot.aifuelassistant.data.datasource

import android.content.Context
import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class OverpassFuelProviderImpl @Inject constructor(
    private val httpClient: OkHttpClient,
    @ApplicationContext private val context: Context? = null
) : OverpassFuelProvider {

    constructor(httpClient: OkHttpClient) : this(httpClient, null)

    companion object {
        private const val TAG = "OverpassFuelProvider"
        val MIRRORS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.private.coffee/api/interpreter",
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter"
        )
        private const val MIRROR_TIMEOUT_MS = 2_000L
        private const val TOTAL_ENRICHMENT_BUDGET_MS = 6_000L
        private const val TIMEOUT_SECONDS = 2L
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val CACHE_FILE_NAME = "overpass_cache.json"
    }

    private class CacheEntry(
        val timestamp: Long,
        val stations: List<GasStation>
    )

    private val cache = java.util.concurrent.ConcurrentHashMap<String, CacheEntry>()

    fun clearCache() {
        cache.clear()
        try {
            val cacheFile = context?.let { File(it.filesDir, CACHE_FILE_NAME) }
            if (cacheFile != null && cacheFile.exists()) {
                cacheFile.delete()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to delete persistent Overpass cache file")
        }
    }

    private val overpassHttpClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun fetchStations(lat: Double, lon: Double, radiusMeters: Double): List<GasStation> =
        withContext(Dispatchers.IO) {
            val cacheKey = "${String.format(java.util.Locale.US, "%.2f", lat)}_${String.format(java.util.Locale.US, "%.2f", lon)}_${radiusMeters.toInt()}"
            val now = System.currentTimeMillis()

            // 1. Check in-memory cache
            cache[cacheKey]?.let { entry ->
                if (now - entry.timestamp < CACHE_TTL_MS) {
                    Timber.tag(TAG).i("Returning in-memory cached Overpass stations (%d stations)", entry.stations.size)
                    return@withContext entry.stations
                }
            }

            // 2. Check persistent disk cache if in-memory cache missed or expired
            val diskEntry = readFromDiskCache(cacheKey)
            if (diskEntry != null) {
                cache[cacheKey] = diskEntry
                if (now - diskEntry.timestamp < CACHE_TTL_MS) {
                    Timber.tag(TAG).i("Returning persisted Overpass stations from disk (%d stations)", diskEntry.stations.size)
                    return@withContext diskEntry.stations
                }
            }

            // 3. Network fetch within 6-second total budget
            val fetchedStations = withTimeoutOrNull(TOTAL_ENRICHMENT_BUDGET_MS) {
                val query = """
                    [out:json][timeout:8];
                    nwr["amenity"="fuel"](around:${radiusMeters.toInt()},$lat,$lon);
                    out center;
                """.trimIndent()

                for (mirrorUrl in MIRRORS) {
                    val stations = withTimeoutOrNull(MIRROR_TIMEOUT_MS) {
                        try {
                            val requestBody = query.toRequestBody("text/plain; charset=utf-8".toMediaType())
                            val request = Request.Builder()
                                .url(mirrorUrl)
                                .post(requestBody)
                                .header("User-Agent", "AIFuelAssistant/1.0")
                                .build()

                            overpassHttpClient.newCall(request).execute().use { response ->
                                if (response.isSuccessful) {
                                    val bodyString = response.body?.string() ?: return@use null
                                    parseOverpassResponse(bodyString)
                                } else {
                                    Timber.tag(TAG).w("Mirror %s returned HTTP error: %d", mirrorUrl, response.code)
                                    null
                                }
                            }
                        } catch (e: Exception) {
                            Timber.tag(TAG).w("Mirror %s failed: %s", mirrorUrl, e.message)
                            null
                        }
                    }

                    if (stations != null) {
                        Timber.tag(TAG).i("Fetched %d stations from Overpass mirror: %s", stations.size, mirrorUrl)
                        val entry = CacheEntry(System.currentTimeMillis(), stations)
                        cache[cacheKey] = entry
                        writeToDiskCache(cacheKey, entry)
                        return@withTimeoutOrNull stations
                    }
                }
                null
            }

            if (fetchedStations != null) {
                return@withContext fetchedStations
            }

            // 4. Fallback to expired cache (disk or memory) if network fetch/enrichment failed or timed out
            val fallbackEntry = cache[cacheKey] ?: diskEntry
            if (fallbackEntry != null) {
                Timber.tag(TAG).w("Enrichment failed or timed out, using fallback Overpass stations (%d stations)", fallbackEntry.stations.size)
                return@withContext fallbackEntry.stations
            }

            Timber.tag(TAG).w("All Overpass mirrors failed or timed out with no cache available")
            emptyList()
        }

    private fun readFromDiskCache(cacheKey: String): CacheEntry? {
        val context = this.context ?: return null
        return try {
            val file = File(context.filesDir, CACHE_FILE_NAME)
            if (!file.exists()) return null
            val content = file.readText()
            val root = JSONObject(content)
            if (!root.has(cacheKey)) return null
            val item = root.getJSONObject(cacheKey)
            val timestamp = item.optLong("timestamp", 0L)
            val stationsJson = item.optJSONArray("stations") ?: JSONArray()
            val stations = ArrayList<GasStation>(stationsJson.length())
            for (i in 0 until stationsJson.length()) {
                val stObj = stationsJson.getJSONObject(i)
                val fuelTypesJson = stObj.optJSONArray("fuelTypes") ?: JSONArray()
                val fuelTypes = ArrayList<FuelPrice>(fuelTypesJson.length())
                for (f in 0 until fuelTypesJson.length()) {
                    val fObj = fuelTypesJson.getJSONObject(f)
                    fuelTypes.add(
                        FuelPrice(
                            type = fObj.optString("type", ""),
                            price = fObj.optDouble("price", 0.0),
                            available = fObj.optBoolean("available", false),
                            source = FuelDataSource.OVERPASS,
                            updatedAt = fObj.optLong("updatedAt", 0L)
                        )
                    )
                }
                stations.add(
                    GasStation(
                        id = stObj.optInt("id"),
                        name = stObj.optString("name"),
                        brand = stObj.optString("brand"),
                        address = stObj.optString("address"),
                        latitude = stObj.optDouble("latitude"),
                        longitude = stObj.optDouble("longitude"),
                        fuelTypes = fuelTypes,
                        queueTime = stObj.optInt("queueTime", 0),
                        reliability = stObj.optInt("reliability", 0),
                        dataSources = setOf(FuelDataSource.OVERPASS),
                        updatedAt = stObj.optLong("updatedAt", 0L),
                        openingHours = stObj.optString("openingHours").takeIf { it.isNotBlank() },
                        osmId = stObj.optString("osmId").takeIf { it.isNotBlank() }
                    )
                )
            }
            CacheEntry(timestamp, stations)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Error reading Overpass disk cache")
            null
        }
    }

    private fun writeToDiskCache(cacheKey: String, entry: CacheEntry) {
        val context = this.context ?: return
        try {
            val file = File(context.filesDir, CACHE_FILE_NAME)
            val root = if (file.exists()) {
                try { JSONObject(file.readText()) } catch (e: Exception) { JSONObject() }
            } else {
                JSONObject()
            }

            val item = JSONObject()
            item.put("timestamp", entry.timestamp)
            val stationsJson = JSONArray()
            for (st in entry.stations) {
                val stObj = JSONObject()
                stObj.put("id", st.id)
                stObj.put("name", st.name)
                stObj.put("brand", st.brand)
                stObj.put("address", st.address)
                stObj.put("latitude", st.latitude)
                stObj.put("longitude", st.longitude)
                stObj.put("queueTime", st.queueTime)
                stObj.put("reliability", st.reliability)
                stObj.put("updatedAt", st.updatedAt)
                stObj.put("openingHours", st.openingHours ?: "")
                stObj.put("osmId", st.osmId ?: "")

                val fuelTypesJson = JSONArray()
                for (f in st.fuelTypes) {
                    val fObj = JSONObject()
                    fObj.put("type", f.type)
                    fObj.put("price", f.price)
                    fObj.put("available", f.available)
                    fObj.put("updatedAt", f.updatedAt)
                    fuelTypesJson.put(fObj)
                }
                stObj.put("fuelTypes", fuelTypesJson)
                stationsJson.put(stObj)
            }
            item.put("stations", stationsJson)
            root.put(cacheKey, item)

            file.writeText(root.toString())
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Error writing Overpass disk cache")
        }
    }

    fun parseOverpassResponse(jsonString: String): List<GasStation> {
        try {
            val root = JSONObject(jsonString)
            val elements = root.optJSONArray("elements") ?: return emptyList()
            val stations = ArrayList<GasStation>(elements.length())

            for (i in 0 until elements.length()) {
                val elem = elements.optJSONObject(i) ?: continue
                val type = elem.optString("type", "")
                val osmId = elem.optLong("id", -1L)
                if (type.isBlank() || osmId < 0) continue

                var elemLat = Double.NaN
                var elemLon = Double.NaN

                if (elem.has("lat") && elem.has("lon")) {
                    elemLat = elem.optDouble("lat", Double.NaN)
                    elemLon = elem.optDouble("lon", Double.NaN)
                } else if (elem.has("center")) {
                    val center = elem.optJSONObject("center")
                    if (center != null) {
                        elemLat = center.optDouble("lat", Double.NaN)
                        elemLon = center.optDouble("lon", Double.NaN)
                    }
                }

                if (elemLat.isNaN() || elemLon.isNaN()) continue

                val tags = elem.optJSONObject("tags")
                val rawName = tags?.optString("name", "")?.trim().orEmpty()
                val rawBrand = tags?.optString("brand", "")?.trim().orEmpty()
                val rawOperator = tags?.optString("operator", "")?.trim().orEmpty()

                val name = when {
                    rawName.isNotBlank() -> rawName
                    rawBrand.isNotBlank() -> rawBrand
                    rawOperator.isNotBlank() -> rawOperator
                    else -> "АЗС (без названия)"
                }

                val brand = when {
                    rawBrand.isNotBlank() -> rawBrand
                    rawOperator.isNotBlank() -> rawOperator
                    rawName.isNotBlank() -> rawName
                    else -> "Прочие"
                }

                val street = tags?.optString("addr:street", "")?.trim().orEmpty()
                val housenumber = tags?.optString("addr:housenumber", "")?.trim().orEmpty()
                val fullAddr = tags?.optString("addr:full", "")?.trim().orEmpty()

                val address = when {
                    street.isNotBlank() && housenumber.isNotBlank() -> "$street, $housenumber"
                    street.isNotBlank() -> street
                    fullAddr.isNotBlank() -> fullAddr
                    else -> "Окрестности OSM"
                }

                val openingHours = tags?.optString("opening_hours", "")?.trim()?.takeIf { it.isNotBlank() }

                // Generates a deterministic negative Int ID for OSM elements
                val uniqueIdKey = "osm_${type}_$osmId"
                var idHash = -abs(uniqueIdKey.hashCode())
                if (idHash == 0) idHash = -1

                val osmIdStr = "osm:$osmId"

                val defaultFuelTypes = listOf(
                    FuelPrice(type = "AI-92", price = 0.0, available = false, source = FuelDataSource.OVERPASS, updatedAt = 0L),
                    FuelPrice(type = "AI-95", price = 0.0, available = false, source = FuelDataSource.OVERPASS, updatedAt = 0L),
                    FuelPrice(type = "AI-98", price = 0.0, available = false, source = FuelDataSource.OVERPASS, updatedAt = 0L),
                    FuelPrice(type = "Diesel", price = 0.0, available = false, source = FuelDataSource.OVERPASS, updatedAt = 0L),
                    FuelPrice(type = "Gas", price = 0.0, available = false, source = FuelDataSource.OVERPASS, updatedAt = 0L)
                )

                val station = GasStation(
                    id = idHash,
                    name = name,
                    brand = brand,
                    address = address,
                    latitude = elemLat,
                    longitude = elemLon,
                    fuelTypes = defaultFuelTypes,
                    queueTime = 0,
                    reliability = 0,
                    dataSources = setOf(FuelDataSource.OVERPASS),
                    updatedAt = 0L,
                    openingHours = openingHours,
                    osmId = osmIdStr
                )

                stations.add(station)
            }

            return stations
        } catch (e: Exception) {
            Timber.tag(TAG).w("Error parsing Overpass JSON response: %s", e.message)
            return emptyList()
        }
    }
}
