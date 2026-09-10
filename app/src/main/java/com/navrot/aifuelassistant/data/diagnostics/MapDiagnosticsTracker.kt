package com.navrot.aifuelassistant.data.diagnostics

/**
 * Глобальный синглтон для отслеживания таймингов старта (Contract Metrics)
 * и диагностики тайлов для экрана "Диагностика карты" (#151 / contract).
 */
object MapDiagnosticsTracker {

    @Volatile
    var t0Ms: Long = System.currentTimeMillis()

    @Volatile
    var emit1Ms: Long = 0L

    @Volatile
    var emit2Ms: Long = 0L

    @Volatile
    var enrichmentMs: Long = 0L

    @Volatile
    var cityResolveMs: Long = 0L

    @Volatile
    var cityResolveSource: String = "cache"

    @Volatile
    var aiPath: String = "local"

    @Volatile
    var routeTest: String = "загрузка..."

    @Volatile
    var activeTileSource: String = "openfreemap"

    @Volatile
    var tileStatus: String = "ok"

    @Volatile
    var fallbackChainLogs: List<String> = emptyList()

    @Volatile
    var registrySize: Int = 0

    @Volatile
    var benzonavtUnmatched: Int = 0

    @Volatile
    var russiabaseUnmatched: Int = 0

    @Volatile
    var registryCount: Int = 0

    @Volatile
    var perSourceCount: Map<String, Int> = emptyMap()

    @Volatile
    var mergeConflicts: Int = 0

    @Volatile
    var firstEmitMs: Long = 0L

    @Volatile
    var firstEmitSource: String = "cache"

    @Volatile
    var activeSources: String = "registry"

    fun resetStartupTimings() {
        activeSources = "registry"
        t0Ms = System.currentTimeMillis()
        emit1Ms = 0L
        emit2Ms = 0L
        enrichmentMs = 0L
        cityResolveMs = 0L
        cityResolveSource = "cache"
        aiPath = "local"
        routeTest = "загрузка..."
        registrySize = 0
        benzonavtUnmatched = 0
        russiabaseUnmatched = 0
        registryCount = 0
        perSourceCount = emptyMap()
        mergeConflicts = 0
        firstEmitMs = 0L
        firstEmitSource = "cache"
    }

    fun recordTileFallback(log: String) {
        fallbackChainLogs = (fallbackChainLogs + log).takeLast(10)
    }

    fun exportDiagnosticsText(): String {
        val perSourceFormatted = perSourceCount.entries.joinToString(", ") { "${it.key}: ${it.value}" }.ifEmpty { "none" }
        val fallbacksStr = fallbackChainLogs.joinToString(" -> ").ifEmpty { "none" }
        return """
            === MAP DIAGNOSTICS BASELINE ===
            activeSources: $activeSources
            registryCount: $registryCount (size: $registrySize)
            perSourceCount: {$perSourceFormatted}
            mergeConflicts: $mergeConflicts
            firstEmitMs: ${firstEmitMs}ms
            firstEmitSource: $firstEmitSource
            emit1Ms: ${emit1Ms}ms
            emit2Ms: ${emit2Ms}ms
            enrichmentMs: ${enrichmentMs}ms
            cityResolveMs: ${cityResolveMs}ms
            cityResolveSource: $cityResolveSource
            aiPath: $aiPath
            routeTest: $routeTest
            activeTileSource: $activeTileSource
            tileStatus: $tileStatus
            fallbackChainLogs: $fallbacksStr
            benzonavtUnmatched: $benzonavtUnmatched
            russiabaseUnmatched: $russiabaseUnmatched
        """.trimIndent()
    }
}
