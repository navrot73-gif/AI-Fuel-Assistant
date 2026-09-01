package com.navrot.aifuelassistant.data.datasource

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class OverpassFuelProviderImpl @Inject constructor(
    private val httpClient: OkHttpClient
) : OverpassFuelProvider {

    companion object {
        private const val TAG = "OverpassFuelProvider"
        val MIRRORS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.private.coffee/api/interpreter",
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter"
        )
        private const val MIRROR_TIMEOUT_MS = 8_000L
        private const val TIMEOUT_SECONDS = 8L
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    private class CacheEntry(
        val timestamp: Long,
        val stations: List<GasStation>
    )

    private val cache = java.util.concurrent.ConcurrentHashMap<String, CacheEntry>()

    fun clearCache() {
        cache.clear()
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
            cache[cacheKey]?.let { entry ->
                if (now - entry.timestamp < CACHE_TTL_MS) {
                    Timber.tag(TAG).i("Returning cached Overpass stations (%d stations)", entry.stations.size)
                    return@withContext entry.stations
                }
            }

            val query = """
                [out:json][timeout:8];
                nwr["amenity"="fuel"](around:${radiusMeters.toInt()},$lat,$lon);
                out center;
            """.trimIndent()

            for (mirrorUrl in MIRRORS) {
                val stations = kotlinx.coroutines.withTimeoutOrNull(MIRROR_TIMEOUT_MS) {
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
                    cache[cacheKey] = CacheEntry(System.currentTimeMillis(), stations)
                    return@withContext stations
                }
            }

            // Return cached entry if available even if expired when all mirrors fail
            cache[cacheKey]?.let { entry ->
                Timber.tag(TAG).w("All mirrors failed, using expired cached Overpass stations (%d stations)", entry.stations.size)
                return@withContext entry.stations
            }

            Timber.tag(TAG).w("All Overpass mirrors failed")
            emptyList()
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
