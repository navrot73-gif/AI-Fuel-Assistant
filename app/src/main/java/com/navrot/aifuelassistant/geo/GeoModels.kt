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

/** Построенный маршрут между двумя точками. */
data class RouteResult(
    val distanceMeters: Double,
    val durationSeconds: Double,
    /** Полилиния маршрута для отрисовки на OSMDroid (список точек). */
    val points: List<GeoPoint>
)

sealed class GeoException(message: String) : Exception(message) {
    class NetworkError(message: String) : GeoException(message)
    class NoResults(message: String) : GeoException(message)
    class InvalidResponse(message: String) : GeoException(message)
}
