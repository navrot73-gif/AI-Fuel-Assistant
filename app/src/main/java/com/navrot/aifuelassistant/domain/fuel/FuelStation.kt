package com.navrot.aifuelassistant.domain.fuel

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * АЗС, отображаемая в диспетчере топлива.
 *
 * Это доменная модель MVP. Источник данных может быть заменён без изменения
 * главного экрана: API, парсер, пользовательский сигнал или агрегатор данных.
 */
data class FuelStation(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val distanceKm: Double? = null,
    val driveTimeMinutes: Int? = null,
    val fuel: Map<String, FuelAvailability> = emptyMap(),
    val dataUpdatedMinutesAgo: Int? = null,

    // Дополнительные поля
    val brand: String? = null, // Бренд/сеть АЗС
    val phone: String? = null,
    val website: String? = null,
    val is24Hours: Boolean? = null,
    val openingHours: OpeningHours? = null,
    val amenities: List<Amenity> = emptyList(),
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val averageRating: Double? = null,
    val reviewCount: Int? = null,
    val lastVisited: Instant? = null,
    val visitCount: Int? = null,
    val isFavorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val images: List<String> = emptyList(), // URL-ы изображений
    val description: String? = null
) {
    /**
     * Проверяет, доступно ли топливо указанного типа
     */
    fun isFuelAvailable(fuelType: String): Boolean {
        return fuel[fuelType]?.status in setOf(
            FuelAvailabilityStatus.AVAILABLE,
            FuelAvailabilityStatus.LIMITED
        )
    }

    /**
     * Получает цену топлива указанного типа
     */
    fun getFuelPrice(fuelType: String): Double? {
        return fuel[fuelType]?.pricePerLiter
    }

    /**
     * Получает статус топлива указанного типа
     */
    fun getFuelStatus(fuelType: String): FuelAvailabilityStatus? {
        return fuel[fuelType]?.status
    }

    /**
     * Проверяет, работает ли АЗС круглосуточно
     */
    fun isOpenNow(): Boolean {
        return is24Hours == true || openingHours?.isOpenNow() == true
    }

    /**
     * Проверяет, является ли АЗС частью сети
     */
    fun isPartOfNetwork(network: String): Boolean {
        return brand?.contains(network, ignoreCase = true) == true ||
                name.contains(network, ignoreCase = true)
    }

    /**
     * Получает рейтинг в виде звезд
     */
    fun getRatingStars(): String {
        return averageRating?.let { rating ->
            "★".repeat(rating.toInt()) + "☆".repeat(5 - rating.toInt())
        } ?: "Нет оценок"
    }

    /**
     * Проверяет наличие удобства
     */
    fun hasAmenity(amenity: Amenity): Boolean {
        return amenities.contains(amenity)
    }

    /**
     * Проверяет способ оплаты
     */
    fun acceptsPaymentMethod(method: PaymentMethod): Boolean {
        return paymentMethods.contains(method)
    }

    companion object {
        /**
         * Создает минимальную копию АЗС для быстрого отображения
         */
        fun createMinimal(
            id: String,
            name: String,
            address: String,
            latitude: Double,
            longitude: Double
        ): FuelStation {
            return FuelStation(
                id = id,
                name = name,
                address = address,
                latitude = latitude,
                longitude = longitude
            )
        }
    }
}

/**
 * Часы работы АЗС
 */
data class OpeningHours(
    val monday: String? = null,
    val tuesday: String? = null,
    val wednesday: String? = null,
    val thursday: String? = null,
    val friday: String? = null,
    val saturday: String? = null,
    val sunday: String? = null,
    val holidays: String? = null
) {
    /**
     * Проверяет, открыта ли АЗС сейчас
     */
    fun isOpenNow(): Boolean {
        val now = LocalDateTime.now()
        val dayOfWeek = now.dayOfWeek.value // 1-7 (понедельник-воскресенье)
        val time = now.toLocalTime()

        val hours = when (dayOfWeek) {
            1 -> monday
            2 -> tuesday
            3 -> wednesday
            4 -> thursday
            5 -> friday
            6 -> saturday
            7 -> sunday
            else -> return false
        }

        return hours?.let { isTimeInRange(time, it) } ?: false
    }

    private fun isTimeInRange(time: java.time.LocalTime, hours: String): Boolean {
        // Простой парсер для формата "HH:MM-HH:MM"
        try {
            val parts = hours.split("-")
            if (parts.size != 2) return false

            val start = java.time.LocalTime.parse(parts[0])
            val end = java.time.LocalTime.parse(parts[1])

            return time in start..end
        } catch (_: Exception) {
            return false
        }
    }
}

/**
 * Удобства на АЗС
 */
enum class Amenity {
    CAFE,
    RESTAURANT,
    SHOP,
    CAR_WASH,
    TIRE_SERVICE,
    ATM,
    TOILET,
    SHOWER,
    PARKING,
    ELECTRIC_CHARGING,
    CAR_RENTAL,
    HOTEL,
    PLAYGROUND,
    PET_FRIENDLY,
    WIFI
}

/**
 * Способы оплаты
 */
enum class PaymentMethod {
    CASH,
    CARD,
    FUEL_CARD,
    APPLE_PAY,
    GOOGLE_PAY,
    SBP, // Система быстрых платежей
    MIR_PAY
}

/**
 * Доступность топлива
 */
data class FuelAvailability(
    val status: FuelAvailabilityStatus,
    val pricePerLiter: Double? = null,
    val queueCars: Int? = null,
    val estimatedWaitMinutes: Int? = null,
    val lastUpdated: Instant? = null,
    val volumeInTank: Double? = null, // Объем в резервуаре (литры)
    val isPremium: Boolean = false,
    val octaneRating: Int? = null // Октановое число
) {
    /**
     * Проверяет, является ли цена конкурентной
     */
    fun isPriceCompetitive(averagePrice: Double, threshold: Double = 1.0): Boolean {
        return pricePerLiter?.let { price ->
            price <= averagePrice + threshold
        } ?: false
    }

    /**
     * Получает время обновления в читаемом формате
     */
    fun getLastUpdatedFormatted(): String? {
        return lastUpdated?.let { instant ->
            val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
            val formatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            localDateTime.format(formatter)
        }
    }
}

/**
 * Статус доступности топлива
 */
enum class FuelAvailabilityStatus {
    AVAILABLE,    // Доступно
    LIMITED,      // Ограниченное количество
    UNAVAILABLE,  // Недоступно
    UNKNOWN,      // Неизвестно
    OUT_OF_STOCK, // Закончилось
    EXPECTED      // Ожидается поставка
}

/**
 * Расширение для карты топлива
 */
fun Map<String, FuelAvailability>.getAvailableFuelTypes(): List<String> {
    return this.filterValues {
        it.status == FuelAvailabilityStatus.AVAILABLE ||
                it.status == FuelAvailabilityStatus.LIMITED
    }.keys.toList()
}

fun Map<String, FuelAvailability>.getCheapestFuelType(): String? {
    return this.filterValues {
        it.status == FuelAvailabilityStatus.AVAILABLE
    }.minByOrNull {
        it.value.pricePerLiter ?: Double.MAX_VALUE
    }?.key
}