package com.navrot.aifuelassistant.domain.predictive

/**
 * Metadata tracking version and learning statistics of the personal model.
 */
data class PersonalModelMetadata(
    val vehicleId: Long,
    val modelVersion: Int = CURRENT_VERSION,
    val trainedSamples: Int = 0,
    val biasAdjustment: Double = 0.0,
    val lastUpdatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
