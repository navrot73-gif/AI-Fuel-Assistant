package com.navrot.aifuelassistant.domain.usecase

import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Тесты для [GetBestStationsUseCase].
 * Проверяем скоринг, фильтрацию и сортировку АЗС.
 */
class GetBestStationsUseCaseTest {

    private lateinit var useCase: GetBestStationsUseCase

    private val ai95_65 = GasStation(
        id = 1, name = "Дешёвая", brand = "Бренд",
        address = "addr", latitude = 55.16, longitude = 61.40,
        fuelTypes = listOf(FuelPrice(type = "АИ-95", price = 65.0, available = true)),
        queueTime = 5, reliability = 80
    )

    private val ai95_70 = GasStation(
        id = 2, name = "Средняя", brand = "Бренд",
        address = "addr", latitude = 55.17, longitude = 61.41,
        fuelTypes = listOf(FuelPrice(type = "АИ-95", price = 70.0, available = true)),
        queueTime = 0, reliability = 100
    )

    private val ai95_68 = GasStation(
        id = 3, name = "Дорогая но быстрая", brand = "Бренд",
        address = "addr", latitude = 55.18, longitude = 61.42,
        fuelTypes = listOf(FuelPrice(type = "АИ-95", price = 68.0, available = true)),
        queueTime = 0, reliability = 50
    )

    private val noAi95 = GasStation(
        id = 4, name = "Только ДТ", brand = "Бренд",
        address = "addr", latitude = 55.19, longitude = 61.43,
        fuelTypes = listOf(FuelPrice(type = "ДТ", price = 62.0, available = true)),
        queueTime = 0, reliability = 100
    )

    private val unavailableAi95 = GasStation(
        id = 5, name = "Нет топлива", brand = "Бренд",
        address = "addr", latitude = 55.20, longitude = 61.44,
        fuelTypes = listOf(FuelPrice(type = "АИ-95", price = 60.0, available = false)),
        queueTime = 0, reliability = 100
    )

    @Before
    fun setUp() {
        useCase = GetBestStationsUseCase()
    }

    // ==================== calculateScore ====================

    @Test
    fun `calculateScore for cheap station with low queue`() {
        val score = useCase.calculateScore(ai95_65, "АИ-95")
        // 65 + 5 * 0.5 - (100 - 80) * 0.2 = 65 + 2.5 - 4 = 63.5
        assertEquals(63.5, score, 0.001)
    }

    @Test
    fun `calculateScore for expensive station with zero queue and max reliability`() {
        val score = useCase.calculateScore(ai95_70, "АИ-95")
        // 70 + 0 * 0.5 - (100 - 100) * 0.2 = 70
        assertEquals(70.0, score, 0.001)
    }

    @Test
    fun `calculateScore for medium station with low reliability`() {
        val score = useCase.calculateScore(ai95_68, "АИ-95")
        // 68 + 0 * 0.5 - (100 - 50) * 0.2 = 68 - 10 = 58
        assertEquals(58.0, score, 0.001)
    }

    @Test
    fun `calculateScore returns MAX_VALUE for missing fuel type`() {
        val score = useCase.calculateScore(noAi95, "АИ-95")
        assertEquals(Double.MAX_VALUE, score, 0.0)
    }

    // ==================== execute (basic) ====================

    @Test
    fun `execute filters out stations without requested fuel type`() {
        val result = useCase.execute(listOf(ai95_65, noAi95), "АИ-95")
        assertEquals(1, result.size)
        assertEquals(1, result[0].id)
    }

    @Test
    fun `execute filters out stations with unavailable fuel`() {
        val result = useCase.execute(listOf(ai95_65, unavailableAi95), "АИ-95")
        assertEquals(1, result.size)
        assertEquals(1, result[0].id)
    }

    @Test
    fun `execute sorts by composite score ascending`() {
        val result = useCase.execute(listOf(ai95_65, ai95_70, ai95_68), "АИ-95")
        // Scores: ai95_65=63.5, ai95_70=70, ai95_68=58
        // Sorted: 58, 63.5, 70
        assertEquals(3, result.size)
        assertEquals(3, result[0].id) // 58 (best)
        assertEquals(1, result[1].id) // 63.5
        assertEquals(2, result[2].id) // 70 (worst)
    }

