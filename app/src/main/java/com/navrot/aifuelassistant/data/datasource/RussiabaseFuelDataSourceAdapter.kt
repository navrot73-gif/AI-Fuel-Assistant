package com.navrot.aifuelassistant.data.datasource

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.domain.capability.SourceCapabilityRegistry
import com.navrot.aifuelassistant.domain.ingestion.FuelDataSourceAdapter
import com.navrot.aifuelassistant.domain.ingestion.FuelSourceRequest
import com.navrot.aifuelassistant.domain.ingestion.FuelSourceResult
import com.navrot.aifuelassistant.domain.ingestion.FuelSourceStatus
import com.navrot.aifuelassistant.domain.ingestion.IngestionObservation
import com.navrot.aifuelassistant.domain.ingestion.SourceIngestionMetrics
import com.navrot.aifuelassistant.domain.intelligence.FuelSourceObservation
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete [FuelDataSourceAdapter] implementation for Russiabase station-level real fuel data source.
 *
 * Source Granularity: STATION_LEVEL
 * Maps external Russiabase station observations to internal StationRegistry stations using brand + ref / address matching.
 */
@Singleton
class RussiabaseFuelDataSourceAdapter @Inject constructor(
    private val russiabaseProvider: RussiabaseProvider,
    private val stationCache: StationCache
) : FuelDataSourceAdapter {

    companion object {
        private const val TAG = "RussiabaseAdapter"
        private const val DEFAULT_CITY = "chelyabinsk"
    }

    override val sourceId: FuelDataSource = FuelDataSource.RUSSIABASE

    suspend fun fetchWithStationMapping(
        request: FuelSourceRequest,
        stationRegistry: List<GasStation>
    ): FuelSourceResult = withContext(Dispatchers.IO) {
        val targetCity = request.targetCity?.trim()?.lowercase() ?: DEFAULT_CITY
        val receivedAt = System.currentTimeMillis()

        if (!SourceCapabilityRegistry.canFeedStationFuelSnapshot(FuelDataSource.RUSSIABASE)) {
            return@withContext FuelSourceResult(
                sourceId = FuelDataSource.RUSSIABASE,
                status = FuelSourceStatus.FAILED,
                errorMessage = "Source RUSSIABASE is restricted by SourceCapabilityRegistry",
                fetchedAt = receivedAt
            )
        }

        val requestFuels = request.normalizedFuelTypes
        val rawFuelParams = requestFuels.map {
            when (it) {
                FuelSourceRequest.CANONICAL_AI92 -> "ai92"
                FuelSourceRequest.CANONICAL_AI95 -> "ai95"
                else -> it.lowercase()
            }
        }

        var isNetworkOrProviderError = false
        var exceptionErrorMessage: String? = null

        val observations = try {
            russiabaseProvider.fetchObservations(
                citySlug = targetCity,
                fuels = rawFuelParams
            )
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Error fetching observations from RussiabaseProvider for %s", targetCity)
            isNetworkOrProviderError = true
            exceptionErrorMessage = e.message ?: e.toString()
            emptyList()
        }

        if (isNetworkOrProviderError) {
            return@withContext FuelSourceResult(
                sourceId = FuelDataSource.RUSSIABASE,
                status = FuelSourceStatus.FAILED,
                errorMessage = "Network/Provider exception: $exceptionErrorMessage",
                fetchedAt = receivedAt
            )
        }

        val recordsReceived = observations.size
        var recordsParsed = 0
        var invalidRecords = 0
        var observationsRejected = 0

        val stationObservations = mutableListOf<FuelSourceObservation>()
        val rawObservations = mutableListOf<IngestionObservation>()

        val matchedStationIds = mutableSetOf<Int>()
        var unmatchedCount = 0

        for (obs in observations) {
            val canonicalFuelType = FuelSourceRequest.normalizeFuelType(obs.fuelType)
            if (canonicalFuelType == null) {
                invalidRecords++
                observationsRejected++
                continue
            }

            if (!requestFuels.contains(canonicalFuelType)) {
                continue
            }

            recordsParsed++

            val price = if (obs.price > 0.0) obs.price else null
            val availability = if (obs.available) {
                FuelAvailabilityStatus.AVAILABLE
            } else {
                FuelAvailabilityStatus.UNAVAILABLE
            }

            val obsRef = RussiabaseMatcher.extractRef(obs.brand)
            val extId = if (obsRef != null) {
                "russiabase:station:$obsRef:${obs.fuelType}"
            } else {
                "russiabase:addr:${obs.brand}:${obs.address}:${obs.fuelType}"
            }

            val rawRef = "russiabase:$targetCity:${obs.fuelType}"

            val rawObs = IngestionObservation(
                sourceId = FuelDataSource.RUSSIABASE,
                externalStationId = extId,
                stationName = obs.brand,
                address = obs.address,
                fuelType = obs.fuelType,
                availability = availability,
                price = price,
                observedAt = receivedAt,
                receivedAt = receivedAt,
                rawReference = rawRef
            )
            rawObservations.add(rawObs)

            val matchingStation = stationRegistry.firstOrNull { station ->
                RussiabaseMatcher.matchesBrandAndRefOrAddress(station, obs)
            }

            if (matchingStation != null) {
                matchedStationIds.add(matchingStation.id)
                stationObservations.add(
                    FuelSourceObservation(
                        stationId = matchingStation.id,
                        fuelType = canonicalFuelType,
                        availability = availability,
                        price = price,
                        observedAt = receivedAt,
                        source = FuelDataSource.RUSSIABASE,
                        referenceId = obsRef ?: matchingStation.ref
                    )
                )
            } else {
                unmatchedCount++
            }
        }

        val metrics = SourceIngestionMetrics(
            recordsReceived = recordsReceived,
            recordsParsed = recordsParsed,
            stationsMatched = matchedStationIds.size,
            stationsUnmatched = unmatchedCount,
            invalidRecords = invalidRecords,
            observationsCreated = stationObservations.size,
            observationsRejected = observationsRejected
        )

        val status = when {
            observations.isEmpty() -> FuelSourceStatus.HEALTHY
            stationObservations.isEmpty() && rawObservations.isNotEmpty() -> FuelSourceStatus.DEGRADED
            invalidRecords > 0 -> FuelSourceStatus.DEGRADED
            else -> FuelSourceStatus.HEALTHY
        }

        return@withContext FuelSourceResult(
            sourceId = FuelDataSource.RUSSIABASE,
            status = status,
            observations = stationObservations,
            rawObservations = rawObservations,
            metrics = metrics,
            fetchedAt = receivedAt
        )
    }

    override suspend fun fetch(request: FuelSourceRequest): FuelSourceResult {
        val registry = stationCache.loadFromCache() ?: emptyList()
        return fetchWithStationMapping(request, registry)
    }
}
