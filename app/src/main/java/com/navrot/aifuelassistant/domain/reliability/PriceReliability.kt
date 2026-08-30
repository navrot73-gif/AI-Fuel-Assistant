package com.navrot.aifuelassistant.domain.reliability

enum class PriceSource {
    USER_CONFIRMED,   // пользовательская подтверждённая цена
    NETWORK,          // данные из сети (Benzonavt и т.д.)
    CACHE,            // локальный кэш
    ASSETS            // офлайн-фолбэк из assets
}

enum class FuelAvailabilityStatus {
    AVAILABLE, // 🟢 есть свежие метки "есть топливо"
    NO_FUEL,   // 🔴 есть свежие метки "нет топлива"
    UNKNOWN    // ⚪ меток нет или они старше 8 часов
}

data class PriceReliability(
    val percent: Int,           // 0..100
    val source: PriceSource,
    val ageDays: Int            // давность данных в днях
)
