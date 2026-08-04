package com.navrot.aifuelassistant.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class FuelPrice(
    val type: String,
    val price: Double,
    val available: Boolean,
    val source: FuelDataSource = FuelDataSource.DEMO,
    val updatedAt: Long = 0L,
    val confidence: Int = 0,
    val photoEvidence: PhotoEvidence? = null
) : Parcelable
