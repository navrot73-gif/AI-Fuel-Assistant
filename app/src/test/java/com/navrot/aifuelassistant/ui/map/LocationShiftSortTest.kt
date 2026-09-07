package com.navrot.aifuelassistant.ui.map

import com.navrot.aifuelassistant.data.GasStationRepositoryInterface
import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.domain.usecase.GetBestStationsUseCase
import com.navrot.aifuelassistant.geo.GeoUtils
import com.navrot.aifuelassistant.ui.map.delegate.MapFilterDelegate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class LocationShiftSortTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val now = System.currentTimeMillis()

    // Station A: Lat 55.1500, Lon 61.4000
    private val stationA = GasStation(
        id = 1,
        brand = "Газпромнефть",
        name = "Станция А",
        latitude = 55.1500,
        longitude = 61.4000,
        address = "Курчатова",
        fuelTypes = listOf(FuelPrice("АИ-95", 55.0, available = true, source = FuelDataSource.DEMO, updatedAt = now)),
        queueTime = 0,
        reliability = 100,
        dataSources = setOf(FuelDataSource.DEMO)
    )

    // Station B: Lat 55.2000, Lon 61.4000 (~5.5 km north of Station A)
    private val stationB = GasStation(
        id = 2,
        brand = "Газпромнефть",
        name = "Станция Б",
        latitude = 55.2000,
        longitude = 61.4000,
        address = "Свердловский тракт",
        fuelTypes = listOf(FuelPrice("АИ-95", 55.0, available = true, source = FuelDataSource.DEMO, updatedAt = now)),
        queueTime = 0,
        reliability = 100,
        dataSources = setOf(FuelDataSource.DEMO)
    )

    private val initialStations = listOf(stationA, stationB)

    @Test
    fun userShiftsBy5km_topStationInNearbySortChanges() = runTest {
        val repository = mock<GasStationRepositoryInterface>()
        whenever(repository.getBestStations(any(), any(), any(), any())).doAnswer { invocation ->
            val lat = invocation.getArgument<Double?>(1)
            val lon = invocation.getArgument<Double?>(2)
            if (lat != null && lon != null) {
                initialStations.sortedBy { GeoUtils.calculateDistance(lat, lon, it.latitude, it.longitude) }
            } else initialStations
        }

        val getBestStationsUseCase = GetBestStationsUseCase()
        val delegate = MapFilterDelegate(repository, getBestStationsUseCase)

        delegate.updateStations(initialStations)

        // Location 1: Close to Station A (55.1501, 61.4001)
        val loc1Lat = 55.1501
        val loc1Lon = 61.4001
        delegate.onLocationUpdated(testScope, loc1Lat, loc1Lon)

        val topStationAtLoc1 = delegate.stations.value.minByOrNull {
            GeoUtils.calculateDistance(loc1Lat, loc1Lon, it.latitude, it.longitude)
        }
        assertEquals("Station A should be top when user is near Station A", 1, topStationAtLoc1?.id)

        // Location 2: User shifts 5.5 km north, close to Station B (55.2001, 61.4001)
        val loc2Lat = 55.2001
        val loc2Lon = 61.4001
        delegate.onLocationUpdated(testScope, loc2Lat, loc2Lon)

        val topStationAtLoc2 = delegate.stations.value.minByOrNull {
            GeoUtils.calculateDistance(loc2Lat, loc2Lon, it.latitude, it.longitude)
        }
        assertEquals("Station B should become top when user shifts 5km to Station B", 2, topStationAtLoc2?.id)
    }
}
