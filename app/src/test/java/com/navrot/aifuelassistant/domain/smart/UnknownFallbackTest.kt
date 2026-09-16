package com.navrot.aifuelassistant.domain.smart

import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import org.junit.Assert.assertNull
import org.junit.Test

class UnknownFallbackTest {

    private val useCase = GetSmartFuelRecommendationUseCase()

    @Test
    fun testTripCostAndTotalCostAreNullWhenConsumptionIsUnknown() {
        val station = GasStation(
            id = 1,
            name = "Test Station",
            brand = "Brand",
            address = "Address",
            latitude = 55.15,
            longitude = 61.40,
            queueTime = 0,
            reliability = 80,
            updatedAt = System.currentTimeMillis(),
            fuelTypes = listOf(
                FuelPrice(type = "АИ-95", price = 55.0, available = true, updatedAt = System.currentTimeMillis())
            )
        )

        val result = useCase.execute(
            stations = listOf(station),
            fuelType = "АИ-95",
            userLat = 55.10,
            userLon = 61.35,
            vehicleId = null // vehicleId null -> consumption prediction null
        )

        val rec = result.topRecommendation!!
        assertNull("Trip cost must be null when consumption prediction is unknown", rec.estimatedTripCost)
        assertNull("Total cost must be null when consumption prediction is unknown", rec.estimatedTotalCost)
    }
}
