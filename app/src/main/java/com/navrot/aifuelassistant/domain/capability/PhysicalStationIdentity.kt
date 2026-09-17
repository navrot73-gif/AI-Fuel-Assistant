package com.navrot.aifuelassistant.domain.capability

/**
 * Expresses whether and how a fuel data source provides physical station identification.
 */
enum class PhysicalStationIdentity {
    NONE,
    EXTERNAL_ID,
    COORDINATES_ONLY,
    EXTERNAL_ID_AND_COORDINATES
}
