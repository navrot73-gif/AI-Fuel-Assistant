package com.navrot.aifuelassistant.domain.reliability

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import org.junit.Assert.assertEquals
import org.junit.Test

class PriceReliabilityCalculatorTest {

    private val nowMs = 1_700_000_000_000L // Фиксированное текущее время
    private val oneDayMs = 24 * 60 * 60 * 1000L

    @Test
    fun `calculate user confirmed price source and age zero penalty`() {
        val station = GasStation(
            id = 1,
            name = "Test",
            brand = "TestBrand",
            address = "TestAddress",
            latitude = 55.0,
            longitude = 37.0,
            fuelTypes = listOf(
                FuelPrice(
                    type = "АИ-95",
                    price = 65.0,
                    available = true,
                    source = FuelDataSource.USER_REPORT,
                    updatedAt = nowMs
                )
            ),
            queueTime = 0,
            reliability = 90
        )

        val reliability = PriceReliabilityCalculator.calculate(station, "АИ-95", currentTimeMs = nowMs)

        assertEquals(PriceSource.USER_CONFIRMED, reliability.source)
        assertEquals(0, reliability.ageDays)
        // Base 95 - 0 penalty = 95
        assertEquals(95, reliability.percent)
    }

    @Test
    fun `calculate network source with source count and photo bonus`() {
        val station = GasStation(
            id = 2,
            name = "Test Net",
            brand = "NetBrand",
            address = "NetAddress",
            latitude = 55.0,
            longitude = 37.0,
            fuelTypes = listOf(
                FuelPrice(
                    type = "АИ-95",
                    price = 65.0,
                    available = true,
                    source = FuelDataSource.BENZONAVT,
                    sourceCount = 3,
                    updatedAt = nowMs - (2 * oneDayMs) // 2 дня давности -> penalty 5%
                )
            ),
            queueTime = 0,
            reliability = 80,
            monumentPhotoUrl = "https://example.com/photo.jpg" // +5% фото бонус
        )

        val reliability = PriceReliabilityCalculator.calculate(station, "АИ-95", currentTimeMs = nowMs)

        assertEquals(PriceSource.NETWORK, reliability.source)
        assertEquals(2, reliability.ageDays)
        // Base 85 - penalty 5 + sourceCount bonus 10 + photo bonus 5 = 95
        assertEquals(95, reliability.percent)
    }

    @Test
    fun `calculate assets source with default age penalty`() {
        val station = GasStation(
            id = 3,
            name = "Assets Station",
            brand = "OfflineBrand",
            address = "OfflineAddress",
            latitude = 55.0,
            longitude = 37.0,
            fuelTypes = listOf(
                FuelPrice(
                    type = "АИ-92",
                    price = 55.0,
                    available = true,
                    source = FuelDataSource.DEMO,
                    updatedAt = 0L
                )
            ),
            queueTime = 5,
            reliability = 70
        )

        val reliability = PriceReliabilityCalculator.calculate(station, "АИ-92", currentTimeMs = nowMs)

        assertEquals(PriceSource.ASSETS, reliability.source)
        assertEquals(30, reliability.ageDays)
        // Base 40 - penalty 30 = 10
        assertEquals(10, reliability.percent)
    }

    @Test
    fun `calculate coerce max 100`() {
        val station = GasStation(
            id = 4,
            name = "Super Verified Station",
            brand = "SuperBrand",
            address = "SuperAddress",
            latitude = 55.0,
            longitude = 37.0,
            fuelTypes = listOf(
                FuelPrice(
                    type = "АИ-95",
                    price = 60.0,
                    available = true,
                    source = FuelDataSource.USER_REPORT,
                    sourceCount = 5, // +10 bonus
                    updatedAt = nowMs
                )
            ),
            queueTime = 0,
            reliability = 100,
            entrancePhotoUrl = "https://example.com/entrance.jpg" // +5 bonus
        )

        val reliability = PriceReliabilityCalculator.calculate(station, "АИ-95", currentTimeMs = nowMs)

        assertEquals(PriceSource.USER_CONFIRMED, reliability.source)
        // Base 95 + 10 + 5 = 110 -> max 100
        assertEquals(100, reliability.percent)
    }
}
