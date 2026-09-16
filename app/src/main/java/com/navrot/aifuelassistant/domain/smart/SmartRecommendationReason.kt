package com.navrot.aifuelassistant.domain.smart

enum class SmartRecommendationReason(val description: String) {
    CHEAPER("Выгодная цена"),
    CLOSER("Короткий маршрут"),
    LOW_QUEUE("Небольшая очередь"),
    FUEL_AVAILABLE("Топливо есть"),
    FRESH_DATA("Данные обновлены"),
    PERSONAL_PREFERENCE("Ваша привычная АЗС"),
    LOW_TRIP_COST("Выгодная поездка"),
    HIGH_CONFIDENCE("Высокая надёжность"),
    MULTI_SOURCE_CONFIRMATION("Подтверждено источниками"),
    SOURCE_CONFLICT("Источники расходятся"),
    STALE_DATA("Данные устарели"),
    UNKNOWN_AVAILABILITY("Наличие не подтверждено"),
    UNKNOWN_PRICE("Цена не указана"),
    PRICE_DATA_STALE("Цена устарела")
}
