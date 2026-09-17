package com.navrot.aifuelassistant.domain.ingestion

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelSourceContractTest {

    @Test
    fun testIngestionObservationDefaultsAndInvariants() {
        val now = System.currentTimeMillis()
        val observation = IngestionObservation(
            sourceId = FuelDataSource.BENZONAVT,
            externalStationId = "ext_101",
            fuelType = "AI-95"
        )

        assertEquals(FuelDataSource.BENZONAVT, observation.sourceId)
        assertEquals("ext_101", observation.externalStationId)
        assertEquals("AI-95", observation.fuelType)
        assertEquals(FuelAvailabilityStatus.UNKNOWN, observation.availability)
        assertNull(observation.price)
        assertNull(observation.observedAt)
        assertTrue(observation.receivedAt >= now)
        assertNull(observation.rawReference)
    }

    @Test
    fun testFuelSourceRequestDefaults() {
        val req = FuelSourceRequest()
        assertEquals(listOf("AI-92", "AI-95"), req.fuelTypes)
        assertEquals(5000L, req.timeoutMs)
        assertNull(req.targetCity)
        assertNull(req.bbox)
    }

    @Test
    fun testFuelSourceResultAndMetrics() {
        val metrics = SourceIngestionMetrics(
            recordsReceived = 10,
            recordsParsed = 10,
            stationsMatched = 8,
            stationsUnmatched = 2,
            invalidRecords = 0
        )

        val result = FuelSourceResult(
            sourceId = FuelDataSource.BENZONAVT,
            status = FuelSourceStatus.HEALTHY,
            metrics = metrics
        )

        assertEquals(FuelDataSource.BENZONAVT, result.sourceId)
        assertEquals(FuelSourceStatus.HEALTHY, result.status)
        assertEquals(10, result.metrics.recordsReceived)
        assertEquals(8, result.metrics.stationsMatched)
        assertEquals(2, result.metrics.stationsUnmatched)
    }
}
