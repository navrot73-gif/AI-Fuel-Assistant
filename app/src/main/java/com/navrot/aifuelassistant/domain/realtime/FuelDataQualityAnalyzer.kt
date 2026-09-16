package com.navrot.aifuelassistant.domain.realtime

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.domain.intelligence.FuelFreshness
import com.navrot.aifuelassistant.domain.intelligence.FuelSourceObservation
import com.navrot.aifuelassistant.domain.intelligence.SourceReliability
import com.navrot.aifuelassistant.domain.intelligence.StationFuelSnapshot
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus

object FuelDataQualityAnalyzer {

    fun analyze(
        snapshot: StationFuelSnapshot,
        observations: List<FuelSourceObservation> = snapshot.sources,
        now: Long = System.currentTimeMillis()
    ): FuelDataQuality {
        val stationId = snapshot.stationId
        val fuelType = snapshot.fuelType

        val matchingObs = observations.filter { it.stationId == stationId && it.fuelType == fuelType }
        val sources = matchingObs.map { it.source }.distinct()
        val sourceCount = sources.size

        val lastUpdated = snapshot.lastUpdatedAt ?: matchingObs.mapNotNull { it.observedAt }.filter { it > 0L }.maxOrNull()
        val ageMinutes = lastUpdated?.let { maxOf(0L, (now - it) / 60000L) }

        // Independent Freshness Calculation
        val latestAvailabilityObsAt = matchingObs.filter { it.availability != FuelAvailabilityStatus.UNKNOWN }
            .mapNotNull { it.observedAt }
            .filter { it > 0L }
            .maxOrNull()

        val latestPriceObsAt = matchingObs.filter { it.price != null && it.price > 0.0 }
            .mapNotNull { it.observedAt }
            .filter { it > 0L }
            .maxOrNull()

        val availabilityFreshness = FuelFreshness.calculateFreshness(latestAvailabilityObsAt, now)
        val priceFreshness = FuelFreshness.calculateFreshness(latestPriceObsAt, now)
        val freshness = snapshot.freshness // snapshot/general freshness

        val reliableSourceCount = matchingObs.count {
            SourceReliability.getReliability(it.source).reliability >= FuelDataQualityPolicy.RELIABLE_SOURCE_MIN_SCORE
        }

        val primaryObs = matchingObs.sortedByDescending { SourceReliability.getReliability(it.source).reliability * SourceReliability.getReliability(it.source).freshnessWeight }
            .firstOrNull()
        val primarySource = primaryObs?.source
        val supportingSources = sources.filter { it != primarySource }

        val conflicts = mutableListOf<FuelObservationConflict>()
        val warnings = mutableListOf<String>()

        // 1. Availability conflict detection
        val availables = matchingObs.filter { it.availability == FuelAvailabilityStatus.AVAILABLE }
        val unavailables = matchingObs.filter { it.availability == FuelAvailabilityStatus.UNAVAILABLE }
        if (availables.isNotEmpty() && unavailables.isNotEmpty()) {
            conflicts.add(
                FuelObservationConflict(
                    stationId = stationId,
                    fuelType = fuelType,
                    observations = matchingObs,
                    detectedAt = now,
                    conflictType = ConflictType.AVAILABILITY_CONFLICT
                )
            )
            warnings.add("Источники расходятся по наличию топлива")
        }

        // 2. Price conflict detection
        val validPrices = matchingObs.mapNotNull { it.price }.filter { it > 0.0 }
        if (validPrices.size >= 2) {
            val minP = validPrices.minOrNull()!!
            val maxP = validPrices.maxOrNull()!!
            if (minP > 0 && (maxP - minP) / minP > FuelDataQualityPolicy.PRICE_CONFLICT_TOLERANCE_PERCENT) {
                conflicts.add(
                    FuelObservationConflict(
                        stationId = stationId,
                        fuelType = fuelType,
                        observations = matchingObs,
                        detectedAt = now,
                        conflictType = ConflictType.PRICE_CONFLICT
                    )
                )
                warnings.add("Различаются данные по цене между источниками")
            }
        }

        val conflict = conflicts.isNotEmpty() || snapshot.isConflict
        val agreement = sourceCount >= 2 && !conflict

        // Source Reliability average
        val avgSourceReliability = if (matchingObs.isNotEmpty()) {
            matchingObs.map { SourceReliability.getReliability(it.source).reliability }.average()
        } else {
            0.0
        }

        // Completeness calculation
        var completenessPoints = 0.0
        if (snapshot.availability != FuelAvailabilityStatus.UNKNOWN) completenessPoints += 0.4
        if (snapshot.price != null && snapshot.price > 0.0) completenessPoints += 0.4
        if (lastUpdated != null) completenessPoints += 0.2
        val completeness = completenessPoints.coerceIn(0.0, 1.0)

        // Quality Warnings
        if (snapshot.availability == FuelAvailabilityStatus.UNKNOWN) {
            warnings.add("Наличие топлива не подтверждено")
        }
        if (snapshot.price == null) {
            warnings.add("Цена на топливо отсутствует")
        }

        if (availabilityFreshness == FuelFreshness.STALE) {
            warnings.add("Данные по наличию устарели (>6 ч)")
        }
        if (priceFreshness == FuelFreshness.STALE) {
            warnings.add("Данные по цене устарели (>6 ч)")
        } else if (priceFreshness == FuelFreshness.AGING) {
            warnings.add("Данные по цене частично устарели (1–6 ч)")
        }

        // Determine Quality Level
        val qualityLevel = when {
            matchingObs.isEmpty() || (snapshot.availability == FuelAvailabilityStatus.UNKNOWN && snapshot.price == null) -> {
                FuelDataQualityLevel.UNKNOWN
            }

            !conflict &&
            (availabilityFreshness == FuelFreshness.VERY_FRESH || availabilityFreshness == FuelFreshness.FRESH) &&
            priceFreshness != FuelFreshness.STALE &&
            avgSourceReliability >= 0.70 &&
            snapshot.availability != FuelAvailabilityStatus.UNKNOWN &&
            snapshot.price != null -> {
                FuelDataQualityLevel.HIGH
            }

            !conflict &&
            availabilityFreshness != FuelFreshness.STALE &&
            completeness >= 0.60 -> {
                FuelDataQualityLevel.MEDIUM
            }

            else -> {
                FuelDataQualityLevel.LOW
            }
        }

        val confidence = snapshot.confidence

        return FuelDataQuality(
            stationId = stationId,
            fuelType = fuelType,
            availability = snapshot.availability,
            price = snapshot.price,
            lastUpdated = lastUpdated,
            ageMinutes = ageMinutes,
            freshness = freshness,
            availabilityFreshness = availabilityFreshness,
            priceFreshness = priceFreshness,
            sourceCount = sourceCount,
            reliableSourceCount = reliableSourceCount,
            agreement = agreement,
            conflict = conflict,
            conflicts = conflicts,
            sourceReliability = avgSourceReliability,
            completeness = completeness,
            qualityLevel = qualityLevel,
            confidence = confidence,
            primarySource = primarySource,
            supportingSources = supportingSources,
            warnings = warnings.distinct()
        )
    }
}
