package com.navrot.aifuelassistant.data.aggregator

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.data.model.PhotoEvidence
import org.junit.Assert.*
import org.junit.Test

/**
 * Тесты для [FuelDataAggregator].
 * Проверяем слияние цен, выбор лучшей цены, расчёт confidence.
 */
class FuelDataAggregatorTest {

    private val baseStation = GasStation(
        id = 1, name = "Тест", brand = "Бренд",
        address = "адрес", latitude = 55.0, longitude = 61.0,
        fuelTypes = listOf(
            FuelPrice(type = "АИ-95", price = 65.0, available = true, confidence = 70),
            FuelPrice(type = "АИ-92", price = 60.0, available = true, confidence = 70)
        ),
        queueTime = 5, reliability = 80
    )

    // ==================== mergePrices ====================

    @Test
    fun `mergePrices keeps existing when no incoming`() {
        val existing = listOf(
            FuelPrice(type = "АИ-95", price = 65.0, available = true, confidence = 80)
        )
        val result = FuelDataAggregator.mergePrices(existing, emptyList())
        assertEquals(1, result.size)
        assertEquals(65.0, result[0].price, 0.001)
    }

    @Test
    fun `mergePrices adds new fuel type`() {
        val existing = listOf(
            FuelPrice(type = "АИ-95", price = 65.0, available = true)
        )
        val incoming = listOf(
            FuelPrice(type = "ДТ", price = 62.0, available = true)
        )
        val result = FuelDataAggregator.mergePrices(existing, incoming)
        assertEquals(2, result.size)
        assertTrue(result.any { it.type == "АИ-95" })
        assertTrue(result.any { it.type == "ДТ" })
    }

    @Test
    fun `mergePrices picks best price for same fuel type`() {
        val existing = listOf(
            FuelPrice(type = "АИ-95", price = 65.0, available = true, confidence = 50)
        )
        val incoming = listOf(
            FuelPrice(
                type = "АИ-95", price = 63.0, available = true,
                confidence = 80, source = FuelDataSource.USER_REPORT
            )
        )
        val result = FuelDataAggregator.mergePrices(existing, incoming)
        assertEquals(1, result.size)
        // USER_REPORT (+10) + available (+20) + confidence 80 = 110
        // existing: available (+20) + confidence 50 = 70
        // incoming wins
        assertEquals(63.0, result[0].price, 0.001)
    }

    @Test
    fun `mergePrices is case-insensitive for fuel type`() {
        val existing = listOf(
            FuelPrice(type = "аи-95", price = 65.0, available = true, confidence = 50)
        )
        val incoming = listOf(
            FuelPrice(type = "АИ-95", price = 63.0, available = true, confidence = 80)
        )
        val result = FuelDataAggregator.mergePrices(existing, incoming)
        // Both map to same uppercase key → should have 1 entry
        assertEquals(1, result.size)
    }

    // ==================== enrichStation ====================

    @Test
    fun `enrichStation merges incoming prices`() {
        val incoming = listOf(
            FuelPrice(
                type = "АИ-95", price = 63.0, available = true,
                confidence = 90, source = FuelDataSource.USER_REPORT
            )
        )
        val result = FuelDataAggregator.enrichStation(
            baseStation, incomingPrices = incoming
        )
        val ai95 = result.fuelTypes.find { it.type == "АИ-95" }
        assertNotNull(ai95)
        assertEquals(63.0, ai95!!.price, 0.001)
    }

    @Test
    fun `enrichStation adds data source`() {
        val result = FuelDataAggregator.enrichStation(
            baseStation,
            source = FuelDataSource.TWO_GIS
        )
        assertTrue(result.dataSources.contains(FuelDataSource.TWO_GIS))
    }

    @Test
    fun `enrichStation does not add DEMO source`() {
        val demoPrice = FuelPrice(
            type = "АИ-95", price = 64.0, available = true,
            source = FuelDataSource.DEMO
        )
        val result = FuelDataAggregator.enrichStation(
            baseStation, incomingPrices = listOf(demoPrice)
        )
        assertFalse(result.dataSources.contains(FuelDataSource.DEMO))
    }

    @Test
    fun `enrichStation accumulates photo evidence`() {
        val evidence = listOf(
            PhotoEvidence(
                photoUri = "file:///photo1.jpg",
                capturedAt = 1000L,
                latitude = 55.0, longitude = 61.0,
                ocrConfidence = 85
            )
        )
        val result = FuelDataAggregator.enrichStation(
            baseStation, evidence = evidence
        )
        assertEquals(1, result.photoEvidence.size)
        assertEquals("file:///photo1.jpg", result.photoEvidence[0].photoUri)
    }

    // ==================== confidence calculation ====================

    @Test
    fun `confidence is in range 0-100`() {
        val result = FuelDataAggregator.enrichStation(baseStation)
        assertTrue(result.confidence in 0..100)
    }

    @Test
    fun `confidence with photo evidence is higher`() {
        val withoutEvidence = FuelDataAggregator.enrichStation(baseStation).confidence

        val withEvidence = FuelDataAggregator.enrichStation(
            baseStation,
            evidence = listOf(
                PhotoEvidence(
                    photoUri = "file:///p.jpg", capturedAt = 1000L,
                    latitude = 55.0, longitude = 61.0, ocrConfidence = 90
                )
            )
        ).confidence

        assertTrue(
            "Confidence with evidence ($withEvidence) should be >= without ($withoutEvidence)",
            withEvidence >= withoutEvidence
        )
    }

    @Test
    fun `enrichStation updates updatedAt from incoming prices`() {
        val incoming = listOf(
            FuelPrice(
                type = "АИ-95", price = 63.0, available = true,
                updatedAt = 9999L, source = FuelDataSource.USER_REPORT
            )
        )
        val result = FuelDataAggregator.enrichStation(
            baseStation, incomingPrices = incoming
        )
        assertEquals(9999L, result.updatedAt)
    }
}