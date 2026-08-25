package com.navrot.aifuelassistant.data.model

enum class ReportPeriod {
    LAST_7_DAYS,
    LAST_30_DAYS,
    LAST_90_DAYS,
    LAST_YEAR,
    ALL_TIME;

    fun getPeriodRange(nowMillis: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val startMillis = when (this) {
            LAST_7_DAYS -> nowMillis - 7L * 24 * 60 * 60 * 1000
            LAST_30_DAYS -> nowMillis - 30L * 24 * 60 * 60 * 1000
            LAST_90_DAYS -> nowMillis - 90L * 24 * 60 * 60 * 1000
            LAST_YEAR -> nowMillis - 365L * 24 * 60 * 60 * 1000
            ALL_TIME -> 0L
        }
        return Pair(startMillis, nowMillis)
    }
}
