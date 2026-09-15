package com.navrot.aifuelassistant.features.dashboard.components

import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.domain.recommendation.RecommendationConfidence
import com.navrot.aifuelassistant.domain.recommendation.StationRecommendation
import com.navrot.aifuelassistant.domain.recommendation.StationRecommendationReason
import com.navrot.aifuelassistant.features.dashboard.BestStationUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BestStationCardUnitTest {

    @Test
    fun `BestStationUiState holds recommendation, alternatives and loading state correctly`() {
        val station1 = GasStation(
            id = 101,
            name = "Газпромнефть",
            brand = "Газпромнефть",
            address = "Свердловский тракт, 12в",
            latitude = 55.2243443,
            longitude = 61.3747471,
            queueTime = 0,
            reliability = 80,
            fuelTypes = listOf(FuelPrice("АИ-95", 68.20, available = true))
        )
        val station2 = GasStation(
            id = 102,
            name = "Лукойл",
            brand = "Лукойл",
            address = "пр. Ленина, 50",
            latitude = 55.16,
            longitude = 61.40,
            queueTime = 0,
            reliability = 80,
            fuelTypes = listOf(FuelPrice("АИ-95", 68.70, available = true))
        )
        val station3 = GasStation(
            id = 103,
            name = "Башнефть",
            brand = "Башнефть",
            address = "ул. Труда, 20",
            latitude = 55.17,
            longitude = 61.41,
            queueTime = 0,
            reliability = 80,
            fuelTypes = listOf(FuelPrice("АИ-95", 69.10, available = true))
        )

        val rec1 = StationRecommendation(
            station = station1,
            score = 68.20,
            estimatedTotalCost = 1420.0,
            reasons = listOf(
                StationRecommendationReason.FUEL_AVAILABLE,
                StationRecommendationReason.LOW_PRICE,
                StationRecommendationReason.SHORT_DISTANCE
            ),
            confidence = RecommendationConfidence.HIGH
        )

        val rec2 = StationRecommendation(
            station = station2,
            score = 68.70,
            estimatedTotalCost = 1450.0,
            reasons = listOf(StationRecommendationReason.FUEL_AVAILABLE),
            confidence = RecommendationConfidence.MEDIUM
        )

        val rec3 = StationRecommendation(
            station = station3,
            score = 69.10,
            estimatedTotalCost = 1490.0,
            reasons = listOf(StationRecommendationReason.FUEL_AVAILABLE),
            confidence = RecommendationConfidence.MEDIUM
        )

        val alternatives = listOf(rec2, rec3)

        val uiState = BestStationUiState(
            isLoading = false,
            recommendation = rec1,
            alternatives = alternatives,
            error = null
        )

        assertNotNull(uiState.recommendation)
        assertEquals(101, uiState.recommendation?.station?.id)
        assertEquals("Газпромнефть", uiState.recommendation?.station?.name)
        assertEquals(2, uiState.alternatives.size)
        assertEquals(102, uiState.alternatives[0].station.id)
        assertEquals(103, uiState.alternatives[1].station.id)
        assertNull(uiState.error)
    }

    @Test
    fun `BestStationUiState empty and error states`() {
        val emptyState = BestStationUiState(
            isLoading = false,
            recommendation = null,
            alternatives = emptyList(),
            error = null
        )
        assertNull(emptyState.recommendation)
        assertTrue(emptyState.alternatives.isEmpty())

        val errorState = BestStationUiState(
            isLoading = false,
            recommendation = null,
            alternatives = emptyList(),
            error = "Не удалось обновить рекомендации"
        )
        assertEquals("Не удалось обновить рекомендации", errorState.error)
    }
}
