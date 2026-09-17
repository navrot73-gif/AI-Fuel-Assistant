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
     * Resets the registry to default baseline registrations for known sources.
     */
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

        val stationLevelSources = listOf(
            FuelDataSource.RUSSIABASE,
            FuelDataSource.OVERPASS,
            FuelDataSource.USER_REPORT,
            FuelDataSource.DEMO,
            FuelDataSource.OFFICIAL_STATION,
            FuelDataSource.GDEBENZ,
            FuelDataSource.TWO_GIS,
            FuelDataSource.YANDEX,
            FuelDataSource.T_BANK,
            FuelDataSource.TELEGRAM,
            FuelDataSource.VK,
            FuelDataSource.PHOTO_EVIDENCE
        )

        for (src in stationLevelSources) {
            val identity = if (src == FuelDataSource.OVERPASS) {
                PhysicalStationIdentity.EXTERNAL_ID_AND_COORDINATES
            } else {
                PhysicalStationIdentity.EXTERNAL_ID
            }
            val caps = if (src == FuelDataSource.OVERPASS) {
                setOf(
                    SourceCapability.PRICE,
                    SourceCapability.AVAILABILITY,
                    SourceCapability.COORDINATES,
                    SourceCapability.STATION_ID,
                    SourceCapability.TIMESTAMP,
                    SourceCapability.PROVENANCE
                )
            } else {
                setOf(
                    SourceCapability.PRICE,
                    SourceCapability.AVAILABILITY,
                    SourceCapability.STATION_ID,
                    SourceCapability.TIMESTAMP,
                    SourceCapability.PROVENANCE
                )
            }
            registerInternal(
                SourceCapabilityDescriptor(
                    sourceId = src,
                    granularity = SourceGranularity.STATION_LEVEL,
                    physicalStationIdentity = identity,
                    capabilities = caps,
                    canProvideStationLevelPrice = true,
                    canProvideStationLevelAvailability = true,
                    canProvideStationLevelQueue = false,
                    eligibleForStationFuelSnapshot = true,
                    eligibleForBestStationRecommendation = true
                )
            )
        }
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
     * Unregistered sources return a default descriptor with UNKNOWN granularity and NONE physical identity.
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
