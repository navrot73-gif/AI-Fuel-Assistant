package com.navrot.aifuelassistant.domain.intelligence

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.data.model.isKnownClosed
import com.navrot.aifuelassistant.domain.recommendation.RecommendationConfidence
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import com.navrot.aifuelassistant.domain.reliability.PriceReliabilityCalculator
import timber.log.Timber

object FuelIntelligenceResolver {

    fun isObservationExpired(obs: FuelSourceObservation, now: Long): Boolean {
        val observedAt = obs.observedAt ?: return true
        if (observedAt <= 0L) return true
        val diffMs = maxOf(0L, now - observedAt)
        val threshold = if (obs.source == FuelDataSource.RUSSIABASE) {
            PriceReliabilityCalculator.RUSSIABASE_FRESHNESS_THRESHOLD_MS
        } else {
            PriceReliabilityCalculator.FRESHNESS_THRESHOLD_MS
        }
        return diffMs > threshold
    }

    fun resolve(
        observations: List<FuelSourceObservation>,
        stationId: Int,
        fuelType: String,
        now: Long = System.currentTimeMillis()
    ): StationFuelSnapshot {
        // Filter observations matching stationId and fuelType, enforcing capability registry boundary
        val eligibleObservations = observations.filter {
            com.navrot.aifuelassistant.domain.capability.SourceCapabilityRegistry.canFeedStationFuelSnapshot(it.source)
        }
        val matching = eligibleObservations.filter { it.stationId == stationId && it.fuelType == fuelType }

        if (matching.isEmpty()) {
            return StationFuelSnapshot(
                stationId = stationId,
                fuelType = fuelType,
                availability = FuelAvailabilityStatus.UNKNOWN,
                price = null,
                freshness = FuelFreshness.UNKNOWN,
                confidence = RecommendationConfidence.UNKNOWN,
                sourceCount = 0,
                confirmingSourceCount = 0,
                conflictingSourceCount = 0,
                isConflict = false,
                conflictReason = null,
                lastUpdatedAt = null,
                sources = emptyList()
            )
        }

        // Distinct sources count
        val sourceCount = matching.size

        // Find latest timestamp among matching observations
        val timestamps = matching.mapNotNull { it.observedAt }.filter { it > 0L }
        val lastUpdatedAt = timestamps.maxOrNull()
        val freshness = FuelFreshness.calculateFreshness(lastUpdatedAt, now)

        // Effective availability taking expiration thresholds (8h standard / 24h Russiabase) into account
        val effectiveObservations = matching.map { obs ->
            if (isObservationExpired(obs, now)) {
                obs.copy(availability = FuelAvailabilityStatus.UNKNOWN)
            } else {
                obs
            }
        }

        val availableObs = effectiveObservations.filter { it.availability == FuelAvailabilityStatus.AVAILABLE }
        val unavailableObs = effectiveObservations.filter { it.availability == FuelAvailabilityStatus.UNAVAILABLE }

        val availCount = availableObs.size
        val unavailCount = unavailableObs.size

        val isConflict = availCount > 0 && unavailCount > 0
        val conflictReason = if (isConflict) "Источники расходятся" else null

        // Calculate winning availability status
        val availability: FuelAvailabilityStatus
        val confirmingCount: Int
        val conflictingCount: Int

        if (availCount == 0 && unavailCount == 0) {
            availability = FuelAvailabilityStatus.UNKNOWN
            confirmingCount = 0
            conflictingCount = 0
        } else if (!isConflict) {
            if (availCount > 0) {
                availability = FuelAvailabilityStatus.AVAILABLE
                confirmingCount = availCount
                conflictingCount = 0
            } else {
                availability = FuelAvailabilityStatus.UNAVAILABLE
                confirmingCount = unavailCount
                conflictingCount = 0
            }
        } else {
            // Conflict resolution: compare weighted scores of AVAILABLE vs UNAVAILABLE
            val availScore = availableObs.sumOf { computeObservationWeight(it, now) }
            val unavailScore = unavailableObs.sumOf { computeObservationWeight(it, now) }

            if (availScore >= unavailScore) {
                availability = FuelAvailabilityStatus.AVAILABLE
                confirmingCount = availCount
                conflictingCount = unavailCount
            } else {
                availability = FuelAvailabilityStatus.UNAVAILABLE
                confirmingCount = unavailCount
                conflictingCount = availCount
            }

            Timber.d(
                "FUEL_INTELLIGENCE_CONFLICT stationId=%d fuel=%s sources=%d available=%d unavailable=%d winner=%s",
                stationId, fuelType, sourceCount, availCount, unavailCount, availability.name
            )
        }

        // Price resolution: pick price from most reliable/fresh observation with a valid price (> 0.0)
        val validPriceObs = matching.filter { it.price != null && it.price > 0.0 }
            .sortedByDescending { computeObservationWeight(it, now) }
        val price = validPriceObs.firstOrNull()?.price

        // Confidence calculation
        val confidence = calculateConfidence(
            matching = matching,
            availability = availability,
            freshness = freshness,
            isConflict = isConflict,
            sourceCount = sourceCount,
            confirmingCount = confirmingCount,
            now = now
        )

        Timber.d(
            "FUEL_INTELLIGENCE stationId=%d fuel=%s status=%s freshness=%s confidence=%s sources=%d confirming=%d conflicting=%d",
            stationId, fuelType, availability.name, freshness.name, confidence.name, sourceCount, confirmingCount, conflictingCount
        )

        return StationFuelSnapshot(
            stationId = stationId,
            fuelType = fuelType,
            availability = availability,
            price = price,
            freshness = freshness,
            confidence = confidence,
            sourceCount = sourceCount,
            confirmingSourceCount = confirmingCount,
            conflictingSourceCount = conflictingCount,
            isConflict = isConflict,
            conflictReason = conflictReason,
            lastUpdatedAt = lastUpdatedAt,
            sources = matching
        )
    }

