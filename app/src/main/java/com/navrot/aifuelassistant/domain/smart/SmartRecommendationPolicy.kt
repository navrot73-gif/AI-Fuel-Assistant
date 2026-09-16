package com.navrot.aifuelassistant.domain.smart

import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.domain.intelligence.FuelFreshness
import com.navrot.aifuelassistant.domain.intelligence.StationFuelSnapshot
import com.navrot.aifuelassistant.domain.personal.PersonalStationProfile
import com.navrot.aifuelassistant.domain.predictive.ConsumptionPrediction
import com.navrot.aifuelassistant.domain.predictive.PersonalStationLearning
import com.navrot.aifuelassistant.domain.recommendation.RecommendationConfidence
import com.navrot.aifuelassistant.domain.recommendation.RecommendationPolicy
import com.navrot.aifuelassistant.domain.recommendation.StationScoring
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import com.navrot.aifuelassistant.domain.reliability.PriceReliabilityCalculator
import kotlin.math.roundToInt

object SmartRecommendationPolicy {

    const val PERSONAL_WEIGHT = 2.0 // Weight multiplier for personal preference score reduction in final score

    fun isHardFilteredOut(snapshot: StationFuelSnapshot): Boolean {
        return snapshot.availability == FuelAvailabilityStatus.UNAVAILABLE ||
                snapshot.availability == FuelAvailabilityStatus.NO_FUEL
    }

