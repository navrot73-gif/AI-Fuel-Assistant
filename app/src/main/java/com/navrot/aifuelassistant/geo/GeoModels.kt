package com.navrot.aifuelassistant.geo

/** Точка на карте. */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)

/** Результат геокодинга — адрес превращённый в координаты. */
data class GeocodingResult(
    val point: GeoPoint,
    val displayName: String
)

sealed class GeoException(message: String) : Exception(message) {
    class NetworkError(message: String) : GeoException(message)
    class NoResults(message: String) : GeoException(message)
    class InvalidResponse(message: String) : GeoException(message)
}