    private fun computeObservationWeight(obs: FuelSourceObservation, now: Long): Double {
        val rel = SourceReliability.getReliability(obs.source)
        val obsFreshness = FuelFreshness.calculateFreshness(obs.observedAt, now)
        val freshnessFactor = when (obsFreshness) {
            FuelFreshness.VERY_FRESH -> 1.0
            FuelFreshness.FRESH -> 0.85
            FuelFreshness.AGING -> 0.5
            FuelFreshness.STALE -> 0.2
            FuelFreshness.UNKNOWN -> 0.3
        }
        return rel.reliability * rel.freshnessWeight * freshnessFactor
    }

    private fun calculateConfidence(
        matching: List<FuelSourceObservation>,
        availability: FuelAvailabilityStatus,
        freshness: FuelFreshness,
        isConflict: Boolean,
        sourceCount: Int,
        confirmingCount: Int,
        now: Long
    ): RecommendationConfidence {
        if (matching.isEmpty() || availability == FuelAvailabilityStatus.UNKNOWN) {
            return RecommendationConfidence.UNKNOWN
        }

        // Stale contradictory or stale weak data can never be HIGH
        if (freshness == FuelFreshness.STALE) {
            return RecommendationConfidence.LOW
        }

        if (isConflict) {
            // Conflicting sources cap confidence at MEDIUM or LOW
            return if (freshness == FuelFreshness.VERY_FRESH || freshness == FuelFreshness.FRESH) {
                RecommendationConfidence.MEDIUM
            } else {
                RecommendationConfidence.LOW
            }
        }

        // Check source reliability of confirming sources
        val maxSourceReliability = matching.maxOfOrNull { SourceReliability.getReliability(it.source).reliability } ?: 0.3

        // Unknown/DEMO source cannot reach HIGH confidence
        val isHighQualitySource = maxSourceReliability >= 0.85

        if (freshness == FuelFreshness.VERY_FRESH || freshness == FuelFreshness.FRESH) {
            if (confirmingCount >= 2 && isHighQualitySource) {
                return RecommendationConfidence.HIGH
            }
            if (confirmingCount >= 1 && isHighQualitySource) {
                return RecommendationConfidence.HIGH
            }
            if (confirmingCount >= 1 && maxSourceReliability >= 0.70) {
                return RecommendationConfidence.MEDIUM
            }
            return RecommendationConfidence.LOW
        }

        if (freshness == FuelFreshness.AGING) {
            return if (isHighQualitySource && confirmingCount >= 1) {
                RecommendationConfidence.MEDIUM
            } else {
                RecommendationConfidence.LOW
            }
        }

        return RecommendationConfidence.LOW
    }

    /**
     * Converts a domain GasStation into a List<FuelSourceObservation> for resolution.
     */
    fun extractObservations(station: GasStation, now: Long = System.currentTimeMillis()): List<FuelSourceObservation> {
        val observations = mutableListOf<FuelSourceObservation>()

        for (fp in station.fuelTypes) {
            val timestamp = when {
                fp.updatedAt > 0L -> fp.updatedAt
                station.updatedAt > 0L -> station.updatedAt
                else -> null
            }

            val isRussiabase = fp.source == FuelDataSource.RUSSIABASE || station.dataSources.contains(FuelDataSource.RUSSIABASE)
            val isClosed = station.isKnownClosed()

            val threshold = if (isRussiabase) {
                PriceReliabilityCalculator.RUSSIABASE_FRESHNESS_THRESHOLD_MS
            } else {
                PriceReliabilityCalculator.FRESHNESS_THRESHOLD_MS
            }

            val isExpired = timestamp == null || (now - timestamp > threshold)

            val avail = when {
                isExpired -> FuelAvailabilityStatus.UNKNOWN
                !fp.available || (isRussiabase && isClosed) -> FuelAvailabilityStatus.UNAVAILABLE
                fp.available -> FuelAvailabilityStatus.AVAILABLE
                else -> FuelAvailabilityStatus.UNKNOWN
            }

            observations.add(
                FuelSourceObservation(
                    stationId = station.id,
                    fuelType = fp.type,
                    availability = avail,
                    price = fp.price.takeIf { it > 0.0 },
                    observedAt = timestamp,
                    source = fp.source,
                    referenceId = station.ref
                )
            )

        }

        return observations
    }

    /**
     * Helper to resolve snapshot directly from GasStation entity.
     */
    fun resolveSnapshot(
        station: GasStation,
        fuelType: String,
        now: Long = System.currentTimeMillis()
    ): StationFuelSnapshot {
        val obs = extractObservations(station, now)
        return resolve(obs, station.id, fuelType, now)
    }
}
