package com.navrot.aifuelassistant.domain.recommendation

import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import com.navrot.aifuelassistant.domain.reliability.PriceReliabilityCalculator
import com.navrot.aifuelassistant.geo.GeoUtils
import timber.log.Timber
import javax.inject.Inject

class BestStationUseCase @Inject constructor() {

    fun execute(
        stations: List<GasStation>,
        fuelType: String,
        userLat: Double? = null,
        userLon: Double? = null,
        refillLiters: Double = RecommendationPolicy.DEFAULT_REFILL_LITERS,
        consumptionLPer100km: Double = RecommendationPolicy.DEFAULT_CONSUMPTION_L_PER_100KM,
        currentTimeMs: Long = System.currentTimeMillis()
    ): BestStationResult {
        val evaluatedCount = stations.size
        if (stations.isEmpty()) {
            return BestStationResult(
                best = null,
                alternatives = emptyList(),
                evaluatedCount = 0
            )
        }

        val matchingFuelStations = stations.filter { s ->
            s.fuelTypes.any { it.type == fuelType }
        }

        if (matchingFuelStations.isEmpty()) {
            return BestStationResult(
                best = null,
                alternatives = emptyList(),
                evaluatedCount = evaluatedCount
            )
        }

        val hasUserLocation = userLat != null && userLon != null &&
                userLat != 0.0 && userLon != 0.0

        val prices = matchingFuelStations.mapNotNull { s ->
            s.fuelTypes.find { it.type == fuelType }?.price?.takeIf { it > 0.0 }
        }
        val minPrice = prices.minOrNull()
        val averagePrice = if (prices.isNotEmpty()) prices.average() else null

        val recommendations = matchingFuelStations.map { station ->
            val distanceKm = if (hasUserLocation) {
                GeoUtils.calculateDistance(userLat!!, userLon!!, station.latitude, station.longitude)
            } else null

            val score = StationScoring.calculateScore(station, fuelType, distanceKm, currentTimeMs)
            val estimatedTotalCost = StationScoring.calculateEstimatedTotalCost(
                station = station,
                fuelType = fuelType,
                distanceKm = distanceKm,
                refillLiters = refillLiters,
                consumptionLPer100km = consumptionLPer100km
            )
            val confidence = StationScoring.evaluateConfidence(station, fuelType, currentTimeMs)
            val reasons = StationScoring.evaluateReasons(
                station = station,
                fuelType = fuelType,
                distanceKm = distanceKm,
                minPrice = minPrice,
                averagePrice = averagePrice,
                currentTimeMs = currentTimeMs
            )

            StationRecommendation(
                station = station,
                score = score,
                estimatedTotalCost = estimatedTotalCost,
                reasons = reasons,
                confidence = confidence
            )
        }

        val sortedRecommendations = recommendations.sortedWith { a, b ->
            StationScoring.compareRecommendations(a, b, fuelType, currentTimeMs)
        }

        val bestCandidate = sortedRecommendations.firstOrNull { rec ->
            val avail = PriceReliabilityCalculator.calculateFuelAvailability(rec.station, fuelType, currentTimeMs)
            avail != FuelAvailabilityStatus.NO_FUEL
        }

        val alternatives = if (bestCandidate != null) {
            sortedRecommendations.filter { it.station.id != bestCandidate.station.id }
        } else {
            sortedRecommendations
        }

        val availableCount = matchingFuelStations.count { s ->
            PriceReliabilityCalculator.calculateFuelAvailability(s, fuelType, currentTimeMs) == FuelAvailabilityStatus.AVAILABLE
        }
        val unknownCount = matchingFuelStations.count { s ->
            PriceReliabilityCalculator.calculateFuelAvailability(s, fuelType, currentTimeMs) == FuelAvailabilityStatus.UNKNOWN
        }
        val withoutPriceCount = matchingFuelStations.count { s ->
            (s.fuelTypes.find { it.type == fuelType }?.price ?: 0.0) <= 0.0
        }
        val staleCount = matchingFuelStations.count { s ->
            val timestamp = s.fuelTypes.find { it.type == fuelType }?.updatedAt ?: s.updatedAt
            timestamp <= 0L || (currentTimeMs - timestamp > RecommendationPolicy.STALE_THRESHOLD_MS)
        }

        Timber.d(
            "STATION_INTELLIGENCE evaluated=%d available=%d unknown=%d without_price=%d stale=%d recommended=%s confidence=%s",
            evaluatedCount,
            availableCount,
            unknownCount,
            withoutPriceCount,
            staleCount,
            bestCandidate?.station?.id?.toString() ?: "none",
            bestCandidate?.confidence?.name ?: "NONE"
        )

        return BestStationResult(
            best = bestCandidate,
            alternatives = alternatives,
            evaluatedCount = evaluatedCount
        )
    }
}