    fun calculateObjectiveScore(
        station: GasStation,
        fuelType: String,
        distanceKm: Double?,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Double {
        return StationScoring.calculateScore(station, fuelType, distanceKm, currentTimeMs)
    }

    fun calculatePersonalScore(learning: PersonalStationLearning?): Double {
        if (learning == null) return 0.0
        return learning.personalPreferenceScore
    }

    fun calculateCombinedScore(
        objectiveScore: Double,
        personalScore: Double
    ): Double {
        // Higher personal preference score yields a lower (better) overall combined score
        return objectiveScore - (personalScore * PERSONAL_WEIGHT)
    }

    fun calculateTripCost(
        distanceKm: Double?,
        price: Double?,
        consumptionLPer100km: Double?
    ): Double? {
        if (distanceKm == null || distanceKm <= 0.0) return null
        if (price == null || price <= 0.0) return null
        if (consumptionLPer100km == null || consumptionLPer100km <= 0.0) return null

        val liters = (distanceKm * consumptionLPer100km) / 100.0
        return liters * price
    }

    fun calculateTotalEstimatedCost(
        station: GasStation,
        fuelType: String,
        distanceKm: Double?,
        price: Double?,
        refillLiters: Double = RecommendationPolicy.DEFAULT_REFILL_LITERS,
        consumptionLPer100km: Double = RecommendationPolicy.DEFAULT_CONSUMPTION_L_PER_100KM
    ): Double? {
        if (price == null || price <= 0.0) return null
        if (distanceKm == null || distanceKm == Double.MAX_VALUE || distanceKm < 0.0) return null

        val fuelCost = price * refillLiters
        val tripCost = (2.0 * distanceKm) * (consumptionLPer100km / 100.0) * price
        val queueCost = if (station.queueTime > 0) station.queueTime * RecommendationPolicy.QUEUE_TIME_COST_PER_MIN_RUB else 0.0
        val reliabilityPenalty = (100 - station.reliability.coerceIn(0, 100)) * RecommendationPolicy.RELIABILITY_WEIGHT

        return fuelCost + tripCost + queueCost + reliabilityPenalty
    }

    fun evaluateConfidence(
        snapshot: StationFuelSnapshot,
        consumptionPrediction: ConsumptionPrediction? = null
    ): RecommendationConfidence {
        if (snapshot.availability == FuelAvailabilityStatus.UNKNOWN && snapshot.price == null) {
            return RecommendationConfidence.UNKNOWN
        }

        if (snapshot.isConflict || snapshot.freshness == FuelFreshness.STALE) {
            return RecommendationConfidence.LOW
        }

        if (snapshot.confidence == RecommendationConfidence.HIGH &&
            snapshot.freshness == FuelFreshness.VERY_FRESH &&
            snapshot.price != null &&
            snapshot.price > 0.0
        ) {
            return RecommendationConfidence.HIGH
        }

        if (snapshot.confidence == RecommendationConfidence.HIGH || snapshot.confidence == RecommendationConfidence.MEDIUM) {
            return RecommendationConfidence.MEDIUM
        }

        return RecommendationConfidence.LOW
    }

    fun evaluateReasons(
        station: GasStation,
        fuelType: String,
        snapshot: StationFuelSnapshot,
        distanceKm: Double?,
        minPrice: Double?,
        avgPrice: Double?,
        learning: PersonalStationLearning?,
        estimatedTripCost: Double?,
        confidence: RecommendationConfidence
    ): List<SmartRecommendationReason> {
        val reasons = LinkedHashSet<SmartRecommendationReason>()

        if (snapshot.availability == FuelAvailabilityStatus.AVAILABLE) {
            reasons.add(SmartRecommendationReason.FUEL_AVAILABLE)
        } else if (snapshot.availability == FuelAvailabilityStatus.UNKNOWN) {
            reasons.add(SmartRecommendationReason.UNKNOWN_AVAILABILITY)
        }

        if (snapshot.isConflict) {
            reasons.add(SmartRecommendationReason.SOURCE_CONFLICT)
        } else if (snapshot.sourceCount >= 2) {
            reasons.add(SmartRecommendationReason.MULTI_SOURCE_CONFIRMATION)
        }

        if (snapshot.freshness == FuelFreshness.STALE) {
            reasons.add(SmartRecommendationReason.STALE_DATA)
        }

        val price = snapshot.price ?: station.fuelTypes.find { it.type == fuelType }?.price
        if (price != null && price > 0.0) {
            if ((minPrice != null && price <= minPrice * 1.005) || (avgPrice != null && price < avgPrice)) {
                reasons.add(SmartRecommendationReason.CHEAPER)
            }
        }

        if (distanceKm != null && distanceKm <= 3.0) {
            reasons.add(SmartRecommendationReason.CLOSER)
        }

        if (station.queueTime > 0 && station.queueTime <= 5) {
            reasons.add(SmartRecommendationReason.LOW_QUEUE)
        }

        if (snapshot.freshness == FuelFreshness.VERY_FRESH || snapshot.freshness == FuelFreshness.FRESH) {
            reasons.add(SmartRecommendationReason.FRESH_DATA)
        }

        if (learning != null && (learning.refuelsCount >= 2 || learning.personalPreferenceScore >= 2.0)) {
            reasons.add(SmartRecommendationReason.PERSONAL_PREFERENCE)
        }

        if (estimatedTripCost != null && estimatedTripCost > 0.0 && distanceKm != null && distanceKm <= 5.0) {
            reasons.add(SmartRecommendationReason.LOW_TRIP_COST)
        }

        if (confidence == RecommendationConfidence.HIGH) {
            reasons.add(SmartRecommendationReason.HIGH_CONFIDENCE)
        }

        return reasons.toList()
    }

    fun compareRecommendations(
        a: SmartStationRecommendation,
        b: SmartStationRecommendation
    ): Int {
        // 1. Confidence comparison (HIGH ordinal 0 < LOW ordinal 2, so HIGH ranks first)
        val confComp = a.confidence.ordinal.compareTo(b.confidence.ordinal)
        if (confComp != 0) return confComp

        // 2. Availability status comparison (AVAILABLE > UNKNOWN > UNAVAILABLE)
        val availOrderA = when (a.availability) {
            FuelAvailabilityStatus.AVAILABLE -> 2
            FuelAvailabilityStatus.UNKNOWN -> 1
            FuelAvailabilityStatus.UNAVAILABLE, FuelAvailabilityStatus.NO_FUEL -> 0
        }
        val availOrderB = when (b.availability) {
            FuelAvailabilityStatus.AVAILABLE -> 2
            FuelAvailabilityStatus.UNKNOWN -> 1
            FuelAvailabilityStatus.UNAVAILABLE, FuelAvailabilityStatus.NO_FUEL -> 0
        }
        val availComp = availOrderB.compareTo(availOrderA)
        if (availComp != 0) return availComp

        // 3. Combined score (lower combined score wins)
        val combinedScoreA = calculateCombinedScore(a.objectiveScore, a.personalScore)
        val combinedScoreB = calculateCombinedScore(b.objectiveScore, b.personalScore)
        val scoreComp = combinedScoreA.compareTo(combinedScoreB)
        if (scoreComp != 0) return scoreComp

        // 4. Data freshness comparison (fresher availability/price freshness wins)
        val freshnessComp = a.availabilityFreshness.ordinal.compareTo(b.availabilityFreshness.ordinal)
        if (freshnessComp != 0) return freshnessComp

        // 5. Total cost (lower cost wins, nulls last)
        if (a.estimatedTotalCost != null && b.estimatedTotalCost != null) {
            val costComp = a.estimatedTotalCost.compareTo(b.estimatedTotalCost)
            if (costComp != 0) return costComp
        } else if (a.estimatedTotalCost != null) {
            return -1
        } else if (b.estimatedTotalCost != null) {
            return 1
        }

        // 6. Distance (shorter distance wins, nulls last)
        if (a.distanceKm != null && b.distanceKm != null) {
            val distComp = a.distanceKm.compareTo(b.distanceKm)
            if (distComp != 0) return distComp
        } else if (a.distanceKm != null) {
            return -1
        } else if (b.distanceKm != null) {
            return 1
        }

        // 7. Stable stationId
        return a.stationId.compareTo(b.stationId)
    }
}
