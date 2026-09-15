package com.navrot.aifuelassistant.domain.recommendation

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StationScoringTest {

    private val now = System.currentTimeMillis()

    private fun createStation(
        id: Int,
        price: Double = 60.0,
        queueTime: Int = 0,
        reliability: Int = 100,
        updatedAt: Long = now,
        available: Boolean = true,
        dataSources: Set<FuelDataSource> = setOf(FuelDataSource.BENZONAVT)
    ): GasStation {
        return GasStation(
            id = id,
            name = "Station $id",
            brand = "Brand $id",
            address = "Address $id",
            latitude = 55.1608 + (id * 0.001),
            longitude = 61.3989 + (id * 0.001),
            fuelTypes = listOf(
                FuelPrice(
                    type = "АИ-95",
                    price = price,
                    available = available,
                    updatedAt = updatedAt,
                    source = FuelDataSource.BENZONAVT
                )
            ),
            queueTime = queueTime,
            reliability = reliability,
            dataSources = dataSources,
            updatedAt = updatedAt
        )
    }

    @Test
    fun `score increases with higher fuel price`() {
        val stCheap = createStation(1, price = 50.0)
        val stExpensive = createStation(2, price = 70.0)

        val scoreCheap = StationScoring.calculateScore(stCheap, "АИ-95", distanceKm = 1.0, currentTimeMs = now)
        val scoreExpensive = StationScoring.calculateScore(stExpensive, "АИ-95", distanceKm = 1.0, currentTimeMs = now)

        assertTrue("Cheaper station must have lower score", scoreCheap < scoreExpensive)
    }

    @Test
    fun `score increases with distance`() {
        val st = createStation(1, price = 60.0)

        val scoreClose = StationScoring.calculateScore(st, "АИ-95", distanceKm = 1.0, currentTimeMs = now)
        val scoreFar = StationScoring.calculateScore(st, "АИ-95", distanceKm = 10.0, currentTimeMs = now)

        assertTrue("Closer station distance must yield lower score", scoreClose < scoreFar)
    }

    @Test
    fun `score increases with queue time`() {
        val stNoQueue = createStation(1, queueTime = 0)
        val stLongQueue = createStation(2, queueTime = 20)

        val scoreNoQueue = StationScoring.calculateScore(stNoQueue, "АИ-95", distanceKm = 1.0, currentTimeMs = now)
        val scoreLongQueue = StationScoring.calculateScore(stLongQueue, "АИ-95", distanceKm = 1.0, currentTimeMs = now)

        assertTrue("Station with long queue must have higher score penalty", scoreNoQueue < scoreLongQueue)
    }

    @Test
    fun `score increases when reliability is lower`() {
        val stReliable = createStation(1, reliability = 100)
        val stUnreliable = createStation(2, reliability = 40)

        val scoreReliable = StationScoring.calculateScore(stReliable, "АИ-95", distanceKm = 1.0, currentTimeMs = now)
        val scoreUnreliable = StationScoring.calculateScore(stUnreliable, "АИ-95", distanceKm = 1.0, currentTimeMs = now)

        assertTrue("Lower reliability must yield higher score penalty", scoreReliable < scoreUnreliable)
    }

    @Test
    fun `score penalizes NO_FUEL heavily`() {
        val stAvailable = createStation(1, available = true)
        val stNoFuel = createStation(2, available = false)

        val scoreAvail = StationScoring.calculateScore(stAvailable, "АИ-95", distanceKm = 1.0, currentTimeMs = now)
        val scoreNoFuel = StationScoring.calculateScore(stNoFuel, "АИ-95", distanceKm = 1.0, currentTimeMs = now)

        assertTrue("NO_FUEL station must receive NO_FUEL penalty", scoreNoFuel >= scoreAvail + 900.0)
    }

    @Test
    fun `freshness lowers confidence for stale data`() {
        val stFresh = createStation(1, updatedAt = now - (5 * 60 * 1000L)) // 5 mins ago
        val stStale = createStation(2, updatedAt = now - (12 * 60 * 60 * 1000L)) // 12 hours ago

        val confFresh = StationScoring.evaluateConfidence(stFresh, "АИ-95", currentTimeMs = now)
        val confStale = StationScoring.evaluateConfidence(stStale, "АИ-95", currentTimeMs = now)

        assertEquals(RecommendationConfidence.HIGH, confFresh)
        assertTrue("Stale data must result in lower confidence", confStale == RecommendationConfidence.LOW || confStale == RecommendationConfidence.UNKNOWN)
    }

    @Test
    fun `total cost is calculated when price and distance are present`() {
        val st = createStation(1, price = 60.0, queueTime = 10)
        val cost = StationScoring.calculateEstimatedTotalCost(
            station = st,
            fuelType = "АИ-95",
            distanceKm = 5.0,
            refillLiters = 30.0,
            consumptionLPer100km = 10.0
        )

        assertNotNull(cost)
        // Fuel cost: 60 * 30 = 1800.
        // Trip cost: (2 * 5) * (10 / 100) * 60 = 1 * 60 = 60.
        // Queue cost: 10 * 0.5 = 5.
        // Reliability cost: 0.
        // Expected total = 1865.0
        assertEquals(1865.0, cost!!, 0.01)
    }

    @Test
    fun `total cost is null when distance or price is missing`() {
        val stNoPrice = createStation(1, price = 0.0)
        val costNoPrice = StationScoring.calculateEstimatedTotalCost(stNoPrice, "АИ-95", distanceKm = 5.0)
        assertNull(costNoPrice)

        val stNormal = createStation(2, price = 60.0)
        val costNoDist = StationScoring.calculateEstimatedTotalCost(stNormal, "АИ-95", distanceKm = null)
        assertNull(costNoDist)
    }
}
