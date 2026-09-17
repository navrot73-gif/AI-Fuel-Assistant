package com.navrot.aifuelassistant.domain.ingestion

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.domain.intelligence.FuelSourceObservation
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus

data class IngestionObservation(
    val sourceId: FuelDataSource,
    val externalStationId: String,
    val stationName: String? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val fuelType: String,
    val availability: FuelAvailabilityStatus = FuelAvailabilityStatus.UNKNOWN,
    val price: Double? = null,
    val observedAt: Long? = null,
    val receivedAt: Long = System.currentTimeMillis(),
    val rawReference: String? = null
) {
    val canonicalFuelType: String

    init {
        require(externalStationId.isNotBlank()) { "externalStationId must not be blank" }
        require(fuelType.isNotBlank()) { "fuelType must not be blank" }

        canonicalFuelType = FuelSourceRequest.normalizeFuelType(fuelType)
            ?: throw IllegalArgumentException("Unsupported fuel type '$fuelType' for IngestionObservation. Only canonical AI-92 and AI-95 are supported.")

        require(receivedAt >= 0) { "receivedAt timestamp must be non-negative" }

        if (price != null) {
            require(price >= 0.0) { "price must be non-negative if present" }
        }
        if (latitude != null) {
            require(latitude in -90.0..90.0) { "latitude must be in [-90, 90]" }
        }
        if (longitude != null) {
            require(longitude in -180.0..180.0) { "longitude must be in [-180, 180]" }
        }
        if (observedAt != null) {
            require(observedAt >= 0) { "observedAt timestamp must be non-negative" }
        }
        if (rawReference != null) {
            require(rawReference.length <= MAX_RAW_REFERENCE_LENGTH) {
                "rawReference length (${rawReference.length}) exceeds maximum allowed limit ($MAX_RAW_REFERENCE_LENGTH characters). Provenance reference must not contain raw HTML/payloads."
            }
        }
    }

    /**
     * Converts this raw/ingested observation into a canonical domain [FuelSourceObservation]
     * for a matched station ID. Guarantees that downstream domain logic receives only [canonicalFuelType].
     */
    fun toFuelSourceObservation(stationId: Int): FuelSourceObservation {
        return FuelSourceObservation(
            stationId = stationId,
            fuelType = canonicalFuelType,
            availability = availability,
            price = price,
            observedAt = observedAt ?: receivedAt,
            source = sourceId,
            referenceId = externalStationId
        )
    }

    companion object {
        const val MAX_RAW_REFERENCE_LENGTH = 512
    }
}
