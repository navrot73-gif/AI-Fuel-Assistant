package com.navrot.aifuelassistant.domain.intelligence

enum class FuelFreshness {
    VERY_FRESH, // 0–15 minutes
    FRESH,      // 15–60 minutes
    AGING,      // 1–6 hours
    STALE,      // >6 hours
    UNKNOWN;    // timestamp unavailable

    companion object {
        const val VERY_FRESH_THRESHOLD_MS = 15 * 60 * 1000L
        const val FRESH_THRESHOLD_MS = 60 * 60 * 1000L
        const val AGING_THRESHOLD_MS = 6 * 60 * 60 * 1000L

        fun calculateFreshness(observedAt: Long?, now: Long = System.currentTimeMillis()): FuelFreshness {
            if (observedAt == null || observedAt <= 0L) {
                return UNKNOWN
            }
            val diffMs = maxOf(0L, now - observedAt)
            return when {
                diffMs <= VERY_FRESH_THRESHOLD_MS -> VERY_FRESH
                diffMs <= FRESH_THRESHOLD_MS -> FRESH
                diffMs <= AGING_THRESHOLD_MS -> AGING
                else -> STALE
            }
        }
    }
}
