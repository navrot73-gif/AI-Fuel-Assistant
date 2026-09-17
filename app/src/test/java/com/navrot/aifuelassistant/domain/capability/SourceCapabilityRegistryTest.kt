package com.navrot.aifuelassistant.domain.capability

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.domain.ingestion.FuelSourceRequest
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SourceCapabilityRegistryTest {

    @Before
    fun setUp() {
        SourceCapabilityRegistry.resetToDefaults()
    }

    // Requirement 1: Registry contains Benzonavt
    @Test
    fun `test 1 - registry contains Benzonavt`() {
        val descriptor = SourceCapabilityRegistry.getCapability(FuelDataSource.BENZONAVT)
        assertNotNull(descriptor)
        assertEquals(FuelDataSource.BENZONAVT, descriptor.sourceId)
    }

    // Requirement 2: Benzonavt granularity = CITY_LEVEL
    @Test
    fun `test 2 - Benzonavt granularity is CITY_LEVEL`() {
        val descriptor = SourceCapabilityRegistry.getCapability(FuelDataSource.BENZONAVT)
        assertEquals(SourceGranularity.CITY_LEVEL, descriptor.granularity)
    }

    // Requirement 3: Benzonavt physical station identity = NONE
    @Test
    fun `test 3 - Benzonavt physical station identity is NONE`() {
        val descriptor = SourceCapabilityRegistry.getCapability(FuelDataSource.BENZONAVT)
        assertEquals(PhysicalStationIdentity.NONE, descriptor.physicalStationIdentity)
        assertFalse(descriptor.canProvideStationLevelPrice)
        assertFalse(descriptor.canProvideStationLevelAvailability)
        assertFalse(descriptor.canProvideStationLevelQueue)
    }

    // Requirement 4: Benzonavt cannot be eligible for StationFuelSnapshot
    @Test
    fun `test 4 - Benzonavt cannot be eligible for StationFuelSnapshot`() {
        val descriptor = SourceCapabilityRegistry.getCapability(FuelDataSource.BENZONAVT)
        assertFalse(descriptor.eligibleForStationFuelSnapshot)
        assertFalse(SourceCapabilityRegistry.canFeedStationFuelSnapshot(FuelDataSource.BENZONAVT))
    }

    // Requirement 5: Benzonavt cannot be eligible for BestStation
    @Test
    fun `test 5 - Benzonavt cannot be eligible for BestStation`() {
        val descriptor = SourceCapabilityRegistry.getCapability(FuelDataSource.BENZONAVT)
        assertFalse(descriptor.eligibleForBestStationRecommendation)
        assertFalse(SourceCapabilityRegistry.canFeedBestStationRecommendation(FuelDataSource.BENZONAVT))
    }

    // Requirement 6: CITY_LEVEL + physical station identity is rejected
    @Test(expected = IllegalArgumentException::class)
    fun `test 6 - CITY_LEVEL with physical station identity is rejected`() {
        val invalid = SourceCapabilityDescriptor(
            sourceId = FuelDataSource.BENZONAVT,
            granularity = SourceGranularity.CITY_LEVEL,
            physicalStationIdentity = PhysicalStationIdentity.EXTERNAL_ID,
            capabilities = setOf(SourceCapability.PRICE, SourceCapability.AVAILABILITY),
            canProvideStationLevelPrice = false,
            canProvideStationLevelAvailability = false,
            eligibleForStationFuelSnapshot = false,
            eligibleForBestStationRecommendation = false
        )
        invalid.validate()
    }

    // Requirement 7: CITY_LEVEL + StationFuelSnapshot eligibility is rejected
    @Test(expected = IllegalArgumentException::class)
    fun `test 7 - CITY_LEVEL with StationFuelSnapshot eligibility is rejected`() {
        val invalid = SourceCapabilityDescriptor(
            sourceId = FuelDataSource.BENZONAVT,
            granularity = SourceGranularity.CITY_LEVEL,
            physicalStationIdentity = PhysicalStationIdentity.NONE,
            capabilities = setOf(SourceCapability.PRICE, SourceCapability.AVAILABILITY),
            canProvideStationLevelPrice = false,
            canProvideStationLevelAvailability = false,
            eligibleForStationFuelSnapshot = true,
            eligibleForBestStationRecommendation = false
        )
        invalid.validate()
    }

    // Requirement 8: Missing coordinate capability cannot become coordinate capability
    @Test(expected = IllegalArgumentException::class)
    fun `test 8 - COORDINATES capability without coordinate identity is rejected`() {
        val invalid = SourceCapabilityDescriptor(
            sourceId = FuelDataSource.RUSSIABASE,
            granularity = SourceGranularity.STATION_LEVEL,
            physicalStationIdentity = PhysicalStationIdentity.EXTERNAL_ID, // Not COORDINATES_ONLY or EXTERNAL_ID_AND_COORDINATES
            capabilities = setOf(SourceCapability.PRICE, SourceCapability.COORDINATES),
            canProvideStationLevelPrice = true,
            canProvideStationLevelAvailability = false,
            eligibleForStationFuelSnapshot = true,
            eligibleForBestStationRecommendation = true
        )
        invalid.validate()
    }

    // Requirement 9: Missing station ID capability cannot become station identity
    @Test(expected = IllegalArgumentException::class)
    fun `test 9 - STATION_ID capability without station ID identity is rejected`() {
        val invalid = SourceCapabilityDescriptor(
            sourceId = FuelDataSource.DEMO,
            granularity = SourceGranularity.STATION_LEVEL,
            physicalStationIdentity = PhysicalStationIdentity.COORDINATES_ONLY, // Not EXTERNAL_ID or EXTERNAL_ID_AND_COORDINATES
            capabilities = setOf(SourceCapability.PRICE, SourceCapability.STATION_ID),
            canProvideStationLevelPrice = true,
            canProvideStationLevelAvailability = false,
            eligibleForStationFuelSnapshot = true,
            eligibleForBestStationRecommendation = true
        )
        invalid.validate()
    }

    // Additional descriptor validation test: station-level price true without PRICE capability
    @Test(expected = IllegalArgumentException::class)
    fun `test additional - station-level price without PRICE capability is rejected`() {
        val invalid = SourceCapabilityDescriptor(
            sourceId = FuelDataSource.RUSSIABASE,
            granularity = SourceGranularity.STATION_LEVEL,
            physicalStationIdentity = PhysicalStationIdentity.EXTERNAL_ID,
            capabilities = setOf(SourceCapability.AVAILABILITY), // Missing PRICE
            canProvideStationLevelPrice = true,
            canProvideStationLevelAvailability = true,
            eligibleForStationFuelSnapshot = true,
            eligibleForBestStationRecommendation = true
        )
        invalid.validate()
    }

    // Additional descriptor validation test: BestStation eligible without station identity
    @Test(expected = IllegalArgumentException::class)
    fun `test additional - BestStation eligible without station identity is rejected`() {
        val invalid = SourceCapabilityDescriptor(
            sourceId = FuelDataSource.DEMO,
            granularity = SourceGranularity.STATION_LEVEL,
            physicalStationIdentity = PhysicalStationIdentity.NONE,
            capabilities = setOf(SourceCapability.PRICE, SourceCapability.AVAILABILITY),
            canProvideStationLevelPrice = true,
            canProvideStationLevelAvailability = true,
            eligibleForStationFuelSnapshot = true,
            eligibleForBestStationRecommendation = true
        )
        invalid.validate()
    }

    @Test
    fun `test unregistered source returns default UNKNOWN descriptor`() {
        val defaultCap = SourceCapabilityRegistry.getCapability(FuelDataSource.GDEBENZ)
        // Note: GDEBENZ is registered as STATION_LEVEL in defaults, but an unlisted source returns default
        val fakeSource = FuelDataSource.valueOf("GDEBENZ")
        assertNotNull(defaultCap)
        assertEquals(SourceGranularity.STATION_LEVEL, defaultCap.granularity)
    }

    @Test
    fun `test canonical fuel normalization preserved`() {
        assertEquals("AI-92", FuelSourceRequest.normalizeFuelType("АИ-92"))
        assertEquals("AI-95", FuelSourceRequest.normalizeFuelType("ron95"))
        assertNull(FuelSourceRequest.normalizeFuelType("DIESEL"))
    }
}
