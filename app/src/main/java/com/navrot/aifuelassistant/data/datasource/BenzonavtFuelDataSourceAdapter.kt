package com.navrot.aifuelassistant.data.datasource

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.domain.ingestion.FuelDataSourceAdapter
import com.navrot.aifuelassistant.domain.ingestion.FuelSourceRequest
import com.navrot.aifuelassistant.domain.ingestion.FuelSourceResult
import com.navrot.aifuelassistant.domain.ingestion.FuelSourceStatus
import com.navrot.aifuelassistant.domain.ingestion.IngestionObservation
import com.navrot.aifuelassistant.domain.ingestion.SourceIngestionMetrics
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Raw DTO representation of Benzonavt city-prices response.
 */
data class BenzonavtResponseDto(
    val city: String?,
    val prices: List<BenzonavtPriceItemDto>?,
    val sourcesUsed: List<String>?,
    val updatedAt: String?,
    val count: Int?
)

data class BenzonavtPriceItemDto(
    val code: String?,
    val median: Double?,
    val min: Double?,
    val max: Double?,
    val sources: List<BenzonavtSourceDto>?,
    val available: Boolean? = null,
    val noFuel: Boolean? = null
)

data class BenzonavtSourceDto(
    val name: String?,
    val price: Double?
)

/**
 * Parser for Benzonavt raw JSON responses into [BenzonavtResponseDto].
 */
object BenzonavtParser {

    fun parseJson(jsonString: String): BenzonavtResponseDto {
        if (jsonString.isBlank()) {
            throw IllegalArgumentException("Response body is empty or blank")
        }
        val root = JSONObject(jsonString)

        val city = if (root.has("city") && !root.isNull("city")) root.optString("city").takeIf { it.isNotBlank() } else null
        val updatedAt = if (root.has("updatedAt") && !root.isNull("updatedAt")) root.optString("updatedAt").takeIf { it.isNotBlank() } else null
        val count = if (root.has("count") && !root.isNull("count")) root.optInt("count") else null

        val pricesArray = root.optJSONArray("prices")
            ?: throw IllegalArgumentException("Missing or invalid 'prices' array in response")

        val prices = ArrayList<BenzonavtPriceItemDto>(pricesArray.length())
        for (i in 0 until pricesArray.length()) {
            val itemObj = pricesArray.optJSONObject(i) ?: continue
            val code = if (itemObj.has("code") && !itemObj.isNull("code")) itemObj.optString("code").takeIf { it.isNotBlank() } else null

            val median = if (itemObj.has("median") && !itemObj.isNull("median")) itemObj.optDouble("median") else null
            val min = if (itemObj.has("min") && !itemObj.isNull("min")) itemObj.optDouble("min") else null
            val max = if (itemObj.has("max") && !itemObj.isNull("max")) itemObj.optDouble("max") else null

            val available = if (itemObj.has("available") && !itemObj.isNull("available")) itemObj.optBoolean("available") else null
            val noFuel = if (itemObj.has("noFuel") && !itemObj.isNull("noFuel")) {
                itemObj.optBoolean("noFuel")
            } else if (itemObj.has("no_fuel") && !itemObj.isNull("no_fuel")) {
                itemObj.optBoolean("no_fuel")
            } else null

            val sourcesArray = itemObj.optJSONArray("sources")
            val sources = if (sourcesArray != null) {
                val list = ArrayList<BenzonavtSourceDto>(sourcesArray.length())
                for (j in 0 until sourcesArray.length()) {
                    val srcObj = sourcesArray.optJSONObject(j) ?: continue
                    val name = if (srcObj.has("name") && !srcObj.isNull("name")) srcObj.optString("name").takeIf { it.isNotBlank() } else null
                    val price = if (srcObj.has("price") && !srcObj.isNull("price")) srcObj.optDouble("price") else null
                    list.add(BenzonavtSourceDto(name, price))
                }
                list
            } else null

            prices.add(
                BenzonavtPriceItemDto(
                    code = code,
                    median = median,
                    min = min,
                    max = max,
                    sources = sources,
                    available = available,
                    noFuel = noFuel
                )
            )
        }

        val sourcesUsedArray = root.optJSONArray("sourcesUsed")
        val sourcesUsed = if (sourcesUsedArray != null) {
            val list = ArrayList<String>(sourcesUsedArray.length())
            for (k in 0 until sourcesUsedArray.length()) {
                val srcName = sourcesUsedArray.optString(k)
                if (srcName.isNotBlank()) list.add(srcName)
            }
            list
        } else null

        return BenzonavtResponseDto(
            city = city,
            prices = prices,
            sourcesUsed = sourcesUsed,
            updatedAt = updatedAt,
            count = count
        )
    }

