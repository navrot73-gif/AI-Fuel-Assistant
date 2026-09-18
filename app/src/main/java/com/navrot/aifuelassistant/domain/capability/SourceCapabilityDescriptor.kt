package com.navrot.aifuelassistant.domain.capability

import com.navrot.aifuelassistant.data.model.FuelDataSource

/**
 * Machine-readable descriptor detailing the granularity, identity model, data capabilities,
 * and domain eligibility for a [FuelDataSource].
 */
data class SourceCapabilityDescriptor(
    val sourceId: FuelDataSource,
    val granularity: SourceGranularity,
    val physicalStationIdentity: PhysicalStationIdentity,
    val capabilities: Set<SourceCapability>,
    val canProvideStationLevelPrice: Boolean,
    val canProvideStationLevelAvailability: Boolean,
    val canProvideStationLevelQueue: Boolean = false,
    val eligibleForStationFuelSnapshot: Boolean,
    val eligibleForBestStationRecommendation: Boolean
) {
    init {
        validate()
    }

    /**
     * Validates capability descriptor integrity and enforces semantic boundaries.
     * Throws [IllegalArgumentException] if contradictory or invalid configurations are provided.
     */
    fun validate() {
        if (granularity == SourceGranularity.CITY_LEVEL) {
            require(physicalStationIdentity == PhysicalStationIdentity.NONE) {
                "CITY_LEVEL source $sourceId cannot have physical station identity ($physicalStationIdentity)"
            }
            require(!eligibleForStationFuelSnapshot) {
                "CITY_LEVEL source $sourceId cannot be eligible for StationFuelSnapshot"
            }
            require(!canProvideStationLevelPrice) {
                "CITY_LEVEL source $sourceId cannot provide station-level price"
            }
            require(!canProvideStationLevelAvailability) {
                "CITY_LEVEL source $sourceId cannot provide station-level availability"
            }
            require(!canProvideStationLevelQueue) {
                "CITY_LEVEL source $sourceId cannot provide station-level queue"
            }
            require(!eligibleForBestStationRecommendation) {
                "CITY_LEVEL source $sourceId cannot be eligible for BestStation recommendation"
            }
        }

        if (canProvideStationLevelPrice) {
            require(capabilities.contains(SourceCapability.PRICE)) {
                "Source $sourceId claims station-level price capability without PRICE in capabilities"
            }
        }

        if (canProvideStationLevelAvailability) {
            require(capabilities.contains(SourceCapability.AVAILABILITY)) {
                "Source $sourceId claims station-level availability capability without AVAILABILITY in capabilities"
            }
        }

        if (canProvideStationLevelQueue) {
            require(capabilities.contains(SourceCapability.QUEUE)) {
                "Source $sourceId claims station-level queue capability without QUEUE in capabilities"
            }
        }

        if (eligibleForBestStationRecommendation) {
            require(physicalStationIdentity != PhysicalStationIdentity.NONE) {
                "Source $sourceId cannot be eligible for BestStation recommendation without physical station identity"
            }
            require(canProvideStationLevelPrice || canProvideStationLevelAvailability) {
                "Source $sourceId cannot be eligible for BestStation recommendation without station-level price or availability"
            }
        }

        if (capabilities.contains(SourceCapability.COORDINATES)) {
            require(
                physicalStationIdentity == PhysicalStationIdentity.COORDINATES_ONLY ||
                        physicalStationIdentity == PhysicalStationIdentity.EXTERNAL_ID_AND_COORDINATES
            ) {
                "Source $sourceId has COORDINATES capability but physicalStationIdentity is $physicalStationIdentity"
            }
        }

        if (capabilities.contains(SourceCapability.STATION_ID)) {
            require(
                physicalStationIdentity == PhysicalStationIdentity.EXTERNAL_ID ||
                        physicalStationIdentity == PhysicalStationIdentity.EXTERNAL_ID_AND_COORDINATES
            ) {
                "Source $sourceId has STATION_ID capability but physicalStationIdentity is $physicalStationIdentity"
            }
        }
    }

    fun canProvide(capability: SourceCapability): Boolean = capabilities.contains(capability)
}
