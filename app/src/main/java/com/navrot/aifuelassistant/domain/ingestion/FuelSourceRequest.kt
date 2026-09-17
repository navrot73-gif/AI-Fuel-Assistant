package com.navrot.aifuelassistant.domain.ingestion

data class FuelSourceRequest(
    val targetCity: String? = null,
    val bbox: BoundingBox? = null,
    val fuelTypes: List<String> = listOf(CANONICAL_AI92, CANONICAL_AI95),
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS
) {
    val normalizedFuelTypes: List<String>

    init {
        require(timeoutMs > 0) { "timeoutMs must be positive" }
        require(fuelTypes.isNotEmpty()) { "fuelTypes must not be empty" }
        if (targetCity != null) {
            require(targetCity.isNotBlank()) { "targetCity must not be blank if specified" }
        }

        normalizedFuelTypes = fuelTypes.map { raw ->
            require(raw.isNotBlank()) { "fuelTypes must not contain blank entries" }
            normalizeFuelType(raw)
                ?: throw IllegalArgumentException("Unsupported or unmapped fuel type: '$raw'. Supported canonical gasoline types are AI-92 and AI-95.")
        }.distinct()
    }

    data class BoundingBox(
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double
    ) {
        init {
            require(minLat in -90.0..90.0) { "minLat must be in [-90, 90]" }
            require(maxLat in -90.0..90.0) { "maxLat must be in [-90, 90]" }
            require(minLon in -180.0..180.0) { "minLon must be in [-180, 180]" }
            require(maxLon in -180.0..180.0) { "maxLon must be in [-180, 180]" }
            require(minLat <= maxLat) { "minLat must be <= maxLat" }
            require(minLon <= maxLon) { "minLon must be <= maxLon" }
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val CANONICAL_AI92 = "AI-92"
        const val CANONICAL_AI95 = "AI-95"

        /**
         * Normalizes raw fuel type strings to canonical domain representations ("AI-92", "AI-95").
         * Returns null if unmapped or unsupported (e.g. DIESEL, AI-98, AI-100).
         */
        fun normalizeFuelType(raw: String): String? {
            val clean = raw.trim().uppercase()
            return when (clean) {
                "AI-92", "АИ-92", "AI92", "АИ92", "92", "RON92", "GASOLINE92" -> CANONICAL_AI92
                "AI-95", "АИ-95", "AI95", "АИ95", "95", "RON95", "GASOLINE95" -> CANONICAL_AI95
                else -> null
            }
        }
    }
}
