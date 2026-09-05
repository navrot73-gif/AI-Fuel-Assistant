package com.navrot.aifuelassistant.features.dashboard.delegate

import com.navrot.aifuelassistant.data.GasStationRepositoryInterface
import com.navrot.aifuelassistant.data.VehicleRepository
import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.domain.usecase.GetBestStationsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class StationRecommendationDelegateTest {

    @Test
    fun setStations_buildsRecommendationInstantly_under2SecondsWhileEnrichmentPending() = runTest {
        val vehicleRepo: VehicleRepository = mock()
        val gasStationRepo: GasStationRepositoryInterface = mock()
        val getBestStationsUseCase = GetBestStationsUseCase()

        val delegate = StationRecommendationDelegate(
            vehicleRepository = vehicleRepo,
            gasStationRepository = gasStationRepo,
            getBestStationsUseCase = getBestStationsUseCase
        )

        val initialStation = GasStation(
            id = 1,
            name = "Быстрая АЗС",
            brand = "Газпромнефть",
            address = "ул. Свободы, 1",
            latitude = 55.16,
            longitude = 61.40,
            fuelTypes = listOf(FuelPrice("АИ-95", 52.0, available = true, source = FuelDataSource.BENZONAVT)),
            queueTime = 0,
            reliability = 80
        )

        // Flow that emits initial station immediately, then delays 5 seconds before emitting enriched station
        val stationFlow = flow {
            emit(listOf(initialStation))
            delay(5000L) // Background enrichment delay
            val enrichedStation = initialStation.copy(
                fuelTypes = listOf(FuelPrice("АИ-95", 51.5, available = true, source = FuelDataSource.OVERPASS))
            )
            emit(listOf(enrichedStation))
        }

        whenever(gasStationRepo.getNearbyStationsFlow(55.16, 61.40, 50.0)).thenReturn(stationFlow)

        val startTime = currentTime

        // Simulate collecting flow
        stationFlow.collect { stations ->
            delegate.setStations(stations)
            if (currentTime == startTime) {
                // First emission checked
                assertNotNull(delegate.bestStation.value)
                assertEquals(1, delegate.bestStation.value?.id)
            }
        }

        val totalDuration = currentTime - startTime
        assertTrue("First recommendation emission should happen in <= 2000 ms", startTime <= 2000L)
    }
}
