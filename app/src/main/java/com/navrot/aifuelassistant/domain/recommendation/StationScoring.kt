package com.navrot.aifuelassistant.domain.recommendation

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import com.navrot.aifuelassistant.domain.reliability.PriceReliabilityCalculator

object StationScoring {

    fun calculateScore(
        station: GasStation,
        fuelType: String,
        distanceKm: Double? = null,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Double {
        val fuel = station.fuelTypes.find { it.type == fuelType }
        val price = fuel?.price ?: 0.0

        // Missing price: penalty equivalent to missing data
        val priceComponent = if (price > 0.0) {
            price * RecommendationPolicy.PRICE_WEIGHT
        } else {
            100.0 * RecommendationPolicy.PRICE_WEIGHT
        }

        // Missing distance: neutral baseline (0.0 penalty), no false distance advantage
        val distancePenalty = if (distanceKm != null && distanceKm != Double.MAX_VALUE && distanceKm >= 0.0) {
            distanceKm * RecommendationPolicy.DISTANCE_WEIGHT
        } else {
            0.0
        }

        // Queue penalty
        val queuePenalty = if (station.queueTime > 0) {
            station.queueTime * RecommendationPolicy.QUEUE_WEIGHT
        } else {
            0.0
        }

        // Reliability penalty: (100 - reliability) * 0.2
        val reliabilityPenalty = (100 - station.reliability.coerceIn(0, 100)) * RecommendationPolicy.RELIABILITY_WEIGHT

        // Availability penalty
        val availability = PriceReliabilityCalculator.calculateFuelAvailability(station, fuelType, currentTimeMs)
        val noFuelPenalty = if (availability == FuelAvailabilityStatus.NO_FUEL) {
            RecommendationPolicy.NO_FUEL_PENALTY
        } else 0.0

        return priceComponent + queuePenalty + reliabilityPenalty + distancePenalty + noFuelPenalty
    }

    fun calculateEstimatedTotalCost(
        station: GasStation,
        fuelType: String,
        distanceKm: Double?,
        refillLiters: Double = RecommendationPolicy.DEFAULT_REFILL_LITERS,
        consumptionLPer100km: Double = RecommendationPolicy.DEFAULT_CONSUMPTION_L_PER_100KM
    ): Double? {
        val fuel = station.fuelTypes.find { it.type == fuelType } ?: return null
        val price = fuel.price
        if (price <= 0.0) return null
        if (distanceKm == null || distanceKm == Double.MAX_VALUE || distanceKm < 0.0) return null

        val fuelCost = price * refillLiters
        val tripCost = (2.0 * distanceKm) * (consumptionLPer100km / 100.0) * price
        val queueCost = if (station.queueTime > 0) station.queueTime * RecommendationPolicy.QUEUE_TIME_COST_PER_MIN_RUB else 0.0
        val reliabilityPenalty = (100 - station.reliability.coerceIn(0, 100)) * RecommendationPolicy.RELIABILITY_WEIGHT

        return fuelCost + tripCost + queueCost + reliabilityPenalty
    }

    fun evaluateConfidence(
        station: GasStation,
        fuelType: String,
        currentTimeMs: Long = System.currentTimeMillis()
    ): RecommendationConfidence {
        val fuel = station.fuelTypes.find { it.type == fuelType }
        val price = fuel?.price ?: 0.0
        val availability = PriceReliabilityCalculator.calculateFuelAvailability(station, fuelType, currentTimeMs)
        val priceReliability = PriceReliabilityCalculator.calculate(station, fuelType, currentTimeMs)

        val timestamp = when {
            fuel?.updatedAt != null && fuel.updatedAt > 0L -> fuel.updatedAt
            station.updatedAt > 0L -> station.updatedAt
            else -> 0L
        }

        val ageMs = if (timestamp > 0L) maxOf(0L, currentTimeMs - timestamp) else Long.MAX_VALUE

        if (price <= 0.0 && timestamp == 0L) {
            return RecommendationConfidence.UNKNOWN
        }

        return when {
            availability == FuelAvailabilityStatus.AVAILABLE &&
                    price > 0.0 &&
                    ageMs <= RecommendationPolicy.NORMAL_THRESHOLD_MS &&
                    priceReliability.percent >= 70 -> RecommendationConfidence.HIGH

            price > 0.0 &&
                    availability != FuelAvailabilityStatus.NO_FUEL &&
                    ageMs <= RecommendationPolicy.STALE_THRESHOLD_MS &&
                    priceReliability.percent >= 40 -> RecommendationConfidence.MEDIUM

            timestamp > 0L || price > 0.0 -> RecommendationConfidence.LOW

            else -> RecommendationConfidence.UNKNOWN
        }
    }

    fun evaluateReasons(
        station: GasStation,
        fuelType: String,
        distanceKm: Double?,
        minPrice: Double? = null,
        averagePrice: Double? = null,
        currentTimeMs: Long = System.currentTimeMillis()
    ): List<StationRecommendationReason> {
        val reasons = mutableListOf<StationRecommendationReason>()
        val fuel = station.fuelTypes.find { it.type == fuelType }
        val price = fuel?.price ?: 0.0
        val availability = PriceReliabilityCalculator.calculateFuelAvailability(station, fuelType, currentTimeMs)
        val priceReliability = PriceReliabilityCalculator.calculate(station, fuelType, currentTimeMs)

        if (availability == FuelAvailabilityStatus.AVAILABLE) {
            reasons.add(StationRecommendationReason.FUEL_AVAILABLE)
        }

        if (price > 0.0) {
            if ((minPrice != null && price <= minPrice * 1.005) || (averagePrice != null && price < averagePrice)) {
                reasons.add(StationRecommendationReason.LOW_PRICE)
            }
        }

        if (distanceKm != null && distanceKm != Double.MAX_VALUE && distanceKm <= 3.0) {
            reasons.add(StationRecommendationReason.SHORT_DISTANCE)
        }

        if (station.queueTime > 0 && station.queueTime <= 5) {
            reasons.add(StationRecommendationReason.SHORT_QUEUE)
        }

        if (station.reliability >= 80 || priceReliability.percent >= 80) {
            reasons.add(StationRecommendationReason.HIGH_RELIABILITY)
        }

        val timestamp = when {
            fuel?.updatedAt != null && fuel.updatedAt > 0L -> fuel.updatedAt
            station.updatedAt > 0L -> station.updatedAt
            else -> 0L
        }
        val ageMs = if (timestamp > 0L) maxOf(0L, currentTimeMs - timestamp) else Long.MAX_VALUE

        if (ageMs <= RecommendationPolicy.NORMAL_THRESHOLD_MS) {
            reasons.add(StationRecommendationReason.FRESH_DATA)
        }

        if (reasons.contains(StationRecommendationReason.LOW_PRICE) && reasons.contains(StationRecommendationReason.SHORT_DISTANCE)) {
            reasons.add(StationRecommendationReason.LOW_TOTAL_COST)
        }

        return reasons
    }

    fun compareRecommendations(
        a: StationRecommendation,
        b: StationRecommendation,
        fuelType: String,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Int {
        // 1. Confidence comparison
        val confComp = b.confidence.ordinal.compareTo(a.confidence.ordinal)
        if (confComp != 0) return confComp

        // 2. Fuel availability comparison (AVAILABLE > UNKNOWN > NO_FUEL)
        val availA = PriceReliabilityCalculator.calculateFuelAvailability(a.station, fuelType, currentTimeMs)
        val availB = PriceReliabilityCalculator.calculateFuelAvailability(b.station, fuelType, currentTimeMs)
        val availOrderA = when (availA) {
            FuelAvailabilityStatus.AVAILABLE -> 2
            FuelAvailabilityStatus.UNKNOWN -> 1
            FuelAvailabilityStatus.NO_FUEL -> 0
        }
        val availOrderB = when (availB) {
            FuelAvailabilityStatus.AVAILABLE -> 2
            FuelAvailabilityStatus.UNKNOWN -> 1
            FuelAvailabilityStatus.NO_FUEL -> 0
        }
        val availComp = availOrderB.compareTo(availOrderA)
        if (availComp != 0) return availComp

        // 3. Score (lower score wins)
        val scoreComp = a.score.compareTo(b.score)
        if (scoreComp != 0) return scoreComp

        // 4. Total Cost (lower cost wins, nulls last)
        if (a.estimatedTotalCost != null && b.estimatedTotalCost != null) {
            val costComp = a.estimatedTotalCost.compareTo(b.estimatedTotalCost)
            if (costComp != 0) return costComp
        } else if (a.estimatedTotalCost != null && b.estimatedTotalCost == null) {
            return -1
        } else if (a.estimatedTotalCost == null && b.estimatedTotalCost != null) {
            return 1
        }

        // 5. Stable stationId tie-break
        return a.station.id.compareTo(b.station.id)
    }
}
