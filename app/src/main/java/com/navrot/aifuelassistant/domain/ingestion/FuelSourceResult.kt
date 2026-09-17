package com.navrot.aifuelassistant.domain.ingestion

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.domain.intelligence.FuelSourceObservation

enum class FuelSourceStatus {
    HEALTHY,
    DEGRADED,
    STALE,
    FAILED,
    DISABLED
}

data class SourceIngestionMetrics(
    val recordsReceived: Int = 0,
    val recordsParsed: Int = 0,
    val stationsMatched: Int = 0,
    val stationsUnmatched: Int = 0,
    val invalidRecords: Int = 0,
    val observationsCreated: Int = 0,
    val observationsRejected: Int = 0
)

data class FuelSourceResult(
    val sourceId: FuelDataSource,
    val status: FuelSourceStatus,
    val observations: List<FuelSourceObservation> = emptyList(),
    val rawObservations: List<IngestionObservation> = emptyList(),
    val metrics: SourceIngestionMetrics = SourceIngestionMetrics(),
    val fetchedAt: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)
