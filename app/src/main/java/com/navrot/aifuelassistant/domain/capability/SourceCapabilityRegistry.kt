package com.navrot.aifuelassistant.domain.capability

import com.navrot.aifuelassistant.data.model.FuelDataSource

object SourceCapabilityRegistry {

    private val descriptors: Map<FuelDataSource, SourceCapabilityDescriptor> = mapOf(
        FuelDataSource.BENZONAVT to SourceCapabilityDescriptor(
            sourceId = FuelDataSource.BENZONAVT,
            granularity = SourceGranularity.CITY_LEVEL,
            physicalStationIdentity = PhysicalStationIdentity.NONE,
            capabilities = setOf(SourceCapability.PRICE, SourceCapability.TIMESTAMP, SourceCapability.PROVENANCE),
            providesStationLevelPrice = false,
            providesStationLevelAvailability = false,
            providesStationLevelQueue = false,
            isEligibleForStationFuelSnapshot = false,
            isEligibleForBestStation = false
        ),
        FuelDataSource.RUSSIABASE to SourceCapabilityDescriptor(
            sourceId = FuelDataSource.RUSSIABASE,
            granularity = SourceGranularity.STATION_LEVEL,
            physicalStationIdentity = PhysicalStationIdentity.EXTERNAL_ID_AND_COORDINATES,
            capabilities = setOf(
                SourceCapability.PRICE,
                SourceCapability.AVAILABILITY,
                SourceCapability.COORDINATES,
                SourceCapability.STATION_ID,
                SourceCapability.TIMESTAMP,
                SourceCapability.PROVENANCE
            ),
            providesStationLevelPrice = true,
            providesStationLevelAvailability = true,
            providesStationLevelQueue = false,
            isEligibleForStationFuelSnapshot = true,
            isEligibleForBestStation = true
        ),
        FuelDataSource.OVERPASS to SourceCapabilityDescriptor(
            sourceId = FuelDataSource.OVERPASS,
            granularity = SourceGranularity.STATION_LEVEL,
            physicalStationIdentity = PhysicalStationIdentity.COORDINATES_ONLY,
            capabilities = setOf(
                SourceCapability.COORDINATES,
                SourceCapability.STATION_ID,
                SourceCapability.TIMESTAMP,
                SourceCapability.PROVENANCE
            ),
            providesStationLevelPrice = false,
            providesStationLevelAvailability = false,
            providesStationLevelQueue = false,
            isEligibleForStationFuelSnapshot = true,
            isEligibleForBestStation = true
        ),
        FuelDataSource.USER_REPORT to SourceCapabilityDescriptor(
            sourceId = FuelDataSource.USER_REPORT,
            granularity = SourceGranularity.STATION_LEVEL,
            physicalStationIdentity = PhysicalStationIdentity.EXTERNAL_ID,
            capabilities = setOf(
                SourceCapability.PRICE,
                SourceCapability.AVAILABILITY,
                SourceCapability.QUEUE,
                SourceCapability.STATION_ID,
                SourceCapability.TIMESTAMP,
                SourceCapability.PROVENANCE
            ),
            providesStationLevelPrice = true,
            providesStationLevelAvailability = true,
            providesStationLevelQueue = true,
            isEligibleForStationFuelSnapshot = true,
            isEligibleForBestStation = true
        ),
        FuelDataSource.DEMO to SourceCapabilityDescriptor(
            sourceId = FuelDataSource.DEMO,
            granularity = SourceGranularity.STATION_LEVEL,
            physicalStationIdentity = PhysicalStationIdentity.EXTERNAL_ID,
            capabilities = setOf(
                SourceCapability.PRICE,
                SourceCapability.AVAILABILITY,
                SourceCapability.STATION_ID,
                SourceCapability.TIMESTAMP,
                SourceCapability.PROVENANCE
            ),
            providesStationLevelPrice = true,
            providesStationLevelAvailability = true,
            providesStationLevelQueue = false,
            isEligibleForStationFuelSnapshot = true,
            isEligibleForBestStation = true
        )
    )

    fun getDescriptor(source: FuelDataSource): SourceCapabilityDescriptor {
        return descriptors[source] ?: createDefaultUnverifiedDescriptor(source)
    }

    fun canFeedStationFuelSnapshot(source: FuelDataSource): Boolean {
        return getDescriptor(source).isEligibleForStationFuelSnapshot
    }

    fun canFeedBestStation(source: FuelDataSource): Boolean {
        return getDescriptor(source).isEligibleForBestStation
    }

    private fun createDefaultUnverifiedDescriptor(source: FuelDataSource): SourceCapabilityDescriptor {
        return SourceCapabilityDescriptor(
            sourceId = source,
            granularity = SourceGranularity.UNKNOWN,
            physicalStationIdentity = PhysicalStationIdentity.NONE,
            capabilities = emptySet(),
            providesStationLevelPrice = false,
            providesStationLevelAvailability = false,
            providesStationLevelQueue = false,
            isEligibleForStationFuelSnapshot = false,
            isEligibleForBestStation = false
        )
    }
}
