package com.navrot.aifuelassistant.data.datasource

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class FuelObservation(
    val brand: String,
    val address: String,
    val fuelType: String,
    val price: Double,
    val available: Boolean,
    val limitNote: String? = null,
    val statusText: String = ""
)

interface RussiabaseProvider {
    suspend fun fetchObservations(
        citySlug: String,
        fuels: List<String> = listOf("ai95", "dt"),
        lat: Double? = null,
        lon: Double? = null
    ): List<FuelObservation>
}

object RussiabaseHtmlParser {

    fun mapMarkToFuelType(mark: String): String {
        return when (mark.lowercase().trim()) {
            "ai92", "аи92", "аи-92", "92" -> "АИ-92"
            "ai95", "аи95", "аи-95", "95" -> "АИ-95"
            "ai98", "аи98", "аи-98", "98" -> "АИ-98"
            "ai100", "аи100", "аи-100", "100" -> "АИ-100"
            "dt", "дт", "diesel" -> "ДТ"
            else -> mark.uppercase().trim()
        }
    }

    /**
     * Parses HTML or text blocks content from russiabase.ru price pages.
     * Structural block format:
     * 1) Name: "Газпромнефть №201" | "ООО «Башнефть-Розница» АЗС 74-020" | ...
     * 2) Address: "Челябинск, Свердловский тракт, 12В..."
     * 3) Block status: "АЗС закрыта" + "Топлива нет, АЗС не работает" -> ALL fuels NO_FUEL
     *    OR "Открыто"/"Очередь"/"Уточнить статус" -> read fuel pairs
     * 4) Fuel pairs: "Аи-92 / 61.05р. / Лимит до 40 л. / Доступно"
     * 5) Freshness: "Отмечено пользователем N мин назад"
     */
    fun parseHtml(html: String, mark: String): List<FuelObservation> {
        val mappedFuelType = mapMarkToFuelType(mark)
        val observations = mutableListOf<FuelObservation>()

        if (html.isBlank()) return emptyList()

        // 1. First attempt: Parse block structures separated by "---" or div blocks
        val rawBlocks = if (html.contains("---")) {
            html.split("---").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            val divItemRegex = Regex("<div[^>]*class=\"[^\"]*(?:station|price-item|item)[^\"]*\"[^>]*>(.*?)</div>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            val matches = divItemRegex.findAll(html).map { cleanHtmlText(it.groupValues[1]) }.toList()
            if (matches.isNotEmpty()) matches else listOf(html)
        }

        for (block in rawBlocks) {
            val cleanBlockText = cleanHtmlText(block)
            val lines = cleanBlockText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
            if (lines.size >= 2) {
                val blockObs = parseStationBlock(lines, mappedFuelType)
                observations.addAll(blockObs)
            }
        }

        if (observations.isNotEmpty()) return observations

        // 2. Fallback attempt: Parse HTML table rows <tr><td>
        val rowRegex = Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val cellRegex = Regex("<td[^>]*>(.*?)</td>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

        val rowMatches = rowRegex.findAll(html).toList()

        for (rowMatch in rowMatches) {
            val rowHtml = rowMatch.groupValues[1]
            val cells = cellRegex.findAll(rowHtml).map { cleanHtmlText(it.groupValues[1]) }.toList()

            if (cells.size >= 3) {
                val rawBrand = cells[0]
                val rawAddress = cells[1]
                val priceOrStatusStr = cells[2]
                val extraStatusStr = if (cells.size >= 4) cells[3] else ""

                val combinedStatus = "$priceOrStatusStr $extraStatusStr".trim()

                val observation = parseRowData(rawBrand, rawAddress, combinedStatus, mappedFuelType)
                if (observation != null) {
                    observations.add(observation)
                }
            }
        }

        return observations
    }

    private fun parseStationBlock(lines: List<String>, requestedFuelType: String): List<FuelObservation> {
        val name = lines[0].trim()
        val address = lines[1].trim()

        if (name.isBlank() || address.isBlank()) return emptyList()
        if (name.equals("АЗС", true) || name.equals("Бренд", true) || address.equals("Адрес", true)) return emptyList()

        val restText = lines.drop(2).joinToString(" ").lowercase()
        val isClosedOrNoFuelAll = restText.contains("азс закрыта") ||
                restText.contains("топлива нет") ||
                restText.contains("азс не работает")

        if (isClosedOrNoFuelAll) {
            // Return NO_FUEL observation for requested fuel type
            return listOf(
                FuelObservation(
                    brand = name,
                    address = address,
                    fuelType = requestedFuelType,
                    price = 0.0,
                    available = false,
                    limitNote = null,
                    statusText = "АЗС закрыта"
                )
            )
        }

        val observations = mutableListOf<FuelObservation>()

        // Check each line from line 3 onwards for fuel pairs
        for (i in 2 until lines.size) {
            val line = lines[i]
            val lineLower = line.lowercase()

            val fuelType = when {
                lineLower.contains("аи-92") || lineLower.contains("аи92") -> "АИ-92"
                lineLower.contains("аи-95") || lineLower.contains("аи95") -> "АИ-95"
                lineLower.contains("аи-98") || lineLower.contains("аи98") -> "АИ-98"
                lineLower.contains("аи-100") || lineLower.contains("аи100") -> "АИ-100"
                lineLower.contains("дт") || lineLower.contains("дизель") -> "ДТ"
                else -> null
            }

            if (fuelType != null) {
                val isNotAvailable = lineLower.contains("отсутствует") ||
                        lineLower.contains("возможно отсутствует") ||
                        lineLower.contains("нет")

                val priceRegex = Regex("(\\d+[.,]\\d{1,2})\\s*р")
                val priceMatch = priceRegex.find(line)
                val price = if (isNotAvailable) 0.0 else (priceMatch?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull() ?: 0.0)

                val limitRegex = Regex("(лимит(?:\\s+до)?\\s+\\d+\\s*л)", RegexOption.IGNORE_CASE)
                val limitMatch = limitRegex.find(line)
                val limitNote = limitMatch?.value?.trim()

                if (fuelType.equals(requestedFuelType, ignoreCase = true) || requestedFuelType.isBlank()) {
                    observations.add(
                        FuelObservation(
                            brand = name,
                            address = address,
                            fuelType = fuelType,
                            price = price,
                            available = !isNotAvailable,
                            limitNote = limitNote,
                            statusText = if (isNotAvailable) "Отсутствует" else "Доступно"
                        )
                    )
                }
            }
        }

        return observations
    }

    private fun parseRowData(
        brandText: String,
        addressText: String,
        statusAndPriceText: String,
        fuelType: String
    ): FuelObservation? {
        val brand = brandText.trim()
        val address = addressText.trim()

        if (brand.isBlank() || address.isBlank()) return null
        if (brand.equals("АЗС", true) || brand.equals("Бренд", true) || address.equals("Адрес", true)) return null

        val lowerStatus = statusAndPriceText.lowercase()

        val isNoFuel = lowerStatus.contains("отсутствует") || lowerStatus.contains("нет в наличии") || lowerStatus.contains("нет топлива") || lowerStatus.contains("азс закрыта")

        val limitRegex = Regex("(лимит(?:\\s+до)?\\s+\\d+\\s*л(?:итров)?)", RegexOption.IGNORE_CASE)
        val limitMatch = limitRegex.find(statusAndPriceText)
        val limitNote = limitMatch?.value?.trim() ?: if (lowerStatus.contains("лимит")) statusAndPriceText.trim() else null

        val priceRegex = Regex("(\\d+[.,]\\d{1,2})")
        val priceMatch = priceRegex.find(statusAndPriceText)
        val parsedPrice = priceMatch?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull() ?: 0.0

        val available = !isNoFuel
        val finalPrice = if (isNoFuel) 0.0 else parsedPrice
        val statusText = when {
            isNoFuel -> "Отсутствует"
            limitNote != null -> limitNote
            else -> "В наличии"
        }

        return FuelObservation(
            brand = brand,
            address = address,
            fuelType = fuelType,
            price = finalPrice,
            available = available,
            limitNote = limitNote,
            statusText = statusText
        )
    }

    private fun cleanHtmlText(html: String): String {
        return html.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<p[^>]*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<div[^>]*>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .split("\n")
            .map { line -> line.replace(Regex("[ \\t]+"), " ").trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .trim()
    }
}

@Singleton
class RussiabaseProviderImpl @Inject constructor(
    private val httpClient: OkHttpClient,
    @ApplicationContext private val context: Context?
) : RussiabaseProvider {

    constructor(httpClient: OkHttpClient) : this(httpClient, null)

    companion object {
        private const val TAG = "RussiabaseProvider"
        private const val BASE_URL = "https://russiabase.ru/prices"
        private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes
        private const val CACHE_FILE_NAME = "russiabase_cache.json"
        private const val TIMEOUT_SECONDS = 3L
    }

    private class CacheEntry(
        val timestamp: Long,
        val observations: List<FuelObservation>
    )

    private val inMemoryCache = ConcurrentHashMap<String, CacheEntry>()

    private val russiabaseHttpClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun fetchObservations(
        citySlug: String,
        fuels: List<String>,
        lat: Double?,
        lon: Double?
    ): List<FuelObservation> = withContext(Dispatchers.IO) {
        val normalizedCity = citySlug.trim().lowercase()

        val raionId = getRaionForCityOrCoords(normalizedCity, lat, lon) ?: run {
            Timber.tag(TAG).d("City %s (lat=%s, lon=%s) not mapped in russiabase_regions.json, skipping", normalizedCity, lat, lon)
            return@withContext emptyList()
        }

        val cacheKey = "${normalizedCity}_$raionId"
        val now = System.currentTimeMillis()

        // 1. Check in-memory cache
        inMemoryCache[cacheKey]?.let { entry ->
            if (now - entry.timestamp < CACHE_TTL_MS) {
                Timber.tag(TAG).d("Returning in-memory cached Russiabase observations (%d items)", entry.observations.size)
                return@withContext entry.observations
            }
        }

        // 2. Check persistent disk cache
        val diskEntry = readFromDiskCache(cacheKey)
        if (diskEntry != null) {
            inMemoryCache[cacheKey] = diskEntry
            if (now - diskEntry.timestamp < CACHE_TTL_MS) {
                Timber.tag(TAG).d("Returning disk cached Russiabase observations (%d items)", diskEntry.observations.size)
                return@withContext diskEntry.observations
            }
        }

        // 3. Polite Network Fetch: max 2 fuel requests (e.g., ai95 + dt)
        val fuelsToFetch = fuels.take(2).ifEmpty { listOf("ai95", "dt") }
        val allObservations = mutableListOf<FuelObservation>()

        try {
            for (mark in fuelsToFetch) {
                val url = "$BASE_URL?raion=$raionId&mark=$mark"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) Chrome/126")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "ru")
                    .build()

                russiabaseHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val html = response.body?.string().orEmpty()
                        val parsed = RussiabaseHtmlParser.parseHtml(html, mark)
                        allObservations.addAll(parsed)
                    } else {
                        val bodySnippet = try {
                            response.body?.string()?.take(200) ?: ""
                        } catch (e: Exception) {
                            ""
                        }
                        Timber.tag(TAG).e("Russiabase request failed for mark %s: HTTP %d, body=%s", mark, response.code, bodySnippet)
                    }
                }
            }

            if (allObservations.isNotEmpty()) {
                val entry = CacheEntry(now, allObservations)
                inMemoryCache[cacheKey] = entry
                writeToDiskCache(cacheKey, entry)
                Timber.tag(TAG).i("Successfully fetched %d Russiabase observations for %s", allObservations.size, normalizedCity)
                return@withContext allObservations
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Error fetching Russiabase data for city %s", normalizedCity)
        }

        // 4. Fallback to expired cache if available
        val fallbackEntry = inMemoryCache[cacheKey] ?: diskEntry
        if (fallbackEntry != null) {
            Timber.tag(TAG).w("Network fetch failed, returning expired Russiabase cache (%d items)", fallbackEntry.observations.size)
            return@withContext fallbackEntry.observations
        }

        emptyList()
    }

    private fun getRaionForCityOrCoords(citySlug: String, lat: Double?, lon: Double?): Int? {
        val context = this.context
        if (context == null) {
            if (citySlug == "chelyabinsk" || citySlug == "nearby" || citySlug == "рядом" || citySlug.isBlank()) return 468
            if (lat != null && lon != null && lat in 54.8..55.5 && lon in 61.0..61.8) return 468
            return null
        }

        return try {
            val jsonString = context.assets.open("russiabase_regions.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)

            val isNearbyMode = citySlug.isBlank() || citySlug == "nearby" || citySlug == "рядом" || citySlug.contains("район")

            if (!isNearbyMode && root.has(citySlug)) {
                val value = root.get(citySlug)
                return when (value) {
                    is Int -> value
                    is JSONObject -> value.optInt("raion")
                    else -> null
                }
            }

            // If nearby mode or citySlug not found directly, check by lat/lon against bboxes
            if (lat != null && lon != null) {
                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = root.get(key)
                    if (value is JSONObject && value.has("bbox") && value.has("raion")) {
                        val bboxArr = value.getJSONArray("bbox")
                        if (bboxArr.length() >= 4) {
                            val minLat = bboxArr.getDouble(0)
                            val minLon = bboxArr.getDouble(1)
                            val maxLat = bboxArr.getDouble(2)
                            val maxLon = bboxArr.getDouble(3)
                            if (lat in minLat..maxLat && lon in minLon..maxLon) {
                                return value.getInt("raion")
                            }
                        }
                    }
                }
            }

            if (citySlug == "chelyabinsk" || isNearbyMode) 468 else null
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Error reading russiabase_regions.json asset")
            if (citySlug == "chelyabinsk" || citySlug == "nearby" || citySlug == "рядом" || citySlug.isBlank()) 468 else null
        }
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
            val obsArray = item.optJSONArray("observations") ?: JSONArray()
            val observations = ArrayList<FuelObservation>(obsArray.length())
            for (i in 0 until obsArray.length()) {
                val obsObj = obsArray.getJSONObject(i)
                observations.add(
                    FuelObservation(
                        brand = obsObj.optString("brand", ""),
                        address = obsObj.optString("address", ""),
                        fuelType = obsObj.optString("fuelType", ""),
                        price = obsObj.optDouble("price", 0.0),
                        available = obsObj.optBoolean("available", true),
                        limitNote = obsObj.optString("limitNote").takeIf { it.isNotBlank() },
                        statusText = obsObj.optString("statusText", "")
                    )
                )
            }
            CacheEntry(timestamp, observations)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Error reading Russiabase disk cache")
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
            val obsArray = JSONArray()
            for (obs in entry.observations) {
                val obsObj = JSONObject()
                obsObj.put("brand", obs.brand)
                obsObj.put("address", obs.address)
                obsObj.put("fuelType", obs.fuelType)
                obsObj.put("price", obs.price)
                obsObj.put("available", obs.available)
                obsObj.put("limitNote", obs.limitNote ?: "")
                obsObj.put("statusText", obs.statusText)
                obsArray.put(obsObj)
            }
            item.put("observations", obsArray)
            root.put(cacheKey, item)

            file.writeText(root.toString())
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Error writing Russiabase disk cache")
        }
    }
}
