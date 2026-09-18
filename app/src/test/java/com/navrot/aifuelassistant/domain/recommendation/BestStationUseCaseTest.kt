package com.navrot.aifuelassistant.domain.recommendation

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BestStationUseCaseTest {

    private val useCase = BestStationUseCase()
    private val now = System.currentTimeMillis()

    private fun buildStation(
        id: Int,
        name: String = "Station $id",
        brand: String = "Brand $id",
        lat: Double = 55.1608,
        lon: Double = 61.3989,
        price: Double = 60.0,
        fuelType: String = "АИ-95",
        available: Boolean = true,
        queueTime: Int = 0,
        reliability: Int = 100,
        updatedAt: Long = now
    ): GasStation {
        return GasStation(
            id = id,
            name = name,
            brand = brand,
            address = "Address $id",
            latitude = lat,
            longitude = lon,
            fuelTypes = listOf(
                FuelPrice(
                    type = fuelType,
                    price = price,
                    available = available,
                    updatedAt = updatedAt,
                    source = FuelDataSource.USER_REPORT
                )
            ),
            queueTime = queueTime,
            reliability = reliability,
            dataSources = setOf(FuelDataSource.USER_REPORT),
            updatedAt = updatedAt
        )
    }

    @Test
    fun `1 available station beats unavailable station`() {
        val stUnavailable = buildStation(id = 1, price = 50.0, available = false)
        val stAvailable = buildStation(id = 2, price = 65.0, available = true)

        val result = useCase.execute(
            stations = listOf(stUnavailable, stAvailable),
            fuelType = "АИ-95",
            userLat = 55.1608,
            userLon = 61.3989,
            currentTimeMs = now
        )

        assertNotNull(result.best)
        assertEquals(2, result.best!!.station.id)
    }

    @Test
    fun `2 cheaper station can beat closer station`() {
        // Station 1: close (0.1 km) but expensive (75 rub)
        val stCloseExpensive = buildStation(id = 1, lat = 55.1610, lon = 61.3989, price = 75.0)
        // Station 2: slightly further (2.0 km) but cheaper (55 rub)
        val stFarCheaper = buildStation(id = 2, lat = 55.1788, lon = 61.3989, price = 55.0)

        val result = useCase.execute(
            stations = listOf(stCloseExpensive, stFarCheaper),
            fuelType = "АИ-95",
            userLat = 55.1608,
            userLon = 61.3989,
            currentTimeMs = now
        )

        assertNotNull(result.best)
        assertEquals("Cheaper station must beat closer expensive station", 2, result.best!!.station.id)
    }

    @Test
    fun `3 queue affects ranking`() {
        val stQueue = buildStation(id = 1, price = 60.0, queueTime = 30)
        val stNoQueue = buildStation(id = 2, price = 60.0, queueTime = 0)

        val result = useCase.execute(
            stations = listOf(stQueue, stNoQueue),
            fuelType = "АИ-95",
            userLat = 55.1608,
            userLon = 61.3989,
            currentTimeMs = now
        )

        assertNotNull(result.best)
        assertEquals(2, result.best!!.station.id)
    }

    @Test
    fun `4 stale data lowers confidence`() {
        val stFresh = buildStation(id = 1, updatedAt = now - (5 * 60 * 1000L))
        val stStale = buildStation(id = 2, updatedAt = now - (24 * 60 * 60 * 1000L))

        val resultFresh = useCase.execute(listOf(stFresh), "АИ-95", currentTimeMs = now)
        val resultStale = useCase.execute(listOf(stStale), "АИ-95", currentTimeMs = now)

        assertEquals(RecommendationConfidence.HIGH, resultFresh.best!!.confidence)
        assertTrue(resultStale.best!!.confidence == RecommendationConfidence.LOW || resultStale.best!!.confidence == RecommendationConfidence.UNKNOWN)
    }

    @Test
    fun `5 missing queue is not zero queue advantage`() {
        val stKnownZeroQueue = buildStation(id = 1, price = 60.0, queueTime = 0)
        val stKnownQueue = buildStation(id = 2, price = 60.0, queueTime = 5)

        val recZero = StationScoring.evaluateReasons(stKnownZeroQueue, "АИ-95", distanceKm = 1.0, currentTimeMs = now)
        val recKnown = StationScoring.evaluateReasons(stKnownQueue, "АИ-95", distanceKm = 1.0, currentTimeMs = now)

        // SHORT_QUEUE reason is only awarded when queueTime is explicitly > 0 and <= 5
        assertTrue(recKnown.contains(StationRecommendationReason.SHORT_QUEUE))
        assertTrue(!recZero.contains(StationRecommendationReason.SHORT_QUEUE))
    }

    @Test
    fun `6 missing price is not zero price`() {
        val stNoPrice = buildStation(id = 1, price = 0.0)
        val stNormalPrice = buildStation(id = 2, price = 60.0)

        val scoreNoPrice = StationScoring.calculateScore(stNoPrice, "АИ-95", distanceKm = 1.0, currentTimeMs = now)
        val scoreNormalPrice = StationScoring.calculateScore(stNormalPrice, "АИ-95", distanceKm = 1.0, currentTimeMs = now)

        assertTrue("Missing price must not result in 0 score advantage", scoreNoPrice > scoreNormalPrice)
    }

    @Test
    fun `7 missing distance is not zero distance`() {
        val stNoDist = buildStation(id = 1, lat = 0.0, lon = 0.0, price = 60.0)
        val stWithDist = buildStation(id = 2, lat = 55.1610, lon = 61.3989, price = 60.0)

        val result = useCase.execute(
            stations = listOf(stNoDist, stWithDist),
            fuelType = "АИ-95",
            userLat = 55.1608,
            userLon = 61.3989,
            currentTimeMs = now
        )

        assertNotNull(result.best)
        // Station with known short distance should be preferred or tie-broken correctly
        assertEquals(2, result.best!!.station.id)
    }

    @Test
    fun `8 no stations returns empty result`() {
        val result = useCase.execute(
            stations = emptyList(),
            fuelType = "АИ-95"
        )

        assertNull(result.best)
        assertTrue(result.alternatives.isEmpty())
        assertEquals(0, result.evaluatedCount)
    }

    @Test
    fun `9 no suitable fuel returns no best`() {
        val stOtherFuel = buildStation(id = 1, fuelType = "ДТ")

        val result = useCase.execute(
            stations = listOf(stOtherFuel),
            fuelType = "АИ-95"
        )

        assertNull(result.best)
        assertTrue(result.alternatives.isEmpty())
        assertEquals(1, result.evaluatedCount)
    }

    @Test
    fun `10 deterministic tie break`() {
        // Identical parameters for two stations
        val stA = buildStation(id = 10, price = 60.0)
        val stB = buildStation(id = 20, price = 60.0)

        val result1 = useCase.execute(listOf(stB, stA), "АИ-95", currentTimeMs = now)
        val result2 = useCase.execute(listOf(stA, stB), "АИ-95", currentTimeMs = now)

        assertEquals(result1.best!!.station.id, result2.best!!.station.id)
        assertEquals(10, result1.best!!.station.id)
    }

    @Test
    fun `11 stable station ID preserved`() {
        val st = buildStation(id = 42, name = "Original Name")

        val result = useCase.execute(listOf(st), "АИ-95", currentTimeMs = now)

        assertNotNull(result.best)
        assertEquals(42, result.best!!.station.id)
        assertEquals("Original Name", result.best!!.station.name)
    }

    @Test
    fun `12 alternatives sorted correctly`() {
        val stBest = buildStation(id = 1, price = 50.0)
        val stSecond = buildStation(id = 2, price = 60.0)
        val stThird = buildStation(id = 3, price = 70.0)

        val result = useCase.execute(
            stations = listOf(stThird, stBest, stSecond),
            fuelType = "АИ-95",
            currentTimeMs = now
        )

        assertEquals(1, result.best!!.station.id)
        assertEquals(2, result.alternatives.size)
        assertEquals(2, result.alternatives[0].station.id)
        assertEquals(3, result.alternatives[1].station.id)
    }
}
