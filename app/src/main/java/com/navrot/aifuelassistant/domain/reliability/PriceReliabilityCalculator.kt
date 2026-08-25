package com.navrot.aifuelassistant.domain.reliability

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation

object PriceReliabilityCalculator {

    fun calculate(
        station: GasStation,
        fuelType: String? = null,
        currentTimeMs: Long = System.currentTimeMillis()
    ): PriceReliability {
        val fuelPrice = if (fuelType != null) {
            station.fuelTypes.find { it.type == fuelType }
        } else {
            station.fuelTypes.firstOrNull()
        }

        val timestamp = when {
            fuelPrice?.updatedAt != null && fuelPrice.updatedAt > 0L -> fuelPrice.updatedAt
            station.updatedAt > 0L -> station.updatedAt
            else -> 0L
        }

        val ageDays = if (timestamp > 0L) {
            val diffMs = maxOf(0L, currentTimeMs - timestamp)
            (diffMs / (1000L * 60 * 60 * 24)).toInt()
        } else {
            30 // Изначальные данные из assets без timestamp считаются давними (> 7 дней)
        }

        val source = determinePriceSource(fuelPrice, station)

        val basePercent = when (source) {
            PriceSource.USER_CONFIRMED -> 95
            PriceSource.NETWORK -> 85
            PriceSource.CACHE -> 70
            PriceSource.ASSETS -> 40
        }

        val agePenalty = when {
            ageDays <= 1 -> 0
            ageDays <= 3 -> 5
            ageDays <= 7 -> 15
            else -> 30
        }

        val sourceCountBonus = if ((fuelPrice?.sourceCount ?: 0) > 1) 10 else 0

        val hasPhoto = fuelPrice?.photoEvidence != null ||
                station.photoEvidence.isNotEmpty() ||
                !station.monumentPhotoUrl.isNullOrBlank() ||
                !station.entrancePhotoUrl.isNullOrBlank()
        val photoBonus = if (hasPhoto) 5 else 0

        val totalPercent = (basePercent - agePenalty + sourceCountBonus + photoBonus).coerceIn(0, 100)

        return PriceReliability(
            percent = totalPercent,
            source = source,
            ageDays = ageDays
        )
    }

    private fun determinePriceSource(fuelPrice: FuelPrice?, station: GasStation): PriceSource {
        val src = fuelPrice?.source
        return when {
            src == FuelDataSource.USER_REPORT -> PriceSource.USER_CONFIRMED
            src == FuelDataSource.BENZONAVT || (src != null && isNetworkSource(src)) -> PriceSource.NETWORK
            station.dataSources.contains(FuelDataSource.USER_REPORT) -> PriceSource.USER_CONFIRMED
            station.dataSources.any { isNetworkSource(it) } -> PriceSource.NETWORK
            (fuelPrice?.updatedAt != null && fuelPrice.updatedAt > 0L) || station.updatedAt > 0L -> PriceSource.CACHE
            else -> PriceSource.ASSETS
        }
    }

    private fun isNetworkSource(source: FuelDataSource): Boolean {
        return source in setOf(
            FuelDataSource.BENZONAVT,
            FuelDataSource.GDEBENZ,
            FuelDataSource.TWO_GIS,
            FuelDataSource.YANDEX,
            FuelDataSource.T_BANK,
            FuelDataSource.OFFICIAL_STATION,
            FuelDataSource.TELEGRAM,
            FuelDataSource.VK
        )
    }
}
