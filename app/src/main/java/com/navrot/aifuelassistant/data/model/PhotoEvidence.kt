package com.navrot.aifuelassistant.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PhotoEvidence(
    val photoUri: String,
    val capturedAt: Long,
    val latitude: Double?,
    val longitude: Double?,
    val stationMatchScore: Int? = null,
    val ocrConfidence: Int? = null,
    val verifiedByAi: Boolean = false,
    val notes: String? = null
) : Parcelable