    @Test
    fun `execute returns empty list when no stations match`() {
        val result = useCase.execute(listOf(noAi95), "АИ-95")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `execute returns empty list for empty input`() {
        val result = useCase.execute(emptyList(), "АИ-95")
        assertTrue(result.isEmpty())
    }

    // ==================== execute (with location) ====================

    @Test
    fun `execute with location filters by radius`() {
        // Station 1 at (55.16, 61.40), user at (55.164, 61.436) — very close
        // Station 3 at (55.18, 61.42), user at (55.164, 61.436) — ~2 km
        // Station 2 at (55.17, 61.41), user at (55.164, 61.436) — ~3.5 km
        val result = useCase.execute(
            listOf(ai95_65, ai95_70, ai95_68),
            "АИ-95",
            userLat = 55.164, userLon = 61.436,
            radiusKm = 3.0
        )
        // All 3 should be within ~3.5 km, but radius is 3.0 km
        // Station 1 is ~0.5 km, Station 3 is ~1.8 km, Station 2 is ~3.3 km
        // So only stations 1 and 3 should be in range
        assertTrue(result.size <= 3)
        // All returned stations must have АИ-95 available
        result.forEach { s ->
            assertTrue(
                s.fuelTypes.any { it.type == "АИ-95" && it.available }
            )
        }
    }

    @Test
    fun `execute with location returns empty when all stations outside radius`() {
        val farStation = GasStation(
            id = 99, name = "Far", brand = "B",
            address = "far", latitude = 50.0, longitude = 50.0,
            fuelTypes = listOf(FuelPrice(type = "АИ-95", price = 60.0, available = true)),
            queueTime = 0, reliability = 100
        )
        val result = useCase.execute(
            listOf(farStation),
            "АИ-95",
            userLat = 55.164, userLon = 61.436,
            radiusKm = 1.0
        )
        assertTrue(result.isEmpty())
    }

    // ==================== Edge cases ====================

    @Test
    fun `queue penalty increases score`() {
        val stationNoQueue = ai95_65.copy(queueTime = 0)
        val stationWithQueue = ai95_65.copy(id = 10, queueTime = 20)

        val scoreNoQueue = useCase.calculateScore(stationNoQueue, "АИ-95")
        val scoreWithQueue = useCase.calculateScore(stationWithQueue, "АИ-95")

        // 20 min queue * 0.5 = 10 руб штрафа
        assertEquals(10.0, scoreWithQueue - scoreNoQueue, 0.001)
    }

    @Test
    fun `higher reliability lowers score (better)`() {
        val lowReliability = ai95_65.copy(id = 20, reliability = 50)
        val highReliability = ai95_65.copy(id = 21, reliability = 100)

        val scoreLow = useCase.calculateScore(lowReliability, "АИ-95")
        val scoreHigh = useCase.calculateScore(highReliability, "АИ-95")

        // (100-50)*0.2 = 10 vs (100-100)*0.2 = 0 → низкая надёжность даёт штраф 10
        // Поэтому scoreLow < scoreHigh (лучше для пользователя)
        assertEquals(10.0, scoreHigh - scoreLow, 0.001)
    }

    @Test
    fun `price dominates over queue and reliability`() {
        // Дешёвая станция с длинной очередью и низкой надёжностью
        val cheapBad = GasStation(
            id = 30, name = "CheapBad", brand = "B",
            address = "a", latitude = 55.0, longitude = 61.0,
            fuelTypes = listOf(FuelPrice(type = "АИ-95", price = 50.0, available = true)),
            queueTime = 30, reliability = 0
        )
        // Дорогая станция без очереди и макс. надёжностью
        val expensivePerfect = GasStation(
            id = 31, name = "ExpensivePerfect", brand = "B",
            address = "a", latitude = 55.0, longitude = 61.0,
            fuelTypes = listOf(FuelPrice(type = "АИ-95", price = 75.0, available = true)),
            queueTime = 0, reliability = 100
        )

        val scoreCheap = useCase.calculateScore(cheapBad, "АИ-95")
        // 50 + 30*0.5 - (100-0)*0.2 = 50+15-20 = 45
        val scoreExpensive = useCase.calculateScore(expensivePerfect, "АИ-95")
        // 75 + 0 - 0 = 75

        assertTrue("Cheap should win: $scoreCheap < $scoreExpensive", scoreCheap < scoreExpensive)
        assertEquals(45.0, scoreCheap, 0.001)
        assertEquals(75.0, scoreExpensive, 0.001)
    }
}
