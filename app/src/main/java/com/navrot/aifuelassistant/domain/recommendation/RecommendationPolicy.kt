package com.navrot.aifuelassistant.domain.recommendation

object RecommendationPolicy {
    // Freshness thresholds (ms)
    const val FRESH_THRESHOLD_MS = 15 * 60 * 1000L       // 0–15 min: high freshness
    const val NORMAL_THRESHOLD_MS = 60 * 60 * 1000L      // 15–60 min: normal freshness
    const val STALE_THRESHOLD_MS = 6 * 60 * 60 * 1000L    // 1–6 hours: stale

    // Vehicle refill defaults
    const val DEFAULT_REFILL_LITERS = 30.0                // L
    const val DEFAULT_CONSUMPTION_L_PER_100KM = 8.0     // L / 100km
    const val QUEUE_TIME_COST_PER_MIN_RUB = 0.5          // rub per queue minute

    // Scoring weights
    const val PRICE_WEIGHT = 1.0
    const val DISTANCE_WEIGHT = 1.2
    const val QUEUE_WEIGHT = 0.5
    const val RELIABILITY_WEIGHT = 0.2
    const val NO_FUEL_PENALTY = 1000.0
}
