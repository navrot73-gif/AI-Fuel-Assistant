package com.navrot.aifuelassistant.domain.ingestion

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelSourceContractTest {

    @Test
    fun testIngestionObservationDefaultsAndCanonicalFuelType() {
        val now = System.currentTimeMillis()
        val observation = IngestionObservation(
            sourceId = FuelDataSource.BENZONAVT,
            externalStationId = "ext_101",
            fuelType = "АИ-95"
        )

        assertEquals(FuelDataSource.BENZONAVT, observation.sourceId)
        assertEquals("ext_101", observation.externalStationId)
        assertEquals("АИ-95", observation.fuelType)
        assertEquals("AI-95", observation.canonicalFuelType)
        assertEquals(FuelAvailabilityStatus.UNKNOWN, observation.availability)
        assertNull(observation.price)
        assertNull(observation.observedAt)
        assertTrue(observation.receivedAt >= now)
        assertNull(observation.rawReference)
    }

    @Test
    fun testDownstreamFuelSourceObservationMapping() {
        val rawObservations = listOf(
            IngestionObservation(sourceId = FuelDataSource.BENZONAVT, externalStationId = "ext_1", fuelType = "АИ-95"),
            IngestionObservation(sourceId = FuelDataSource.BENZONAVT, externalStationId = "ext_2", fuelType = "ron95"),
            IngestionObservation(sourceId = FuelDataSource.BENZONAVT, externalStationId = "ext_3", fuelType = "AI95"),
            IngestionObservation(sourceId = FuelDataSource.BENZONAVT, externalStationId = "ext_4", fuelType = "92")
        )

        val downstream1 = rawObservations[0].toFuelSourceObservation(stationId = 101)
        val downstream2 = rawObservations[1].toFuelSourceObservation(stationId = 102)
        val downstream3 = rawObservations[2].toFuelSourceObservation(stationId = 103)
        val downstream4 = rawObservations[3].toFuelSourceObservation(stationId = 104)

        assertEquals("AI-95", downstream1.fuelType)
        assertNotEquals("АИ-95", downstream1.fuelType)

        assertEquals("AI-95", downstream2.fuelType)
        assertNotEquals("ron95", downstream2.fuelType)

        assertEquals("AI-95", downstream3.fuelType)
        assertNotEquals("AI95", downstream3.fuelType)

        assertEquals("AI-92", downstream4.fuelType)
        assertNotEquals("92", downstream4.fuelType)

        assertEquals(FuelAvailabilityStatus.UNKNOWN, downstream1.availability)
        assertNull(downstream1.price)
    }

    @Test
    fun testFuelSourceRequestDefaultsAndNormalization() {
        val req = FuelSourceRequest()
        assertEquals(listOf("AI-92", "AI-95"), req.fuelTypes)
        assertEquals(listOf("AI-92", "AI-95"), req.normalizedFuelTypes)
        assertEquals(5000L, req.timeoutMs)
        assertNull(req.targetCity)
        assertNull(req.bbox)

        val reqAliases = FuelSourceRequest(fuelTypes = listOf("АИ-92", "ron95"))
        assertEquals(listOf("AI-92", "AI-95"), reqAliases.normalizedFuelTypes)

        assertEquals("AI-92", FuelSourceRequest.normalizeFuelType("АИ-92"))
        assertEquals("AI-95", FuelSourceRequest.normalizeFuelType("ron95"))
        assertNull(FuelSourceRequest.normalizeFuelType("DIESEL"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFuelSourceRequestUnsupportedFuelTypeDiesel() {
        FuelSourceRequest(fuelTypes = listOf("DIESEL"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFuelSourceRequestUnsupportedFuelTypeAI98() {
        FuelSourceRequest(fuelTypes = listOf("AI-98"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFuelSourceRequestInvalidTimeout() {
        FuelSourceRequest(timeoutMs = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFuelSourceRequestEmptyFuelTypes() {
        FuelSourceRequest(fuelTypes = emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFuelSourceRequestBlankFuelType() {
        FuelSourceRequest(fuelTypes = listOf("  "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFuelSourceRequestInvalidBoundingBoxLat() {
        FuelSourceRequest.BoundingBox(minLat = -95.0, maxLat = 55.0, minLon = 60.0, maxLon = 61.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFuelSourceRequestInvalidBoundingBoxOrder() {
        FuelSourceRequest.BoundingBox(minLat = 55.0, maxLat = 50.0, minLon = 60.0, maxLon = 61.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testIngestionObservationBlankExternalId() {
        IngestionObservation(
            sourceId = FuelDataSource.BENZONAVT,
            externalStationId = "   ",
            fuelType = "AI-95"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testIngestionObservationUnsupportedFuelTypeDiesel() {
        IngestionObservation(
            sourceId = FuelDataSource.BENZONAVT,
            externalStationId = "ext_101",
            fuelType = "DIESEL"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testIngestionObservationUnsupportedFuelTypeAI98() {
        IngestionObservation(
            sourceId = FuelDataSource.BENZONAVT,
            externalStationId = "ext_101",
            fuelType = "AI-98"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testIngestionObservationNegativePrice() {
        IngestionObservation(
            sourceId = FuelDataSource.BENZONAVT,
            externalStationId = "ext_101",
            fuelType = "AI-95",
            price = -10.0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testIngestionObservationInvalidCoordinates() {
        IngestionObservation(
            sourceId = FuelDataSource.BENZONAVT,
            externalStationId = "ext_101",
            fuelType = "AI-95",
            latitude = 100.0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testIngestionObservationUnsafeRawReferenceLength() {
        val hugeReference = "ref_".repeated(200) // 800 chars > 512 max limit
        IngestionObservation(
            sourceId = FuelDataSource.BENZONAVT,
            externalStationId = "ext_101",
            fuelType = "AI-95",
            rawReference = hugeReference
        )
    }

    @Test
    fun testTimestampSemanticsIsolation() {
        val observedAt = 1600000000000L
        val receivedAt = System.currentTimeMillis()
        val obs = IngestionObservation(
            sourceId = FuelDataSource.BENZONAVT,
            externalStationId = "ext_101",
            fuelType = "AI-95",
            observedAt = observedAt,
            receivedAt = receivedAt
        )
        assertEquals(observedAt, obs.observedAt)
        assertEquals(receivedAt, obs.receivedAt)
        assertTrue(obs.observedAt != obs.receivedAt)
    }

    @Test
    fun testFuelSourceResultAndMetricsInvariants() {
        val metrics = SourceIngestionMetrics(
            recordsReceived = 10,
            recordsParsed = 8,
            stationsMatched = 7,
            stationsUnmatched = 1,
            invalidRecords = 2
        )

        val result = FuelSourceResult(
            sourceId = FuelDataSource.BENZONAVT,
            status = FuelSourceStatus.HEALTHY,
            metrics = metrics
        )

        assertEquals(FuelDataSource.BENZONAVT, result.sourceId)
        assertEquals(FuelSourceStatus.HEALTHY, result.status)
        assertEquals(10, result.metrics.recordsReceived)
        assertEquals(8, result.metrics.recordsParsed)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testSourceIngestionMetricsInvalidParsedCount() {
        SourceIngestionMetrics(
            recordsReceived = 5,
            recordsParsed = 10
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFuelSourceResultFailedWithoutErrorMessage() {
        FuelSourceResult(
            sourceId = FuelDataSource.BENZONAVT,
            status = FuelSourceStatus.FAILED,
            errorMessage = "   "
        )
    }

    @Test
    fun testUnknownSafetyAssertions() {
        val obs = IngestionObservation(
            sourceId = FuelDataSource.DEMO,
            externalStationId = "ext_demo",
            fuelType = "AI-95"
        )
        assertEquals(FuelAvailabilityStatus.UNKNOWN, obs.availability)
        assertNull(obs.price)
    }

    private companion object {
        fun String.repeated(n: Int): String = buildString {
            repeat(n) { append(this@repeated) }
        }
    }
}
