package com.navrot.aifuelassistant.domain.capability

import com.navrot.aifuelassistant.data.model.FuelDataSource

data class SourceCapabilityDescriptor(
    val sourceId: FuelDataSource,
    val granularity: SourceGranularity,
    val physicalStationIdentity: PhysicalStationIdentity,
    val capabilities: Set<SourceCapability>,
    val providesStationLevelPrice: Boolean,
    val providesStationLevelAvailability: Boolean,
    val providesStationLevelQueue: Boolean,
    val isEligibleForStationFuelSnapshot: Boolean,
    val isEligibleForBestStation: Boolean
) {
    init {
        if (granularity == SourceGranularity.CITY_LEVEL || physicalStationIdentity == PhysicalStationIdentity.NONE) {
            require(!providesStationLevelPrice) {
                "City-level or non-station sources cannot provide station-level price"
            }
            require(!providesStationLevelAvailability) {
                "City-level or non-station sources cannot provide station-level availability"
            }
            require(!providesStationLevelQueue) {
                "City-level or non-station sources cannot provide station-level queue"
            }
            require(!isEligibleForStationFuelSnapshot) {
                "City-level or non-station sources cannot be eligible for StationFuelSnapshot"
            }
            require(!isEligibleForBestStation) {
                "City-level or non-station sources cannot be eligible for BestStation"
            }
        }
    }
}
