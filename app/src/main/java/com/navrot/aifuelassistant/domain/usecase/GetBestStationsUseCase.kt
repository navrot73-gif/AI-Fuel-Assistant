package com.navrot.aifuelassistant.domain.usecase

import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.geo.GeoUtils
import javax.inject.Inject

/**
 * Скоринг и ранжирование АЗС.
 *
 * Вынесен из GasStationRepository для тестируемости и соответствия
 * принципу единой ответственности (Single Responsibility Principle).
 *
 * Формула скоринга:
 *   score = fuelPrice + queueTime * 0.5 - (100 - reliability) * 0.2
 *
 * Чем меньше score — тем лучше АЗС для пользователя.
 */
class GetBestStationsUseCase @Inject constructor() {

    /**
 * Фильтрует станции с нужным типом топлива и сортирует по composite score.
 *
 * @param stations полный список АЗС
 * @param fuelType тип топлива для фильтрации (например "АИ-95")
 * @return список станций, отсортированных от лучшей к худшей
 */
    fun execute(stations: List<GasStation>, fuelType: String): List<GasStation> {
        return stations
            .filter { s -> s.fuelTypes.any { it.type == fuelType && it.available } }
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
                dist <= radiusKm && s.fuelTypes.any { it.type == fuelType && it.available }
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
 * - (100 - reliability) * RELIABILITY_WEIGHT: бонус за надёжность
 */
    fun calculateScore(station: GasStation, fuelType: String): Double {
        val fuel = station.fuelTypes.find { it.type == fuelType }
        if (fuel == null || !fuel.available) return Double.MAX_VALUE
        val queuePenalty = station.queueTime * QUEUE_WEIGHT
        val reliabilityBonus = (100 - station.reliability) * RELIABILITY_WEIGHT
        return fuel.price + queuePenalty - reliabilityBonus
    }

    companion object {
        /** Вес очереди в скоринге. 1 минута очереди = 0.5 руб. штрафа. */
        const val QUEUE_WEIGHT = 0.5

        /** Вес надёжности. 1 пункт надёжности = 0.2 руб. бонуса. */
        const val RELIABILITY_WEIGHT = 0.2
    }
}
