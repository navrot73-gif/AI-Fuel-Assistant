package com.navrot.aifuelassistant.domain.capability

import com.navrot.aifuelassistant.data.model.FuelDataSource
import java.util.concurrent.ConcurrentHashMap

object SourceCapabilityRegistry {

    private val registry = ConcurrentHashMap<FuelDataSource, SourceCapabilityDescriptor>()

    init {
        resetToDefaults()
    }

    fun resetToDefaults() {
        registry.clear()

        registerInternal(
            SourceCapabilityDescriptor(
                sourceId = FuelDataSource.BENZONAVT,
                granularity = SourceGranularity.CITY_LEVEL,
                physicalStationIdentity = PhysicalStationIdentity.NONE,
                capabilities = setOf(
                    SourceCapability.PRICE,
                    SourceCapability.AVAILABILITY,
                    SourceCapability.TIMESTAMP,
                    SourceCapability.PROVENANCE
                ),
                canProvideStationLevelPrice = false,
                canProvideStationLevelAvailability = false,
                canProvideStationLevelQueue = false,
                eligibleForStationFuelSnapshot = false,
                eligibleForBestStationRecommendation = false
            )
        )

        registerInternal(
            SourceCapabilityDescriptor(
                sourceId = FuelDataSource.RUSSIABASE,
                granularity = SourceGranularity.STATION_LEVEL,
                physicalStationIdentity = PhysicalStationIdentity.EXTERNAL_ID,
                capabilities = setOf(
                    SourceCapability.PRICE,
                    SourceCapability.AVAILABILITY,
                    SourceCapability.STATION_ID,
                    SourceCapability.TIMESTAMP,
                    SourceCapability.PROVENANCE
                ),
                canProvideStationLevelPrice = true,
                canProvideStationLevelAvailability = true,
                canProvideStationLevelQueue = false,
                eligibleForStationFuelSnapshot = true,
                eligibleForBestStationRecommendation = true
            )
        )

        registerInternal(
            SourceCapabilityDescriptor(
                sourceId = FuelDataSource.OVERPASS,
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
                canProvideStationLevelPrice = true,
                canProvideStationLevelAvailability = true,
                canProvideStationLevelQueue = false,
                eligibleForStationFuelSnapshot = true,
                eligibleForBestStationRecommendation = true
            )
        )

        registerInternal(
            SourceCapabilityDescriptor(
                sourceId = FuelDataSource.USER_REPORT,
                granularity = SourceGranularity.STATION_LEVEL,
                physicalStationIdentity = PhysicalStationIdentity.EXTERNAL_ID,
                capabilities = setOf(
                    SourceCapability.PRICE,
                    SourceCapability.AVAILABILITY,
                    SourceCapability.STATION_ID,
                    SourceCapability.TIMESTAMP,
                    SourceCapability.PROVENANCE
                ),
                canProvideStationLevelPrice = true,
                canProvideStationLevelAvailability = true,
                canProvideStationLevelQueue = false,
                eligibleForStationFuelSnapshot = true,
                eligibleForBestStationRecommendation = true
            )
        )

        registerInternal(
            SourceCapabilityDescriptor(
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
                canProvideStationLevelPrice = true,
                canProvideStationLevelAvailability = true,
                canProvideStationLevelQueue = false,
                eligibleForStationFuelSnapshot = true,
                eligibleForBestStationRecommendation = true
            )
        )
    }

    fun register(descriptor: SourceCapabilityDescriptor) {
        descriptor.validate()
        registerInternal(descriptor)
    }

    private fun registerInternal(descriptor: SourceCapabilityDescriptor) {
        registry[descriptor.sourceId] = descriptor
    }

    fun getCapability(sourceId: FuelDataSource): SourceCapabilityDescriptor {
        return registry[sourceId] ?: createDefaultDescriptor(sourceId)
    }

    fun getDescriptor(sourceId: FuelDataSource): SourceCapabilityDescriptor {
        return getCapability(sourceId)
    }

    fun canFeedStationFuelSnapshot(sourceId: FuelDataSource): Boolean {
        return getCapability(sourceId).eligibleForStationFuelSnapshot
    }

    fun canFeedBestStation(sourceId: FuelDataSource): Boolean {
        return getCapability(sourceId).eligibleForBestStationRecommendation
    }

    fun canFeedBestStationRecommendation(sourceId: FuelDataSource): Boolean {
        return getCapability(sourceId).eligibleForBestStationRecommendation
    }

    fun canProvideStationLevelPrice(sourceId: FuelDataSource): Boolean {
        return getCapability(sourceId).canProvideStationLevelPrice
    }

    fun canProvideStationLevelAvailability(sourceId: FuelDataSource): Boolean {
        return getCapability(sourceId).canProvideStationLevelAvailability
    }

    private fun createDefaultDescriptor(sourceId: FuelDataSource): SourceCapabilityDescriptor {
        return SourceCapabilityDescriptor(
            sourceId = sourceId,
            granularity = SourceGranularity.UNKNOWN,
            physicalStationIdentity = PhysicalStationIdentity.NONE,
            capabilities = emptySet(),
            canProvideStationLevelPrice = false,
            canProvideStationLevelAvailability = false,
            canProvideStationLevelQueue = false,
            eligibleForStationFuelSnapshot = false,
            eligibleForBestStationRecommendation = false
        )
    }
}
