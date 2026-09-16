package com.navrot.aifuelassistant.domain.realtime

object FuelDataQualityPolicy {
    // TTL thresholds in milliseconds
    const val AVAILABILITY_TTL_MS = 15 * 60 * 1000L       // 15 min: availability rapidly ages
    const val PRICE_TTL_MS = 60 * 60 * 1000L              // 60 min: price normal TTL
    const val STALE_TTL_MS = 6 * 60 * 60 * 1000L          // 6 hours: stale boundary

    // Price conflict tolerance percentage (e.g., 2% variance allowed before triggering PRICE_CONFLICT)
    const val PRICE_CONFLICT_TOLERANCE_PERCENT = 0.02

    // Reliable source min score threshold
    const val RELIABLE_SOURCE_MIN_SCORE = 0.70
}
