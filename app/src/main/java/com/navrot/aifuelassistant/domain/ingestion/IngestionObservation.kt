package com.navrot.aifuelassistant.domain.ingestion

import com.navrot.aifuelassistant.data.model.FuelDataSource
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
)
