package com.navrot.aifuelassistant.data.diagnostics

/**
 * Глобальный синглтон для отслеживания таймингов старта (Contract Metrics)
 * и диагностики тайлов для экрана "Диагностика карты" (#151 / contract).
 */
object MapDiagnosticsTracker {

    @Volatile
    var emit1Ms: Long = 0L

    @Volatile
    var emit2Ms: Long = 0L

    @Volatile
    var enrichmentMs: Long = 0L

    @Volatile
    var activeTileSource: String = "openfreemap"

    @Volatile
    var tileStatus: String = "ok"

    @Volatile
    var fallbackChainLogs: List<String> = emptyList()

    fun resetStartupTimings() {
        emit1Ms = 0L
        emit2Ms = 0L
        enrichmentMs = 0L
    }

    fun recordTileFallback(log: String) {
        fallbackChainLogs = (fallbackChainLogs + log).takeLast(10)
    }
}
