package com.navrot.aifuelassistant.data.datasource

import com.navrot.aifuelassistant.data.UserPriceRepository
import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.data.providers.BenzonavtProvider
import com.navrot.aifuelassistant.data.providers.FuelPriceInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class StationPriceApplierTest {

    private val userPrices: UserPriceRepository = mock()
    private val benzonavtProvider: BenzonavtProvider = mock()

    private lateinit var applier: StationPriceApplier

    @Before
    fun setUp() {
        applier = StationPriceApplierImpl(userPrices, benzonavtProvider)
    }

    private fun createBaseStation(id: Int = 1, price: Double = 50.0): GasStation {
        return GasStation(
            id = id,
            name = "Station 1",
            brand = "Brand 1",
            address = "Street 1",
            latitude = 55.0,
            longitude = 60.0,
            fuelTypes = listOf(FuelPrice(type = "AI-95", price = price, available = true, updatedAt = 0L)),
            queueTime = 0,
            reliability = 100
        )
    }

    @Test
    fun `applyUserPrices overrides fuel price when user report exists`() {
        val base = listOf(createBaseStation(id = 1, price = 50.0))
        whenever(userPrices.getAll()).doReturn(mapOf(Pair(1, "AI-95") to 58.0))

        val result = applier.applyUserPrices(base)

        val updatedFuel = result.first().fuelTypes.first()
        assertEquals(58.0, updatedFuel.price, 0.001)
        assertEquals(FuelDataSource.USER_REPORT, updatedFuel.source)
        assertTrue(result.first().dataSources.contains(FuelDataSource.USER_REPORT))
    }

    @Test
    fun `applyBenzonavtToStation updates price when Benzonavt timestamp is newer`() {
        val baseStation = createBaseStation(id = 1, price = 50.0)
        val futureIso = java.time.OffsetDateTime.now().plusHours(1).toString()
        val benzonavt = mapOf(
            "AI-95" to FuelPriceInfo(
                median = 54.5,
                min = 53.0,
                max = 56.0,
                sourceCount = 10,
                updatedAt = futureIso
            )
        )

        val updated = applier.applyBenzonavtToStation(baseStation, benzonavt, "Chelyabinsk")

        val updatedFuel = updated.fuelTypes.first()
        assertEquals(54.5, updatedFuel.price, 0.001)
        assertEquals(FuelDataSource.BENZONAVT, updatedFuel.source)
        assertTrue(updated.dataSources.contains(FuelDataSource.BENZONAVT))
    }

    @Test
    fun `applyAllPrices applies user prices first then Benzonavt`() = runBlocking {
        val base = listOf(createBaseStation(id = 1, price = 50.0))
        whenever(userPrices.getAll()).doReturn(emptyMap())
        whenever(benzonavtProvider.currentCity()).doReturn("Chelyabinsk")

        val futureIso = java.time.OffsetDateTime.now().plusHours(1).toString()
        whenever(benzonavtProvider.fetchCityPrices(any())).doReturn(
            mapOf("AI-95" to FuelPriceInfo(52.0, 50.0, 55.0, 3, futureIso))
        )

        val result = applier.applyAllPrices(base)
        assertEquals(52.0, result.first().fuelTypes.first().price, 0.001)
    }
}