    fun parseIsoTimestamp(isoString: String?): Long? {
        if (isoString.isNullOrBlank()) return null
        return try {
            Instant.parse(isoString.trim()).toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }
    }
}

/**
 * Concrete [FuelDataSourceAdapter] implementation for Benzonavt fuel price aggregator.
 *
 * Source Granularity: CITY_LEVEL
 * Does not map aggregate city prices to individual station IDs or pretend station-level identity.
 */
@Singleton
class BenzonavtFuelDataSourceAdapter @Inject constructor(
    private val httpClient: OkHttpClient,
    private val baseUrl: String = BASE_URL
) : FuelDataSourceAdapter {

    companion object {
        private const val TAG = "BenzonavtAdapter"
        const val BASE_URL = "https://ai-fuel-proxy.navrot73.workers.dev/city-prices"
        private const val DEFAULT_CITY = "chelyabinsk"
        val SOURCE_GRANULARITY = com.navrot.aifuelassistant.domain.capability.SourceCapabilityRegistry.getCapability(FuelDataSource.BENZONAVT).granularity.name
    }

    override val sourceId: FuelDataSource = FuelDataSource.BENZONAVT

    val capability: com.navrot.aifuelassistant.domain.capability.SourceCapabilityDescriptor
        get() = com.navrot.aifuelassistant.domain.capability.SourceCapabilityRegistry.getCapability(sourceId)

    override suspend fun fetch(request: FuelSourceRequest): FuelSourceResult = withContext(Dispatchers.IO) {
        val targetCity = request.targetCity?.trim()?.lowercase() ?: DEFAULT_CITY
        val receivedAt = System.currentTimeMillis()

        val adapterHttpClient = httpClient.newBuilder()
            .connectTimeout(request.timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(request.timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(request.timeoutMs, TimeUnit.MILLISECONDS)
            .build()

        val url = baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("city", targetCity)
            ?.build()

        if (url == null) {
            return@withContext FuelSourceResult(
                sourceId = FuelDataSource.BENZONAVT,
                status = FuelSourceStatus.FAILED,
                errorMessage = "Invalid endpoint URL",
                fetchedAt = receivedAt
            )
        }

        val httpRequest = Request.Builder()
            .url(url)
            .header("User-Agent", "AIFuelAssistant/1.0")
            .header("Accept", "application/json")
            .build()

        val rawResponseBody: String? = try {
            withTimeoutOrNull(request.timeoutMs) {
                adapterHttpClient.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.tag(TAG).w("HTTP %d fetching city %s", response.code, targetCity)
                        return@use "HTTP_ERROR:${response.code}"
                    }
                    response.body?.string()
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Network exception fetching Benzonavt city %s", targetCity)
            return@withContext FuelSourceResult(
                sourceId = FuelDataSource.BENZONAVT,
                status = FuelSourceStatus.FAILED,
                errorMessage = "Network exception: ${e.message}",
                fetchedAt = receivedAt
            )
        }

        if (rawResponseBody == null) {
            return@withContext FuelSourceResult(
                sourceId = FuelDataSource.BENZONAVT,
                status = FuelSourceStatus.FAILED,
                errorMessage = "Request timed out after ${request.timeoutMs}ms",
                fetchedAt = receivedAt
            )
        }

        if (rawResponseBody.startsWith("HTTP_ERROR:")) {
            val code = rawResponseBody.removePrefix("HTTP_ERROR:")
            return@withContext FuelSourceResult(
                sourceId = FuelDataSource.BENZONAVT,
                status = FuelSourceStatus.FAILED,
                errorMessage = "HTTP error $code",
                fetchedAt = receivedAt
            )
        }

        val dto: BenzonavtResponseDto = try {
            BenzonavtParser.parseJson(rawResponseBody)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to parse Benzonavt JSON")
            return@withContext FuelSourceResult(
                sourceId = FuelDataSource.BENZONAVT,
                status = FuelSourceStatus.FAILED,
                errorMessage = "Malformed response: ${e.message}",
                fetchedAt = receivedAt
            )
        }

        val items = dto.prices ?: emptyList()
        val recordsReceived = items.size
        var recordsParsed = 0
        var invalidRecords = 0
        var observationsRejected = 0

        val observedAt = BenzonavtParser.parseIsoTimestamp(dto.updatedAt)
        val rawObservations = ArrayList<IngestionObservation>()

        for (item in items) {
            val rawCode = item.code
            if (rawCode.isNullOrBlank()) {
                invalidRecords++
                continue
            }

            val canonicalFuelType = FuelSourceRequest.normalizeFuelType(rawCode)
            if (canonicalFuelType == null) {
                invalidRecords++
                observationsRejected++
                Timber.tag(TAG).d("Unsupported fuel type rejected: '%s'", rawCode)
                continue
            }

            if (!request.normalizedFuelTypes.contains(canonicalFuelType)) {
                continue
            }

            val rawPrice = item.median ?: item.min ?: item.max
            val price = if (rawPrice != null && rawPrice > 0.0) rawPrice else null

            // Strict availability semantics: UNKNOWN must remain UNKNOWN when explicit availability field is absent/null
            val availability = when {
                item.noFuel == true || item.available == false -> FuelAvailabilityStatus.UNAVAILABLE
                item.available == true -> FuelAvailabilityStatus.AVAILABLE
                else -> FuelAvailabilityStatus.UNKNOWN
            }

            val extId = "benzonavt:city:${targetCity}:${canonicalFuelType}"
            val rawRef = "benzonavt:${targetCity}:${canonicalFuelType}"

            try {
                val rawObs = IngestionObservation(
                    sourceId = FuelDataSource.BENZONAVT,
                    externalStationId = extId,
                    stationName = "Benzonavt $targetCity Aggregate",
                    address = targetCity,
                    fuelType = rawCode,
                    availability = availability,
                    price = price,
                    observedAt = observedAt,
                    receivedAt = receivedAt,
                    rawReference = rawRef
                )
                rawObservations.add(rawObs)
                recordsParsed++
            } catch (e: Exception) {
                invalidRecords++
                observationsRejected++
                Timber.tag(TAG).w(e, "Failed creating IngestionObservation for %s", rawCode)
            }
        }

        // CITY_LEVEL source: station identity is absent -> stationsMatched MUST be 0.
        // Aggregate observations remain traceable in rawObservations.
        val metrics = SourceIngestionMetrics(
            recordsReceived = recordsReceived,
            recordsParsed = recordsParsed,
            stationsMatched = 0,
            stationsUnmatched = rawObservations.size,
            invalidRecords = invalidRecords,
            observationsCreated = 0,
            observationsRejected = observationsRejected
        )

        val status = when {
            rawObservations.isEmpty() && recordsReceived > 0 -> FuelSourceStatus.DEGRADED
            rawObservations.isEmpty() -> FuelSourceStatus.HEALTHY
            invalidRecords > 0 -> FuelSourceStatus.DEGRADED
            else -> FuelSourceStatus.HEALTHY
        }

        return@withContext FuelSourceResult(
            sourceId = FuelDataSource.BENZONAVT,
            status = status,
            observations = emptyList(), // No fake station-level observations generated
            rawObservations = rawObservations,
            metrics = metrics,
            fetchedAt = receivedAt
        )
    }
}
