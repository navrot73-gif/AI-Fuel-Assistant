package com.navrot.aifuelassistant.domain.smart

import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.domain.intelligence.FuelIntelligenceResolver
import com.navrot.aifuelassistant.domain.personal.PersonalFuelEvent
import com.navrot.aifuelassistant.domain.predictive.ConsumptionPrediction
import com.navrot.aifuelassistant.domain.predictive.RecommendationFeedback
import com.navrot.aifuelassistant.domain.predictive.usecase.PersonalStationPreferenceUseCase
import com.navrot.aifuelassistant.domain.predictive.usecase.PredictConsumptionUseCase
import com.navrot.aifuelassistant.domain.recommendation.RecommendationPolicy
import com.navrot.aifuelassistant.geo.GeoUtils
import javax.inject.Inject

class GetSmartFuelRecommendationUseCase @Inject constructor(
    private val personalStationPreferenceUseCase: PersonalStationPreferenceUseCase,
    private val predictConsumptionUseCase: PredictConsumptionUseCase
) {

    // Secondary constructor for convenient default instantiation in tests/non-DI usages
    constructor() : this(
        personalStationPreferenceUseCase = PersonalStationPreferenceUseCase(),
        predictConsumptionUseCase = PredictConsumptionUseCase()
    )

    fun execute(
        stations: List<GasStation>,
        fuelType: String,
        userLat: Double? = null,
        userLon: Double? = null,
        vehicleId: Long? = null,
        personalEvents: List<PersonalFuelEvent> = emptyList(),
        feedbacks: List<RecommendationFeedback> = emptyList(),
        refillLiters: Double = RecommendationPolicy.DEFAULT_REFILL_LITERS,
        currentTimeMs: Long = System.currentTimeMillis()
    ): SmartRecommendationResult {
        val evaluatedCount = stations.size
        if (stations.isEmpty()) {
            return SmartRecommendationResult(
                topRecommendation = null,
                alternatives = emptyList(),
                evaluatedCount = 0
            )
        }

        val matchingFuelStations = stations.filter { s ->
            s.fuelTypes.any { it.type == fuelType }
        }

        if (matchingFuelStations.isEmpty()) {
            return SmartRecommendationResult(
                topRecommendation = null,
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
        val avgPrice = if (prices.isNotEmpty()) prices.average() else null

        val stationLearnings = personalStationPreferenceUseCase.getAllStationLearnings(personalEvents, feedbacks)

        val consumptionPrediction: ConsumptionPrediction? = if (vehicleId != null) {
            predictConsumptionUseCase(
                vehicleId = vehicleId,
                fuelType = fuelType,
                events = personalEvents
            )
        } else null

        val candidates = matchingFuelStations.mapNotNull { station ->
            val snapshot = FuelIntelligenceResolver.resolveSnapshot(station, fuelType, currentTimeMs)

            val dataQuality = com.navrot.aifuelassistant.domain.realtime.FuelDataQualityAnalyzer.analyze(snapshot, now = currentTimeMs)

            // Apply safety filter: exclude explicitly UNAVAILABLE / NO_FUEL stations
            if (com.navrot.aifuelassistant.domain.realtime.SmartRecommendationSafetyPolicy.isExcludedFromRecommendation(dataQuality)) {
                return@mapNotNull null
            }

            val safetyWarnings = com.navrot.aifuelassistant.domain.realtime.SmartRecommendationSafetyPolicy.generateSafetyWarnings(dataQuality)

            val distanceKm = if (hasUserLocation) {
                GeoUtils.calculateDistance(userLat!!, userLon!!, station.latitude, station.longitude)
            } else null

            val estimatedTravelMinutes = distanceKm?.let {
                (it / 30.0 * 60.0).toInt().coerceAtLeast(1)
            }

            val queueTimeMinutes = if (station.queueTime > 0) station.queueTime else null

            val objectiveScore = SmartRecommendationPolicy.calculateObjectiveScore(
                station = station,
                fuelType = fuelType,
                distanceKm = distanceKm,
                currentTimeMs = currentTimeMs
            )

            val learning = stationLearnings[station.id]
            val personalScore = SmartRecommendationPolicy.calculatePersonalScore(learning)

            val price = snapshot.price ?: station.fuelTypes.find { it.type == fuelType }?.price

            val estimatedTripCost = SmartRecommendationPolicy.calculateTripCost(
                distanceKm = distanceKm,
                price = price,
                consumptionLPer100km = consumptionPrediction?.predictedConsumption ?: RecommendationPolicy.DEFAULT_CONSUMPTION_L_PER_100KM
            )

            val estimatedFuelCost = price?.let { it * refillLiters }

            val estimatedTotalCost = SmartRecommendationPolicy.calculateTotalEstimatedCost(
                station = station,
                fuelType = fuelType,
                distanceKm = distanceKm,
                price = price,
                refillLiters = refillLiters,
                consumptionLPer100km = consumptionPrediction?.predictedConsumption ?: RecommendationPolicy.DEFAULT_CONSUMPTION_L_PER_100KM
            )

            val baseConfidence = SmartRecommendationPolicy.evaluateConfidence(
                snapshot = snapshot,
                consumptionPrediction = consumptionPrediction
            )
            val confidence = com.navrot.aifuelassistant.domain.realtime.SmartRecommendationSafetyPolicy.adjustConfidenceForSafety(
                baseConfidence = baseConfidence,
                dataQuality = dataQuality
            )

            val reasons = SmartRecommendationPolicy.evaluateReasons(
                station = station,
                fuelType = fuelType,
                snapshot = snapshot,
                distanceKm = distanceKm,
                minPrice = minPrice,
                avgPrice = avgPrice,
                learning = learning,
                estimatedTripCost = estimatedTripCost,
                confidence = confidence
            )

            val personalVisitCount = learning?.refuelsCount?.takeIf { it > 0 }

            SmartStationRecommendation(
                station = station,
                stationId = station.id,
                stationName = station.name,
                address = station.address,
                fuelType = fuelType,
                availability = snapshot.availability,
                availabilityFreshness = snapshot.freshness,
                price = price,
                priceFreshness = snapshot.freshness,
                distanceKm = distanceKm,
                estimatedTravelMinutes = estimatedTravelMinutes,
                queueTimeMinutes = queueTimeMinutes,
                objectiveScore = objectiveScore,
                personalScore = personalScore,
                estimatedFuelCost = estimatedFuelCost,
                estimatedTripCost = estimatedTripCost,
                estimatedTotalCost = estimatedTotalCost,
                confidence = confidence,
                reasons = reasons,
                personalVisitCount = personalVisitCount,
                recommended = false,
                dataQuality = dataQuality,
                safetyWarnings = safetyWarnings
            )
        }

        if (candidates.isEmpty()) {
            return SmartRecommendationResult(
                topRecommendation = null,
                alternatives = emptyList(),
                evaluatedCount = evaluatedCount
            )
        }

        val sorted = candidates.sortedWith { a, b ->
            SmartRecommendationPolicy.compareRecommendations(a, b)
        }

        val topCandidate = sorted.first().copy(recommended = true)
        val alternatives = sorted.drop(1).take(2)

        return SmartRecommendationResult(
            topRecommendation = topCandidate,
            alternatives = alternatives,
            evaluatedCount = evaluatedCount
        )
    }
}
