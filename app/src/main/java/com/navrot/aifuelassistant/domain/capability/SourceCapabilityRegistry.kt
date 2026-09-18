package com.navrot.aifuelassistant.domain.capability

import com.navrot.aifuelassistant.data.model.FuelDataSource
import java.util.concurrent.ConcurrentHashMap

/**
 * Central authoritative registry for source capabilities, granularity, physical identity models,
 * and domain eligibility rules.
 */
object SourceCapabilityRegistry {

    private val registry = ConcurrentHashMap<FuelDataSource, SourceCapabilityDescriptor>()

    init {
        resetToDefaults()
    }

    /**
     * Resets the registry to default baseline registrations for verified sources with code implementations.
     */
    fun resetToDefaults() {
        registry.clear()

        // 1. BENZONAVT — Real Source Adapter (City-level aggregate)
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

        // 2. RUSSIABASE — Real Source Provider/Matcher (Station-level)
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

        // 3. OVERPASS — Real Source Provider (Station-level with coordinates)
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

        // 4. USER_REPORT — Real In-App Price Override Repository (Station-level)
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

        // 5. DEMO — Baseline static station dataset
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

    /**
     * Registers or updates a source capability descriptor. Performs validation before registration.
     */
    fun register(descriptor: SourceCapabilityDescriptor) {
        descriptor.validate()
        registerInternal(descriptor)
    }

    private fun registerInternal(descriptor: SourceCapabilityDescriptor) {
        registry[descriptor.sourceId] = descriptor
    }

    /**
     * Returns the authoritative capability descriptor for the given [FuelDataSource].
     * Unregistered/unverified sources return a conservative default descriptor with UNKNOWN granularity and NONE physical identity.
     */
    fun getCapability(sourceId: FuelDataSource): SourceCapabilityDescriptor {
        return registry[sourceId] ?: createDefaultDescriptor(sourceId)
    }

    fun canFeedStationFuelSnapshot(sourceId: FuelDataSource): Boolean {
        return getCapability(sourceId).eligibleForStationFuelSnapshot
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
