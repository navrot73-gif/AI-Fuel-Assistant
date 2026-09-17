package com.navrot.aifuelassistant.domain.ingestion

data class FuelSourceRequest(
    val targetCity: String? = null,
    val bbox: BoundingBox? = null,
    val fuelTypes: List<String> = listOf("AI-92", "AI-95"),
    val timeoutMs: Long = 5000L
) {
    data class BoundingBox(
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double
    )
}
