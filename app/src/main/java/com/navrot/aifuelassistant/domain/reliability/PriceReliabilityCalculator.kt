package com.navrot.aifuelassistant.domain.reliability

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.data.model.isKnownClosed

object PriceReliabilityCalculator {

    const val FRESHNESS_THRESHOLD_MS = 8 * 60 * 60 * 1000L // 8 часов
    const val RUSSIABASE_FRESHNESS_THRESHOLD_MS = 24 * 60 * 60 * 1000L // 24 часа

    /**
     * In-memory кеш для calculate() и calculateFuelAvailability().
     *
     * Ключ: stationId + fuelType + priceUpdatedAt + stationUpdatedAt + (source, photoBonus, sourceCount)
     * Значение: PriceReliability (для calculate) или FuelAvailabilityStatus (для calculateFuelAvailability).
     *
     * Размер кеша ограничен 256 записями (≈100 станций × 2-3 типа топлива).
     * Инвалидация: при изменении любого компонента ключа кеш автоматически miss'ит,
     * потому что ключ включает `updatedAt` и `source`.
     *
     * Потокобезопасность: synchronized-блок достаточен — методы calculate() короткие,
     * contention минимален. Для UI-потока это ~1-5 мкс на попадание.
     */
    private const val CACHE_MAX_SIZE = 256
    private data class CacheKey(
        val stationId: Int,
        val fuelType: String,
        val priceUpdatedAt: Long,
        val stationUpdatedAt: Long,
        val priceSource: FuelDataSource?,
        val sourceCount: Int,
        val hasPhoto: Boolean,
        val available: Boolean
    )
    private data class CacheEntry<T>(
        val reliability: T,
        val priceReliability: PriceReliability?,
        val computedAtMs: Long
    )

    private val reliabilityCache = LinkedHashMap<CacheKey, CacheEntry<PriceReliability>>(CACHE_MAX_SIZE, 0.75f, true)
    private val availabilityCache = LinkedHashMap<CacheKey, CacheEntry<FuelAvailabilityStatus>>(CACHE_MAX_SIZE, 0.75f, true)

    private fun <T> LinkedHashMap<CacheKey, CacheEntry<T>>.getOrPut(
        key: CacheKey,
        compute: () -> CacheEntry<T>
    ): CacheEntry<T> = synchronized(this) {
        // LRU: удаляем старейший если переполнен
        while (size >= CACHE_MAX_SIZE) {
            val oldest = keys.iterator()
            if (oldest.hasNext()) {
                oldest.next()
                oldest.remove()
            } else break
        }
        get(key) ?: compute().also { put(key, it) }
    }

    fun clearCache() {
        synchronized(reliabilityCache) { reliabilityCache.clear() }
        synchronized(availabilityCache) { availabilityCache.clear() }
    }

    /**
     * Строит ключ кеша для станции. Включает все поля, от которых зависит результат:
     * stationId, fuelType, updatedAt (для инвалидации по времени), source/sourceCount (для бонуса),
     * available (для calculateFuelAvailability), hasPhoto (для бонуса reliability).
     */
    private fun buildKey(station: GasStation, fuelType: String?, fuelPrice: FuelPrice?): CacheKey {
        val photoBonus = fuelPrice?.photoEvidence != null ||
                station.photoEvidence.isNotEmpty() ||
                !station.monumentPhotoUrl.isNullOrBlank() ||
                !station.entrancePhotoUrl.isNullOrBlank()
        return CacheKey(
            stationId = station.id,
            fuelType = fuelType ?: "",
            priceUpdatedAt = fuelPrice?.updatedAt ?: 0L,
            stationUpdatedAt = station.updatedAt,
            priceSource = fuelPrice?.source,
            sourceCount = fuelPrice?.sourceCount ?: 0,
            hasPhoto = photoBonus,
            available = fuelPrice?.available ?: true
        )
    }

