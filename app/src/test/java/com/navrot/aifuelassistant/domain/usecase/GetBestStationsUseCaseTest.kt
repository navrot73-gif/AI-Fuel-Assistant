package com.navrot.aifuelassistant.domain.usecase

import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import org.junit.Assert.*
import org.junit.Test

/**
 * Тесты скоринга и ранжирования АЗС.
 *
 * Формула: score = fuelPrice + queueTime * 0.5 + (100 - reliability) * 0.2
 * Чем меньше score — тем лучше.
 */
class GetBestStationsUseCaseTest {

    private val useCase = GetBestStationsUseCase()

    // ==================== Helper ====================

    private fun station(
        id: Int = 1,
        price: Double = 45.0,
        queueTime: Int = 5,
        reliability: Int = 80,
        available: Boolean = true
    ): GasStation = GasStation(
        id = id,
        name = "АЗС $id",
        brand = "Бренд$id",
        address = "г. Тест, ул. Тестовая, $id",
        latitude = 55.0 + id * 0.01,
        longitude = 61.0 + id * 0.01,
        fuelTypes = listOf(
            FuelPrice(type = "АИ-95", price = price, available = available)
        ),
        queueTime = queueTime,
        reliability = reliability
    )

    // ==================== calculateScore ====================

    @Test
    fun `score considers price as base`() {
        val s = station(price = 50.0, queueTime = 0, reliability = 100)
        // 50 + 0 * 0.5 + (100 - 100) * 0.2 = 50
        assertEquals(50.0, useCase.calculateScore(s, "АИ-95"), 0.001)
    }

    @Test
    fun `score penalizes queue time`() {
        val s1 = station(price = 45.0, queueTime = 0, reliability = 100)
        val s2 = station(id = 2, price = 45.0, queueTime = 10, reliability = 100)
        // s1: 45 + 0 + 0 = 45
        // s2: 45 + 10 * 0.5 + 0 = 50
        assertTrue(useCase.calculateScore(s1, "АИ-95") < useCase.calculateScore(s2, "АИ-95"))
    }

    @Test
    fun `score penalizes low reliability`() {
        val s1 = station(price = 45.0, queueTime = 0, reliability = 50)
        val s2 = station(id = 2, price = 45.0, queueTime = 0, reliability = 100)
        // s1: 45 + 0 + (100 - 50) * 0.2 = 45 + 10 = 55
        // s2: 45 + 0 + (100 - 100) * 0.2 = 45 + 0 = 45
        // s2 лучше (45 < 55) — надёжность штрафует ненадёжные АЗС
        assertTrue(useCase.calculateScore(s2, "АИ-95") < useCase.calculateScore(s1, "АИ-95"))
    }

    @Test
    fun `score returns MAX_VALUE for unavailable fuel`() {
        val s = station(available = false)
        assertEquals(Double.MAX_VALUE, useCase.calculateScore(s, "АИ-95"), 0.0)
    }

    @Test
    fun `score returns MAX_VALUE for missing fuel type`() {
        val s = GasStation(
            id = 1, name = "", brand = "", address = "",
            latitude = 0.0, longitude = 0.0,
            fuelTypes = listOf(FuelPrice(type = "ДТ", price = 40.0, available = true)),
            queueTime = 0, reliability = 100
        )
        assertEquals(Double.MAX_VALUE, useCase.calculateScore(s, "АИ-95"), 0.0)
    }

    // ==================== execute (simple) ====================

    @Test
    fun `execute filters by fuel type and availability`() {
        val stations = listOf(
            station(id = 1, price = 44.0, available = true),  // АИ-95 available
            station(id = 2, price = 42.0, available = false), // unavailable — excluded
            GasStation(
                id = 3, name = "", brand = "", address = "",
                latitude = 0.0, longitude = 0.0,
                fuelTypes = listOf(FuelPrice(type = "ДТ", price = 40.0, available = true)),
                queueTime = 0, reliability = 100
            )
        )
        val result = useCase.execute(stations, "АИ-95")
        assertEquals(1, result.size)
        assertEquals(1, result[0].id)
    }

    @Test
    fun `execute sorts by score ascending`() {
        val stations = listOf(
            station(id = 1, price = 48.0, queueTime = 0, reliability = 100), // 48 + 0 + 0 = 48
            station(id = 2, price = 45.0, queueTime = 5, reliability = 80),  // 45 + 2.5 + 4 = 51.5
            station(id = 3, price = 50.0, queueTime = 0, reliability = 100)  // 50 + 0 + 0 = 50
        )
        val result = useCase.execute(stations, "АИ-95")
        // Лучший — id=1 (48), затем id=3 (50), затем id=2 (51.5)
        assertEquals(1, result[0].id)
        assertEquals(3, result[1].id)
        assertEquals(2, result[2].id)
    }

    @Test
    fun `execute returns empty list when no matching fuel`() {
        val stations = listOf(
            GasStation(
                id = 1, name = "", brand = "", address = "",
                latitude = 0.0, longitude = 0.0,
                fuelTypes = listOf(FuelPrice(type = "ДТ", price = 40.0, available = true)),
                queueTime = 0, reliability = 100
            )
        )
        val result = useCase.execute(stations, "АИ-95")
        assertTrue(result.isEmpty())
    }

    // ==================== execute (with location) ====================

    @Test
    fun `execute with location filters by radius`() {
        val stations = listOf(
            station(id = 1, price = 40.0), // (55.01, 61.01)
            GasStation(
                id = 2, name = "Far", brand = "", address = "",
                latitude = 50.0, longitude = 50.0, // very far
                fuelTypes = listOf(FuelPrice(type = "АИ-95", price = 30.0, available = true)),
                queueTime = 0, reliability = 100
            )
        )
        // User at (55.0, 61.0), radius 10 km — only station 1 is nearby
        val result = useCase.execute(stations, "АИ-95", 55.0, 61.0, 10.0)
        assertEquals(1, result.size)
        assertEquals(1, result[0].id)
    }

    @Test
    fun `execute with location returns all within radius sorted by score`() {
        val stations = listOf(
            station(id = 1, price = 48.0),
            station(id = 2, price = 44.0),
            station(id = 3, price = 46.0)
        )
        val result = useCase.execute(stations, "АИ-95", 55.0, 61.0, 100.0)
        assertEquals(3, result.size)
        assertEquals(2, result[0].id) // cheapest first
    }
}
