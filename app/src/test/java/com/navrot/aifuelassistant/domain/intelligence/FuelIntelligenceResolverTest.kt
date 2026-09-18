package com.navrot.aifuelassistant.domain.intelligence

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.domain.recommendation.RecommendationConfidence
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelIntelligenceResolverTest {

    private val now = 1_700_000_000_000L // Fixed reference timestamp

    // 1. No observations -> UNKNOWN
    @Test
    fun `test 1 - no observations yields UNKNOWN`() {
        val snapshot = FuelIntelligenceResolver.resolve(
            observations = emptyList(),
            stationId = 1,
            fuelType = "AI-95",
            now = now
        )

        assertEquals(FuelAvailabilityStatus.UNKNOWN, snapshot.availability)
        assertEquals(FuelFreshness.UNKNOWN, snapshot.freshness)
        assertEquals(RecommendationConfidence.UNKNOWN, snapshot.confidence)
        assertNull(snapshot.price)
        assertEquals(0, snapshot.sourceCount)
        assertFalse(snapshot.isConflict)
    }

    // 2. One fresh AVAILABLE observation -> AVAILABLE
    @Test
    fun `test 2 - one fresh AVAILABLE observation yields AVAILABLE`() {
        val obs = FuelSourceObservation(
            stationId = 1,
            fuelType = "AI-95",
            availability = FuelAvailabilityStatus.AVAILABLE,
            price = 55.0,
            observedAt = now - 5 * 60 * 1000L, // 5 mins ago
            source = FuelDataSource.USER_REPORT
        )

        val snapshot = FuelIntelligenceResolver.resolve(listOf(obs), 1, "AI-95", now)

        assertEquals(FuelAvailabilityStatus.AVAILABLE, snapshot.availability)
        assertEquals(FuelFreshness.VERY_FRESH, snapshot.freshness)
        assertEquals(RecommendationConfidence.HIGH, snapshot.confidence)
        assertEquals(55.0, snapshot.price!!, 0.001)
        assertFalse(snapshot.isConflict)
    }

    // 3. One fresh UNAVAILABLE observation -> UNAVAILABLE
    @Test
    fun `test 3 - one fresh UNAVAILABLE observation yields UNAVAILABLE`() {
        val obs = FuelSourceObservation(
            stationId = 1,
            fuelType = "AI-95",
            availability = FuelAvailabilityStatus.UNAVAILABLE,
            price = 55.0,
            observedAt = now - 10 * 60 * 1000L, // 10 mins ago
            source = FuelDataSource.RUSSIABASE
        )

        val snapshot = FuelIntelligenceResolver.resolve(listOf(obs), 1, "AI-95", now)

        assertEquals(FuelAvailabilityStatus.UNAVAILABLE, snapshot.availability)
        assertEquals(FuelFreshness.VERY_FRESH, snapshot.freshness)
        assertEquals(RecommendationConfidence.HIGH, snapshot.confidence)
        assertFalse(snapshot.isConflict)
    }

    // 4. One stale AVAILABLE observation -> AVAILABLE + STALE
    @Test
    fun `test 4 - one stale AVAILABLE observation yields AVAILABLE and STALE`() {
        val obs = FuelSourceObservation(
            stationId = 1,
            fuelType = "AI-95",
            availability = FuelAvailabilityStatus.AVAILABLE,
            price = 52.0,
            observedAt = now - 7 * 60 * 60 * 1000L, // 7 hours ago (> 6h)
            source = FuelDataSource.USER_REPORT
        )

        val snapshot = FuelIntelligenceResolver.resolve(listOf(obs), 1, "AI-95", now)

        assertEquals(FuelAvailabilityStatus.AVAILABLE, snapshot.availability)
        assertEquals(FuelFreshness.STALE, snapshot.freshness)
        assertEquals(RecommendationConfidence.LOW, snapshot.confidence)
        assertFalse(snapshot.isConflict)
    }

    // 5. One stale UNAVAILABLE observation -> UNAVAILABLE + STALE
    @Test
    fun `test 5 - one stale UNAVAILABLE observation yields UNAVAILABLE and STALE`() {
        val obs = FuelSourceObservation(
            stationId = 1,
            fuelType = "AI-95",
            availability = FuelAvailabilityStatus.UNAVAILABLE,
            price = null,
            observedAt = now - 10 * 60 * 60 * 1000L, // 10 hours ago (> 6h)
            source = FuelDataSource.RUSSIABASE
        )

        val snapshot = FuelIntelligenceResolver.resolve(listOf(obs), 1, "AI-95", now)

        assertEquals(FuelAvailabilityStatus.UNAVAILABLE, snapshot.availability)
        assertEquals(FuelFreshness.STALE, snapshot.freshness)
        assertEquals(RecommendationConfidence.LOW, snapshot.confidence)
        assertFalse(snapshot.isConflict)
    }

    // 6. Two fresh agreeing AVAILABLE sources -> HIGH confidence
    @Test
    fun `test 6 - two fresh agreeing AVAILABLE sources yields HIGH confidence`() {
        val obs1 = FuelSourceObservation(
            stationId = 1, fuelType = "AI-95", availability = FuelAvailabilityStatus.AVAILABLE,
            price = 55.0, observedAt = now - 5 * 60 * 1000L, source = FuelDataSource.RUSSIABASE
        )
        val obs2 = FuelSourceObservation(
            stationId = 1, fuelType = "AI-95", availability = FuelAvailabilityStatus.AVAILABLE,
            price = 55.0, observedAt = now - 10 * 60 * 1000L, source = FuelDataSource.USER_REPORT
        )

        val snapshot = FuelIntelligenceResolver.resolve(listOf(obs1, obs2), 1, "AI-95", now)

        assertEquals(FuelAvailabilityStatus.AVAILABLE, snapshot.availability)
        assertEquals(RecommendationConfidence.HIGH, snapshot.confidence)
        assertEquals(2, snapshot.sourceCount)
        assertEquals(2, snapshot.confirmingSourceCount)
        assertEquals(0, snapshot.conflictingSourceCount)
        assertFalse(snapshot.isConflict)
    }

    // 7. Two fresh agreeing UNAVAILABLE sources -> HIGH confidence
    @Test
    fun `test 7 - two fresh agreeing UNAVAILABLE sources yields HIGH confidence`() {
        val obs1 = FuelSourceObservation(
            stationId = 1, fuelType = "AI-95", availability = FuelAvailabilityStatus.UNAVAILABLE,
            price = null, observedAt = now - 5 * 60 * 1000L, source = FuelDataSource.RUSSIABASE
        )
        val obs2 = FuelSourceObservation(
            stationId = 1, fuelType = "AI-95", availability = FuelAvailabilityStatus.UNAVAILABLE,
            price = null, observedAt = now - 10 * 60 * 1000L, source = FuelDataSource.USER_REPORT
        )

        val snapshot = FuelIntelligenceResolver.resolve(listOf(obs1, obs2), 1, "AI-95", now)

        assertEquals(FuelAvailabilityStatus.UNAVAILABLE, snapshot.availability)
        assertEquals(RecommendationConfidence.HIGH, snapshot.confidence)
        assertEquals(2, snapshot.confirmingSourceCount)
        assertFalse(snapshot.isConflict)
    }

    // 8. Fresh conflicting sources -> conflict=true, reduced confidence
    @Test
    fun `test 8 - fresh conflicting sources yields conflict true and reduced confidence`() {
        val obs1 = FuelSourceObservation(
            stationId = 1, fuelType = "AI-95", availability = FuelAvailabilityStatus.AVAILABLE,
            price = 55.0, observedAt = now - 10 * 60 * 1000L, source = FuelDataSource.USER_REPORT
        )
        val obs2 = FuelSourceObservation(
            stationId = 1, fuelType = "AI-95", availability = FuelAvailabilityStatus.UNAVAILABLE,
            price = 55.0, observedAt = now - 8 * 60 * 1000L, source = FuelDataSource.RUSSIABASE
        )

        val snapshot = FuelIntelligenceResolver.resolve(listOf(obs1, obs2), 1, "AI-95", now)

        assertTrue(snapshot.isConflict)
        assertEquals("Источники расходятся", snapshot.conflictReason)
        assertTrue(snapshot.confidence == RecommendationConfidence.MEDIUM || snapshot.confidence == RecommendationConfidence.LOW)
        assertEquals(2, snapshot.sourceCount)
    }

    // 9. Fresh trusted source vs stale weak source -> deterministic winner
    @Test
    fun `test 9 - fresh trusted source vs stale weak source yields fresh trusted winner`() {
        val freshTrusted = FuelSourceObservation(
            stationId = 1, fuelType = "AI-95", availability = FuelAvailabilityStatus.AVAILABLE,
            price = 55.0, observedAt = now - 5 * 60 * 1000L, source = FuelDataSource.USER_REPORT
        )
        val staleWeak = FuelSourceObservation(
            stationId = 1, fuelType = "AI-95", availability = FuelAvailabilityStatus.UNAVAILABLE,
            price = 50.0, observedAt = now - 10 * 60 * 60 * 1000L, source = FuelDataSource.OVERPASS
        )

        val snapshot = FuelIntelligenceResolver.resolve(listOf(staleWeak, freshTrusted), 1, "AI-95", now)

        assertEquals(FuelAvailabilityStatus.AVAILABLE, snapshot.availability)
        assertEquals(55.0, snapshot.price!!, 0.001)
    }

    // 10. Missing timestamp -> freshness UNKNOWN
    @Test
    fun `test 10 - missing timestamp yields freshness UNKNOWN`() {
        val obs = FuelSourceObservation(
            stationId = 1, fuelType = "AI-95", availability = FuelAvailabilityStatus.AVAILABLE,
            price = 55.0, observedAt = null, source = FuelDataSource.USER_REPORT
        )

        val snapshot = FuelIntelligenceResolver.resolve(listOf(obs), 1, "AI-95", now)

        assertEquals(FuelFreshness.UNKNOWN, snapshot.freshness)
    }

    // 11. Missing price -> price=null
    @Test
    fun `test 11 - missing price yields price null`() {
        val obs = FuelSourceObservation(
            stationId = 1, fuelType = "AI-95", availability = FuelAvailabilityStatus.AVAILABLE,
            price = null, observedAt = now - 5 * 60 * 1000L, source = FuelDataSource.USER_REPORT
        )

        val snapshot = FuelIntelligenceResolver.resolve(listOf(obs), 1, "AI-95", now)

        assertNull(snapshot.price)
    }

    // 12. AI-92 and AI-95 handled independently
    @Test
    fun `test 12 - AI-92 and AI-95 handled independently`() {
        val obs92 = FuelSourceObservation(
            stationId = 1, fuelType = "AI-92", availability = FuelAvailabilityStatus.AVAILABLE,
            price = 50.0, observedAt = now - 5 * 60 * 1000L, source = FuelDataSource.USER_REPORT
        )
        val obs95 = FuelSourceObservation(
            stationId = 1, fuelType = "AI-95", availability = FuelAvailabilityStatus.UNAVAILABLE,
            price = 55.0, observedAt = now - 5 * 60 * 1000L, source = FuelDataSource.RUSSIABASE
        )

        val list = listOf(obs92, obs95)

        val snapshot92 = FuelIntelligenceResolver.resolve(list, 1, "AI-92", now)
        val snapshot95 = FuelIntelligenceResolver.resolve(list, 1, "AI-95", now)

        assertEquals(FuelAvailabilityStatus.AVAILABLE, snapshot92.availability)
        assertEquals(50.0, snapshot92.price!!, 0.001)

        assertEquals(FuelAvailabilityStatus.UNAVAILABLE, snapshot95.availability)
        assertEquals(55.0, snapshot95.price!!, 0.001)
    }

    // 13. Unknown source does not receive maximum confidence
    @Test
    fun `test 13 - unknown source does not receive maximum confidence`() {
        val obs = FuelSourceObservation(
            stationId = 1, fuelType = "AI-95", availability = FuelAvailabilityStatus.AVAILABLE,
            price = 55.0, observedAt = now - 5 * 60 * 1000L, source = FuelDataSource.DEMO
        )

        val snapshot = FuelIntelligenceResolver.resolve(listOf(obs), 1, "AI-95", now)

        assertTrue("Confidence must not be HIGH for DEMO/unknown source", snapshot.confidence != RecommendationConfidence.HIGH)
    }

    // 14. Deterministic output for same input
    @Test
    fun `test 14 - deterministic output for same input`() {
        val obs1 = FuelSourceObservation(
            stationId = 42, fuelType = "AI-95", availability = FuelAvailabilityStatus.AVAILABLE,
            price = 55.0, observedAt = now - 15 * 60 * 1000L, source = FuelDataSource.USER_REPORT
        )
        val obs2 = FuelSourceObservation(
            stationId = 42, fuelType = "AI-95", availability = FuelAvailabilityStatus.UNAVAILABLE,
            price = 54.5, observedAt = now - 12 * 60 * 1000L, source = FuelDataSource.RUSSIABASE
        )

        val res1 = FuelIntelligenceResolver.resolve(listOf(obs1, obs2), 42, "AI-95", now)
        val res2 = FuelIntelligenceResolver.resolve(listOf(obs1, obs2), 42, "AI-95", now)

        assertEquals(res1, res2)
    }

    // 15. Station ID remains unchanged
    @Test
    fun `test 15 - station ID remains unchanged`() {
        val obs = FuelSourceObservation(
            stationId = -201, fuelType = "AI-95", availability = FuelAvailabilityStatus.AVAILABLE,
            price = 55.0, observedAt = now, source = FuelDataSource.USER_REPORT
        )

        val snapshot = FuelIntelligenceResolver.resolve(listOf(obs), -201, "AI-95", now)

        assertEquals(-201, snapshot.stationId)
    }
}