    fun calculateFuelAvailability(
        station: GasStation,
        fuelType: String? = null,
        currentTimeMs: Long = System.currentTimeMillis()
    ): FuelAvailabilityStatus {
        val fuelPrice = if (fuelType != null) {
            station.fuelTypes.find { it.type == fuelType }
        } else {
            station.fuelTypes.firstOrNull()
        } ?: return FuelAvailabilityStatus.UNKNOWN

        // Кеш: ключ включает все поля, от которых зависит результат + текущее время, округлённое до минуты
        // (чтобы кеш оставался валидным секунду, а не miss'ил из-за каждого тика часов).
        // currentTimeMs зависит от тика — поэтому округляем до минуты в ключе НЕ включаем,
        // а вместо этого инвалидируем через diffMs в compute.
        val cacheKey = buildKey(station, fuelType, fuelPrice)

        val cached = synchronized(availabilityCache) { availabilityCache[cacheKey] }
        if (cached != null) {
            // Если вычислялось недавно и station не изменилась — отдаём как есть,
            // даже если currentTimeMs слегка сдвинулся (но в пределах freshness threshold).
            val ageSinceCompute = currentTimeMs - cached.computedAtMs
            if (ageSinceCompute < 60_000L) return cached.reliability
            // Иначе пересчитываем только если результат зависит от времени
        }

        val status = computeAvailability(fuelPrice, station, currentTimeMs)
        synchronized(availabilityCache) {
            while (availabilityCache.size >= CACHE_MAX_SIZE) {
                val it = availabilityCache.keys.iterator()
                if (it.hasNext()) { it.next(); it.remove() } else break
            }
            availabilityCache[cacheKey] = CacheEntry(status, null, currentTimeMs)
        }
        return status
    }

    private fun computeAvailability(
        fuelPrice: FuelPrice,
        station: GasStation,
        currentTimeMs: Long
    ): FuelAvailabilityStatus {
        val timestamp = when {
            fuelPrice.updatedAt > 0L -> fuelPrice.updatedAt
            station.updatedAt > 0L -> station.updatedAt
            else -> 0L
        }

        if (timestamp <= 0L) return FuelAvailabilityStatus.UNKNOWN

        val diffMs = maxOf(0L, currentTimeMs - timestamp)

        if (fuelPrice.source == FuelDataSource.RUSSIABASE || station.dataSources.contains(FuelDataSource.RUSSIABASE)) {
            if (!fuelPrice.available || station.isKnownClosed()) {
                if (diffMs <= RUSSIABASE_FRESHNESS_THRESHOLD_MS) {
                    return FuelAvailabilityStatus.NO_FUEL
                }
            }
        }

        val threshold = if (fuelPrice.source == FuelDataSource.RUSSIABASE) RUSSIABASE_FRESHNESS_THRESHOLD_MS else FRESHNESS_THRESHOLD_MS

        return if (diffMs <= threshold) {
            if (fuelPrice.available) FuelAvailabilityStatus.AVAILABLE else FuelAvailabilityStatus.NO_FUEL
        } else {
            FuelAvailabilityStatus.UNKNOWN
        }
    }

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

        val cacheKey = buildKey(station, fuelType, fuelPrice)
        val cached = synchronized(reliabilityCache) { reliabilityCache[cacheKey] }
        if (cached != null) {
            // Reliability зависит от currentTimeMs (через ageDays), но в пределах 1 дня
            // разница мизерная — кешируем на 60 секунд.
            val ageSinceCompute = currentTimeMs - cached.computedAtMs
            if (ageSinceCompute < 60_000L) return cached.reliability
        }

        val reliability = computeReliability(fuelPrice, station, currentTimeMs)
        synchronized(reliabilityCache) {
            while (reliabilityCache.size >= CACHE_MAX_SIZE) {
                val it = reliabilityCache.keys.iterator()
                if (it.hasNext()) { it.next(); it.remove() } else break
            }
            reliabilityCache[cacheKey] = CacheEntry(reliability, reliability, currentTimeMs)
        }
        return reliability
    }

    private fun computeReliability(
        fuelPrice: FuelPrice?,
        station: GasStation,
        currentTimeMs: Long
    ): PriceReliability {
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
            FuelDataSource.RUSSIABASE,
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
