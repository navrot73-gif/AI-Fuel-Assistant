package com.navrot.aifuelassistant.domain.usecase

import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.geo.GeoUtils
import javax.inject.Inject

/**
 * Скоринг и ранжирование АЗС.
 *
 * Формула:
 *   score = fuelPrice + queueTime * 0.5 + (100 - reliability) * 0.2
 *
 * Чем меньше score — тем лучше АЗС.
 * Низкая цена и короткая очередь улучшают результат;
 * низкая надёжность добавляет штраф.
 */
class GetBestStationsUseCase @Inject constructor() {

    fun execute(stations: List<GasStation>, fuelType: String): List<GasStation> {
        return stations
            .filter { s -> s.fuelTypes.any { it.type == fuelType && it.available } }
            .sortedBy { s -> calculateScore(s, fuelType) }
    }

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

    /** Чем меньше score — тем лучше. Недоступное топливо никогда не выигрывает. */
    fun calculateScore(station: GasStation, fuelType: String): Double {
        val fuel = station.fuelTypes.find { it.type == fuelType }
        if (fuel == null || !fuel.available) return Double.MAX_VALUE

        val queuePenalty = station.queueTime.coerceAtLeast(0) * QUEUE_WEIGHT
        val reliabilityPenalty = (100 - station.reliability.coerceIn(0, 100)) * RELIABILITY_WEIGHT
        return fuel.price + queuePenalty + reliabilityPenalty
    }

    companion object {
        const val QUEUE_WEIGHT = 0.5
        const val RELIABILITY_WEIGHT = 0.2
    }
}
