package com.navrot.aifuelassistant.domain.capability

import com.navrot.aifuelassistant.data.model.FuelDataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceCapabilityRegistryTest {

    @Test
    fun testBenzonavtCapabilityDescriptor() {
        val descriptor = SourceCapabilityRegistry.getDescriptor(FuelDataSource.BENZONAVT)

        assertEquals(FuelDataSource.BENZONAVT, descriptor.sourceId)
        assertEquals(SourceGranularity.CITY_LEVEL, descriptor.granularity)
        assertEquals(PhysicalStationIdentity.NONE, descriptor.physicalStationIdentity)
        assertFalse(descriptor.providesStationLevelPrice)
        assertFalse(descriptor.providesStationLevelAvailability)
        assertFalse(descriptor.providesStationLevelQueue)
        assertFalse(descriptor.isEligibleForStationFuelSnapshot)
        assertFalse(descriptor.isEligibleForBestStation)

        assertFalse(SourceCapabilityRegistry.canFeedStationFuelSnapshot(FuelDataSource.BENZONAVT))
        assertFalse(SourceCapabilityRegistry.canFeedBestStation(FuelDataSource.BENZONAVT))
    }

    @Test
    fun testRussiabaseCapabilityDescriptor() {
        val descriptor = SourceCapabilityRegistry.getDescriptor(FuelDataSource.RUSSIABASE)

        assertEquals(FuelDataSource.RUSSIABASE, descriptor.sourceId)
        assertEquals(SourceGranularity.STATION_LEVEL, descriptor.granularity)
        assertEquals(PhysicalStationIdentity.EXTERNAL_ID, descriptor.physicalStationIdentity)
        assertTrue(descriptor.providesStationLevelPrice)
        assertTrue(descriptor.providesStationLevelAvailability)
        assertFalse(descriptor.providesStationLevelQueue)
        assertFalse("Russiabase does not provide raw coordinates", descriptor.capabilities.contains(SourceCapability.COORDINATES))
        assertTrue(descriptor.isEligibleForStationFuelSnapshot)
        assertTrue(descriptor.isEligibleForBestStation)

        assertTrue(SourceCapabilityRegistry.canFeedStationFuelSnapshot(FuelDataSource.RUSSIABASE))
        assertTrue(SourceCapabilityRegistry.canFeedBestStation(FuelDataSource.RUSSIABASE))
    }

    @Test
    fun testUnverifiedSourcesDefaultToConservativeIneligible() {
        val descriptor = SourceCapabilityRegistry.getDescriptor(FuelDataSource.GDEBENZ)

        assertEquals(FuelDataSource.GDEBENZ, descriptor.sourceId)
        assertEquals(SourceGranularity.UNKNOWN, descriptor.granularity)
        assertEquals(PhysicalStationIdentity.NONE, descriptor.physicalStationIdentity)
        assertFalse(descriptor.isEligibleForStationFuelSnapshot)
        assertFalse(descriptor.isEligibleForBestStation)

        assertFalse(SourceCapabilityRegistry.canFeedStationFuelSnapshot(FuelDataSource.GDEBENZ))
        assertFalse(SourceCapabilityRegistry.canFeedBestStation(FuelDataSource.GDEBENZ))
    }
}
