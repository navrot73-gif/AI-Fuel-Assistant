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
) {
    init {
        require(recordsReceived >= 0) { "recordsReceived must be non-negative" }
        require(recordsParsed >= 0) { "recordsParsed must be non-negative" }
        require(stationsMatched >= 0) { "stationsMatched must be non-negative" }
        require(stationsUnmatched >= 0) { "stationsUnmatched must be non-negative" }
        require(invalidRecords >= 0) { "invalidRecords must be non-negative" }
        require(observationsCreated >= 0) { "observationsCreated must be non-negative" }
        require(observationsRejected >= 0) { "observationsRejected must be non-negative" }
        require(recordsParsed <= recordsReceived) { "recordsParsed ($recordsParsed) cannot exceed recordsReceived ($recordsReceived)" }
    }
}

data class FuelSourceResult(
    val sourceId: FuelDataSource,
    val status: FuelSourceStatus,
    val observations: List<FuelSourceObservation> = emptyList(),
    val rawObservations: List<IngestionObservation> = emptyList(),
    val metrics: SourceIngestionMetrics = SourceIngestionMetrics(),
    val fetchedAt: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
) {
    init {
        require(fetchedAt >= 0) { "fetchedAt timestamp must be non-negative" }
        if (status == FuelSourceStatus.FAILED) {
            require(!errorMessage.isNullAndBlank()) { "FAILED status requires a non-blank errorMessage" }
        }
    }

    private companion object {
        fun String?.isNullAndBlank(): Boolean = this == null || this.isBlank()
    }
}
