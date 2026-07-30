package com.navrot.aifuelassistant.data.model

/**
 * Evidence attached to a fuel-station report.
 * The photo itself is stored outside this model; photoUri points to local/cloud storage.
 */
data class PhotoEvidence(
    val photoUri: String,
    val capturedAt: Long,
    val latitude: Double?,
    val longitude: Double?,
    val stationMatchScore: Int? = null,
    val ocrConfidence: Int? = null,
    val verifiedByAi: Boolean = false,
    val notes: String? = null
)
