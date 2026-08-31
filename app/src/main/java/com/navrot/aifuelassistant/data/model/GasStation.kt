package com.navrot.aifuelassistant.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class GasStation(
    val id: Int,
    val name: String,
    val brand: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val fuelTypes: List<FuelPrice>,
    val queueTime: Int,
    val reliability: Int,
    val dataSources: Set<FuelDataSource> = emptySet(),
    val updatedAt: Long = 0L,
    val confidence: Int = 0,
    val photoEvidence: List<PhotoEvidence> = emptyList(),
    val monumentPhotoUrl: String? = null,
    val entrancePhotoUrl: String? = null,
    val openingHours: String? = null
) : Parcelable

fun GasStation.matchesBrand(selectedBrand: String): Boolean {
    val normSel = selectedBrand.trim().lowercase()
    if (normSel.isBlank()) return true
    val normBrand = this.brand.trim().lowercase()
    val normName = this.name.trim().lowercase()
    return normBrand.contains(normSel) || normName.contains(normSel)
}

fun GasStation.isKnownClosed(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
    val hours = this.openingHours?.trim() ?: return false
    if (hours.isBlank() || hours.equals("24/7", ignoreCase = true) || hours.equals("24 hours", ignoreCase = true)) {
        return false
    }
    try {
        val parts = hours.split("-")
        if (parts.size == 2) {
            val startParts = parts[0].trim().split(":")
            val endParts = parts[1].trim().split(":")
            if (startParts.size == 2 && endParts.size == 2) {
                val startHour = startParts[0].toInt()
                val startMin = startParts[1].toInt()
                val endHour = endParts[0].toInt()
                val endMin = endParts[1].toInt()

                val calendar = java.util.Calendar.getInstance().apply { timeInMillis = currentTimeMs }
                val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                val currentMin = calendar.get(java.util.Calendar.MINUTE)

                val currentTotal = currentHour * 60 + currentMin
                val startTotal = startHour * 60 + startMin
                val endTotal = endHour * 60 + endMin

                return if (startTotal <= endTotal) {
                    currentTotal < startTotal || currentTotal >= endTotal
                } else {
                    currentTotal in endTotal..<startTotal
                }
            }
        }
    } catch (_: Exception) {
    }
    return false
}
