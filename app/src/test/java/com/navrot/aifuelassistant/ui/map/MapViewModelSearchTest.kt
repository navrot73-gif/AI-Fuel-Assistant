package com.navrot.aifuelassistant.ui.map

import com.navrot.aifuelassistant.data.GasStationRepositoryInterface
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.providers.BenzonavtProvider
import com.navrot.aifuelassistant.domain.usecase.GetBestStationsUseCase
import com.navrot.aifuelassistant.geo.GeoException
import com.navrot.aifuelassistant.geo.GeocodingProvider
import com.navrot.aifuelassistant.geo.GeocodingResult
import com.navrot.aifuelassistant.geo.GeoPoint
import com.navrot.aifuelassistant.ui.map.TileWarmupService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@ExperimentalCoroutinesApi
class MapViewModelSearchTest {

    @Mock
    private lateinit var repository: GasStationRepositoryInterface

    @Mock
    private lateinit var geocodingProvider: GeocodingProvider

    @Mock
    private lateinit var okHttpClient: OkHttpClient

    @Mock
    private lateinit var getBestStationsUseCase: GetBestStationsUseCase

    @Mock
    private lateinit var benzonavtProvider: BenzonavtProvider

    @Mock
    private lateinit var tileWarmupService: TileWarmupService

    private lateinit var viewModel: MapViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        viewModel = MapViewModel(
            repository = repository,
            okHttpClient = okHttpClient,
            getBestStationsUseCase = getBestStationsUseCase,
            benzonavtProvider = benzonavtProvider,
            tileWarmupService = tileWarmupService,
            geocodingProvider = geocodingProvider
        )
    }

    private fun makeStation(
        id: Int,
        name: String,
        brand: String,
        lat: Double,
        lon: Double,
        price: Double = 50.0
    ): GasStation {
        return GasStation(
            id = id,
            name = name,
            brand = brand,
            address = "",
            latitude = lat,
            longitude = lon,
            fuelTypes = listOf(FuelPrice("АИ-95", price, true)),
            queueTime = 0,
            reliability = 90,
            dataSources = emptySet(),
            updatedAt = 0L,
            confidence = 0,
            photoEvidence = emptyList()
        )
    }

    @Test
    fun `local match does NOT trigger geocoding`() = runTest {
        // Given: локальный поиск возвращает станции
        val localStations = listOf(makeStation(1, "АЗС 1", "Лукойл", 55.0, 61.0))
        `when`(repository.searchStations("Лукойл")).thenReturn(localStations)

        // When: ищем "Лукойл"
        viewModel.searchStations("Лукойл")
        advanceUntilIdle()

        // Then: геокодинг НЕ вызывался
        Mockito.verify(geocodingProvider, Mockito.never()).geocode(Mockito.anyString())

        // И результат — локальные станции
        assertEquals(localStations, viewModel.stations.value)
        assertNull(viewModel.geocodedLocation.value)
    }

    @Test
    fun `fallback to geocodingProvider when no local matches (success)`() = runTest {
        // Given: локальный поиск пуст, геокодинг возвращает точку
        `when`(repository.searchStations("Неизвестный адрес")).thenReturn(emptyList())
        val geoResult = GeocodingResult(
            point = GeoPoint(55.123, 61.456),
            displayName = "Челябинск, ул. Ленина, 1"
        )
        `when`(geocodingProvider.geocode("Неизвестный адрес")).thenReturn(geoResult)
        `when`(repository.getNearbyStations(55.123, 61.456, 10.0)).thenReturn(listOf(
            makeStation(2, "АЗС рядом", "Газпром", 55.123, 61.456, 51.0)
        ))

        // When: ищем "Неизвестный адрес"
        viewModel.searchStations("Неизвестный адрес")
        advanceUntilIdle()

        // Then: геокодинг вызывался
        Mockito.verify(geocodingProvider).geocode("Неизвестный адрес")

        // Результат — станции рядом с геокодированной точкой
        val stations = viewModel.stations.value
        assertEquals(1, stations.size)
        assertEquals("АЗС рядом", stations[0].name)

        // И фокус установлен на геокодированную точку
        val focus = viewModel.geocodedLocation.value
        assertNotNull(focus)
        assertEquals(55.123, focus!!.latitude, 0.001)
        assertEquals(61.456, focus.longitude, 0.001)
    }

    @Test
    fun `GeoException_NoResults → empty result without error`() = runTest {
        // Given: локальный поиск пуст, геокодинг кидает NoResults
        `when`(repository.searchStations("qwerty")).thenReturn(emptyList())
        `when`(geocodingProvider.geocode("qwerty")).thenThrow(GeoException.NoResults("Ничего не найдено"))

        // When
        viewModel.searchStations("qwerty")
        advanceUntilIdle()

        // Then: пустой список, нет ошибки, фокус сброшен
        assertTrue(viewModel.stations.value.isEmpty())
        assertNull(viewModel.error.value)
        assertNull(viewModel.geocodedLocation.value)
    }

    @Test
    fun `GeoException_NetworkError → graceful handling`() = runTest {
        // Given: локальный поиск пуст, геокодинг кидает NetworkError
        `when`(repository.searchStations("offline")).thenReturn(emptyList())
        `when`(geocodingProvider.geocode("offline")).thenThrow(GeoException.NetworkError("Нет сети"))

        // When
        viewModel.searchStations("offline")
        advanceUntilIdle()

        // Then: пустой список, ошибка в UI, фокус не меняется
        assertTrue(viewModel.stations.value.isEmpty())
        assertEquals("Нет сети для геокодинга", viewModel.error.value)
        // Последняя сохраненная локация осталась бы (null в тесте)
    }

    @Test
    fun `clearGeocodedLocation resets focus`() = runTest {
        // Given: есть геокодированная точка
        val geoResult = GeocodingResult(
            point = GeoPoint(55.123, 61.456),
            displayName = "Челябинск"
        )
        `when`(repository.searchStations("ул. Ленина")).thenReturn(emptyList())
        `when`(geocodingProvider.geocode("ул. Ленина")).thenReturn(geoResult)
        `when`(repository.getNearbyStations(55.123, 61.456, 10.0)).thenReturn(emptyList())

        viewModel.searchStations("ул. Ленина")
        advanceUntilIdle()
        assertNotNull(viewModel.geocodedLocation.value)

        // When: очищаем
        viewModel.clearGeocodedLocation()

        // Then: фокус сброшен
        assertNull(viewModel.geocodedLocation.value)
    }

    @Test
    fun `race-guard fast input 3 queries only last executes`() = runTest {
        // Given: локальный поиск пуст для всех запросов
        `when`(repository.searchStations(Mockito.anyString())).thenReturn(emptyList())

        // Первые два запроса — медленные (должны отброситься)
        val slowGeoResult = GeocodingResult(GeoPoint(1.0, 1.0), "slow")
        // Последний запрос — быстрый (должен сработать)
        val fastGeoResult = GeocodingResult(GeoPoint(55.555, 61.666), "fast")

        var callCount = 0
        Mockito.doAnswer { invocation ->
            callCount++
            if (callCount <= 2) {
                // Имитируем задержку для первых двух вызовов через Thread.sleep
                Thread.sleep(500)
            }
            if (callCount == 1 || callCount == 2) {
                return@doAnswer slowGeoResult
            }
            return@doAnswer fastGeoResult
        }.`when`(geocodingProvider).geocode(Mockito.anyString())

        `when`(repository.getNearbyStations(Mockito.anyDouble(), Mockito.anyDouble(), Mockito.anyDouble()))
            .thenReturn(emptyList())

        // When: три быстрых запроса подряд
        viewModel.searchStations("a")
        viewModel.searchStations("ab")
        viewModel.searchStations("abc")

        advanceUntilIdle()

        // Then: только последний запрос дал результат
        val focus = viewModel.geocodedLocation.value
        assertNotNull(focus)
        assertEquals(55.555, focus!!.latitude, 0.001)
        assertEquals(61.666, focus.longitude, 0.001)
    }
}