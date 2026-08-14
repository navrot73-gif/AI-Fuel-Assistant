package com.navrot.aifuelassistant.geo

import kotlin.math.*

/**
 * Единый утилитарный объект для географических расчётов.
 * Формула Гаверсинуса вынесена сюда из 4 дублированных мест.
 */
object GeoUtils {

    /**
     * Расстояние между двумя точками (широта/долгота) по формуле Гаверсинуса.
     *
     * @return расстояние в километрах
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    /**
     * Обратное геокодирование — получение названия города по координатам.
     * Сначала пытается использовать [NominatimGeocodingProvider],
     * при ошибке или отсутствии сети — fallback на хардкод.
     *
     * @param geocodingProvider провайдер обратного геокодинга, может быть null
     */
    suspend fun detectCity(
        lat: Double,
        lon: Double,
        geocodingProvider: GeocodingProvider? = null
    ): String {
        // Сначала пробуем Nominatim
        geocodingProvider?.let {
            try {
                val result = it.reverseGeocode(lat, lon)
                // Извлекаем город из display_name (формат: "город, район, область, страна")
                val city = result.displayName.split(",").firstOrNull()?.trim()
                if (!city.isNullOrBlank()) return city
            } catch (_: Exception) {
                // Fallback на хардкод
            }
        }

        return hardcodedDetectCity(lat, lon)
    }

    /**
     * Хардкод-фоллбэк для городов Челябинской области и Москвы.
     * Internal для использования из UI (MapScreen) напрямую.
     */
    internal fun hardcodedDetectCity(lat: Double, lon: Double): String {
        return when {
            lat in 55.1..55.3 && lon in 61.2..61.6 -> "Челябинске"
            lat in 54.0..54.2 && lon in 61.4..61.7 -> "Троицке"
            lat in 55.0..55.1 && lon in 60.0..60.2 -> "Миассе"
            lat in 55.1..55.2 && lon in 59.5..59.8 -> "Златоусте"
            lat in 53.3..53.5 && lon in 58.9..59.2 -> "Магнитогорске"
            lat in 55.0..55.1 && lon in 61.5..61.7 -> "Копейске"
            lat in 56.0..56.1 && lon in 60.6..60.8 -> "Снежинске"
            lat in 55.7..55.8 && lon in 60.6..60.8 -> "Озёрске"
            lat in 54.4..54.5 && lon in 61.1..61.3 -> "Южноуральске"
            lat in 54.9..55.0 && lon in 57.2..57.4 -> "Аше"
            lat in 55.7..55.8 && lon in 37.5..37.7 -> "Москве"
            else -> "вашем районе"
        }
    }

    /**
     * Преобразует название города (любой падеж) в slug для API прокси.
     * Если город не определён/не известен — fallback на "chelyabinsk".
     */
    fun toCitySlug(cityName: String?): String {
        val lower = cityName?.lowercase()
            ?: return "chelyabinsk"
        return when {
            lower.contains("челябинс") -> "chelyabinsk"
            lower.contains("троицк") -> "troitsk"
            lower.contains("миасс") -> "miass"
            lower.contains("златоуст") -> "zlatoust"
            lower.contains("магнитогорск") -> "magnitogorsk"
            lower.contains("копейск") -> "kopeysk"
            lower.contains("москв") -> "moscow"
            lower.contains("екатеринбург") -> "ekaterinburg"
            lower.contains("тюмен") -> "tyumen"
            lower.contains("перм") -> "perm"
            else -> "chelyabinsk"
        }
    }
}
