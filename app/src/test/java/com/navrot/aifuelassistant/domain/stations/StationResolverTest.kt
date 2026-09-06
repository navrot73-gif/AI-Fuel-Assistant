package com.navrot.aifuelassistant.domain.stations

import com.navrot.aifuelassistant.data.GasStationRepositoryInterface
import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class StationResolverTest {

    private val mockRepository = mock<GasStationRepositoryInterface>()
    private lateinit var resolver: StationResolver

    private val userLat = 55.1644
    private val userLon = 61.4368

    private val osmStationSverdlovsky = GasStation(
        id = -1001,
        name = "Татнефть",
        brand = "Татнефть",
        address = "Челябинск, Свердловский тракт, 12в",
        latitude = 55.1700,
        longitude = 61.4100,
        fuelTypes = listOf(
            FuelPrice("АИ-95", 0.0, available = false, source = FuelDataSource.OVERPASS)
        ),
        queueTime = 0,
        reliability = 0,
        dataSources = setOf(FuelDataSource.OVERPASS),
        osmId = "osm:12345"
    )

    private val staticStationKurchatova = GasStation(
        id = 10,
        name = "Газпромнефть",
        brand = "Газпромнефть",
        address = "Челябинск, ул. Курчатова, 2/1",
        latitude = 55.1500,
        longitude = 61.4200,
        fuelTypes = listOf(
            FuelPrice("АИ-95", 62.0, available = true)
        ),
        queueTime = 0,
        reliability = 90
    )

    private val staticStationSverdlovskyGazprom = GasStation(
        id = 12,
        name = "Газпромнефть Свердловский тракт",
        brand = "Газпромнефть",
        address = "Челябинск, Свердловский тракт, 40/1",
        latitude = 55.1847,
        longitude = 61.4098,
        fuelTypes = listOf(
            FuelPrice("АИ-95", 61.0, available = true)
        ),
        queueTime = 0,
        reliability = 95
    )

    @Before
    fun setup() = runTest {
        whenever(mockRepository.getNearbyStations(any(), any(), any()))
            .thenReturn(listOf(osmStationSverdlovsky, staticStationKurchatova, staticStationSverdlovskyGazprom))
        whenever(mockRepository.getAllStations())
            .thenReturn(listOf(osmStationSverdlovsky, staticStationKurchatova, staticStationSverdlovskyGazprom))

        resolver = StationResolver(mockRepository)
    }

    @Test
    fun `nearestByBrand finds OSM station with negative ID (Sverdlovsky case)`() = runTest {
        val result = resolver.nearestByBrand("Татнефть", userLat, userLon)
        assertNotNull(result)
        assertEquals(-1001, result?.id)
        assertEquals("Челябинск, Свердловский тракт, 12в", result?.address)
    }

    @Test
    fun `findByAddress finds station by substring ignoring case`() = runTest {
        val results = resolver.findByAddress("Свердловский тракт", userLat, userLon)
        assertEquals(2, results.size)
        assertTrue(results.any { it.id == -1001 })
        assertTrue(results.any { it.id == 12 })
    }

    @Test
    fun `resolveQuery with brand and address returns correct intersection station`() = runTest {
        val resolved = resolver.resolveQuery("Газпромнефть Свердловский тракт", userLat, userLon)
        assertNotNull(resolved)
        assertEquals(12, resolved?.id)
        assertEquals("Челябинск, Свердловский тракт, 40/1", resolved?.address)
    }

    @Test
    fun `resolveQuery with address only returns nearest address match`() = runTest {
        val resolved = resolver.resolveQuery("Свердловский тракт", userLat, userLon)
        assertNotNull(resolved)
        assertEquals(-1001, resolved?.id)
    }
}
