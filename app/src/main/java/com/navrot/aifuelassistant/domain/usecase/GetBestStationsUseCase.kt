package com.navrot.aifuelassistant.domain.usecase

import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import com.navrot.aifuelassistant.domain.reliability.PriceReliabilityCalculator
import com.navrot.aifuelassistant.geo.GeoUtils
import javax.inject.Inject

/**
 * Скоринг и ранжирование АЗС.
 *
 * Вынесен из GasStationRepository для тестируемости и соответствия
 * принципу единой ответственности (Single Responsibility Principle).
 *
 * Формула скоринга:
 *   score = fuelPrice + queueTime * 0.5 + (100 - reliability) * 0.2 + noFuelPenalty
 *
 * Чем меньше score — тем лучше АЗС для пользователя.
 * Штраф за ненадёжность: чем ниже reliability, тем больше score.
 * Штраф за NO_FUEL: станции без топлива не скрываются, но штрафуются в скоринге (+1000).
 */
data class ScoringWeights(
    val price: Double = 1.0,
    val queueTime: Double = 0.5,
    val reliability: Double = 0.2,
    val distance: Double = 1.2,
    val noFuelPenalty: Double = 1000.0
)

class GetBestStationsUseCase(
    private val weights: ScoringWeights = ScoringWeights()
) {
    @Inject
    constructor() : this(ScoringWeights())

    /**
     * Фильтрует станции с нужным типом топлива и сортирует по composite score.
     *
     * @param stations полный список АЗС
     * @param fuelType тип топлива для фильтрации (например "АИ-95")
     * @return список станций, отсортированных от лучшей к худшей
     */
    fun execute(stations: List<GasStation>, fuelType: String): List<GasStation> {
        return stations
            .filter { s -> s.fuelTypes.any { it.type == fuelType } }
            .sortedBy { s -> calculateScore(s, fuelType) }
    }

    /**
     * Фильтрует станции в радиусе от пользователя и сортирует по composite score.
     *
     * @param stations полный список АЗС
     * @param fuelType тип топлива
     * @param userLat широта пользователя
     * @param userLon долгота пользователя
     * @param radiusKm радиус поиска в километрах
     * @return список ближайших станций, отсортированных от лучшей к худшей
     */
    fun execute(
        stations: List<GasStation>,
        fuelType: String,
        userLat: Double,
        userLon: Double,
        radiusKm: Double
    ): List<GasStation> {
        return stations
            .filter { s ->
                val dist = GeoUtils.calculateDistance(userLat, userLon, s.latitude, s.longitude)
                dist <= radiusKm && s.fuelTypes.any { it.type == fuelType }
            }
            .sortedBy { s -> calculateScore(s, fuelType) }
    }

    /**
     * Composite score для одной АЗС.
     * Чем меньше — тем лучше.
     *
     * Компоненты:
     * - fuelPrice: цена за литр (основной фактор, руб.)
     * - queueTime * QUEUE_WEIGHT: штраф за очередь
     * - (100 - reliability) * RELIABILITY_WEIGHT: штраф за ненадёжность
     * - noFuelPenalty: штраф если статус NO_FUEL
     */
    fun calculateScore(
        station: GasStation,
        fuelType: String,
        distanceKm: Double? = null,
        currentTimeMs: Long = System.currentTimeMillis()
    ): Double {
        val fuel = station.fuelTypes.find { it.type == fuelType } ?: return Double.MAX_VALUE
        val queuePenalty = station.queueTime * weights.queueTime
        val reliabilityPenalty = (100 - station.reliability) * weights.reliability
        val distancePenalty = if (distanceKm != null && distanceKm != Double.MAX_VALUE) {
            distanceKm * weights.distance
        } else 0.0

        val availability = PriceReliabilityCalculator.calculateFuelAvailability(station, fuelType, currentTimeMs)
        val noFuelPenalty = if (availability == FuelAvailabilityStatus.NO_FUEL) weights.noFuelPenalty else 0.0

        return fuel.price * weights.price + queuePenalty + reliabilityPenalty + distancePenalty + noFuelPenalty
    }

    companion object {
        /** Вес очереди в скоринге. 1 минута очереди = 0.5 руб. штрафа. */
        const val QUEUE_WEIGHT = 0.5

        /** Вес надёжности. 1 пункт ненадёжности = 0.2 руб. штрафа. */
        const val RELIABILITY_WEIGHT = 0.2

        /** Вес расстояния в скоринге. 1 км = 1.2 руб. штрафа. */
        const val DISTANCE_WEIGHT = 1.2

        /** Штраф за отсутствие топлива. */
        const val NO_FUEL_PENALTY = 1000.0
    }
}
