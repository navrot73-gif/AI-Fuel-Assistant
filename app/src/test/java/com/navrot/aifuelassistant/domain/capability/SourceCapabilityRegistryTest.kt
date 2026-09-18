package com.navrot.aifuelassistant.domain.capability

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.domain.ingestion.FuelSourceRequest
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

    // Verified sources audit: RUSSIABASE, OVERPASS, USER_REPORT, DEMO
    @Test
    fun `test verified sources have explicit station-level capability descriptors`() {
        val russiabase = SourceCapabilityRegistry.getCapability(FuelDataSource.RUSSIABASE)
        assertEquals(SourceGranularity.STATION_LEVEL, russiabase.granularity)
        assertEquals(PhysicalStationIdentity.EXTERNAL_ID, russiabase.physicalStationIdentity)
        assertTrue(russiabase.canProvideStationLevelPrice)
        assertTrue(russiabase.canProvideStationLevelAvailability)
        assertTrue(russiabase.eligibleForStationFuelSnapshot)
        assertTrue(russiabase.eligibleForBestStationRecommendation)

        val overpass = SourceCapabilityRegistry.getCapability(FuelDataSource.OVERPASS)
        assertEquals(SourceGranularity.STATION_LEVEL, overpass.granularity)
        assertEquals(PhysicalStationIdentity.EXTERNAL_ID_AND_COORDINATES, overpass.physicalStationIdentity)
        assertTrue(overpass.capabilities.contains(SourceCapability.COORDINATES))

        val userReport = SourceCapabilityRegistry.getCapability(FuelDataSource.USER_REPORT)
        assertEquals(SourceGranularity.STATION_LEVEL, userReport.granularity)
        assertTrue(userReport.eligibleForBestStationRecommendation)

        val demo = SourceCapabilityRegistry.getCapability(FuelDataSource.DEMO)
        assertEquals(SourceGranularity.STATION_LEVEL, demo.granularity)
        assertTrue(demo.eligibleForStationFuelSnapshot)
    }

    // Unverified placeholder sources audit: GDEBENZ, TWO_GIS, YANDEX, T_BANK, OFFICIAL_STATION, TELEGRAM, VK, PHOTO_EVIDENCE
    @Test
    fun `test unverified placeholder sources resolve safely to UNKNOWN descriptor`() {
        val unverifiedSources = listOf(
            FuelDataSource.GDEBENZ,
            FuelDataSource.TWO_GIS,
            FuelDataSource.YANDEX,
            FuelDataSource.T_BANK,
            FuelDataSource.OFFICIAL_STATION,
            FuelDataSource.TELEGRAM,
            FuelDataSource.VK,
            FuelDataSource.PHOTO_EVIDENCE
        )

        for (src in unverifiedSources) {
            val descriptor = SourceCapabilityRegistry.getCapability(src)
            assertEquals("Unverified source $src MUST resolve to UNKNOWN granularity", SourceGranularity.UNKNOWN, descriptor.granularity)
            assertEquals("Unverified source $src MUST resolve to NONE physical identity", PhysicalStationIdentity.NONE, descriptor.physicalStationIdentity)
            assertTrue("Unverified source $src MUST have empty capabilities", descriptor.capabilities.isEmpty())
            assertFalse("Unverified source $src MUST NOT provide station-level price", descriptor.canProvideStationLevelPrice)
            assertFalse("Unverified source $src MUST NOT provide station-level availability", descriptor.canProvideStationLevelAvailability)
            assertFalse("Unverified source $src MUST NOT provide station-level queue", descriptor.canProvideStationLevelQueue)
            assertFalse("Unverified source $src MUST NOT be eligible for StationFuelSnapshot", descriptor.eligibleForStationFuelSnapshot)
            assertFalse("Unverified source $src MUST NOT be eligible for BestStation", descriptor.eligibleForBestStationRecommendation)
        }
    }

    // Requirement 11 (K3.1): Unknown/unregistered source resolves safely to UNKNOWN descriptor
    @Test
    fun `test unregistered source returns default UNKNOWN descriptor`() {
        val unverified = FuelDataSource.GDEBENZ
        val defaultCap = SourceCapabilityRegistry.getCapability(unverified)

        assertNotNull(defaultCap)
        assertEquals(SourceGranularity.UNKNOWN, defaultCap.granularity)
        assertEquals(PhysicalStationIdentity.NONE, defaultCap.physicalStationIdentity)
        assertTrue(defaultCap.capabilities.isEmpty())
        assertFalse(defaultCap.canProvideStationLevelPrice)
        assertFalse(defaultCap.canProvideStationLevelAvailability)
        assertFalse(defaultCap.eligibleForStationFuelSnapshot)
        assertFalse(defaultCap.eligibleForBestStationRecommendation)
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
    fun `test canonical fuel normalization preserved`() {
        assertEquals("AI-92", FuelSourceRequest.normalizeFuelType("АИ-92"))
        assertEquals("AI-95", FuelSourceRequest.normalizeFuelType("ron95"))
        assertNull(FuelSourceRequest.normalizeFuelType("DIESEL"))
    }
}
