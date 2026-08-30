package com.navrot.aifuelassistant.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.data.providers.BenzonavtProvider
import com.navrot.aifuelassistant.data.providers.FuelPriceInfo
import com.navrot.aifuelassistant.domain.usecase.GetBestStationsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

/**
 * Интеграционные тесты [GasStationRepository] на Robolectric.
 *
 * Покрывает ключевые сценарии:
 *  - загрузка станций из assets/stations.json (fallback при отсутствии сети)
 *  - searchStations: фильтр по name/brand/address
 *  - getStationsByCity: фильтр по адресу
 *  - getNearbyStations: фильтр по радиусу + сортировка по расстоянию
 *  - getCheapestStation / getStationsSortedByPriceAsc/Desc / getStationsByQueue
 *  - reportUserPrice / clearUserPrice
 *  - applyBenzonavtToStation (через reportUserPrice → applyAllPrices)
 *
 * Зависимости:
 *  - Context из ApplicationProvider (Robolectric даёт реальный Context с доступом к assets)
 *  - OkHttpClient — реальный (но remote-загрузка будет фейлиться, т.к. URL хардкожен
 *    на raw.githubusercontent.com — без сети просто вернёт null и пойдёт fallback)
 *  - UserPriceRepository — mock (SharedPreferences тоже можно через Robolectric,
 *    но mock чище для проверки вызовов report/clear)
 *  - BenzonavtProvider — mock (возвращает emptyMap, чтобы не зависеть от сети)
 *  - CoroutineScope — реальный с SupervisorJob (для фоновой подкачки цен)
 *
 * Важно: `runBlocking` используется вместо `runTest`, потому что некоторые методы
 * репозитория запускают фоновые корутины через `appScope.launch`, которые должны
 * реально выполниться (а в `runTest` виртуальное время не продвигается автоматически).
 */
@RunWith(RobolectricTestRunner::class)
class GasStationRepositoryTest {

    private lateinit var context: Context
    private lateinit var httpClient: OkHttpClient
    private lateinit var appScope: CoroutineScope

    // Используем прямое создание mock-объектов вместо @Mock + MockitoAnnotations.openMocks(this).
    // Robolectric + @Mock иногда конфликтуют при инициализации lateinit-полей
    // (MockitoAnnotations не находит аннотации в Robolectric classloader).
    // Прямой mock<T>() надёжнее и не требует openMocks().
    private val userPrices: UserPriceRepository = mock()
    private val benzonavtProvider: BenzonavtProvider = mock()
    private val getBestStationsUseCase: GetBestStationsUseCase = mock()

    private lateinit var repository: GasStationRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        httpClient = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        // Сбрасываем mock-состояние между тестами (mock() создаёт новые инстансы
        // при инициализации поля, но поля — val, поэтому инстансы не пересоздаются.
        // Mockito.reset гарантирует, что stubbing из предыдущего теста не протекает.)
        org.mockito.Mockito.reset(userPrices, benzonavtProvider, getBestStationsUseCase)

        // userPrices по умолчанию пустой
        whenever(userPrices.getAll()).doReturn(emptyMap())
        // benzonavtProvider по умолчанию возвращает пустую Map (нет данных)
        whenever(benzonavtProvider.currentCity()).doReturn("chelyabinsk")
        whenever(benzonavtProvider.fetchCityPrices(any())).doReturn(emptyMap())

        repository = GasStationRepository(
            context = context,
            httpClient = httpClient,
            userPrices = userPrices,
            getBestStationsUseCase = getBestStationsUseCase,
            benzonavtProvider = benzonavtProvider,
            appScope = appScope
        )
    }

    // ==================== Базовая загрузка из assets ====================

    @Test
    fun `getAllStations loads from assets when network and cache unavailable`() = runBlocking {
        // Remote-запрос к raw.githubusercontent.com без сети/в CI упадёт,
        // cache-файла нет — должен сработать fallback на assets/stations.json.
        val stations = repository.getAllStations()

        assertTrue("Should load at least 1 station from assets", stations.isNotEmpty())
        // assets/stations.json содержит реальные данные проекта
        assertTrue("Should load many stations (assets has ~30+)", stations.size >= 5)
    }

    @Test
    fun `loaded stations have valid required fields`() = runBlocking {
        val stations = repository.getAllStations()
        assertTrue(stations.isNotEmpty())

        val first = stations.first()
        assertTrue("id should be positive", first.id > 0)
        assertTrue("name should not be blank", first.name.isNotBlank())
        assertTrue("brand should not be blank", first.brand.isNotBlank())
        assertTrue("address should not be blank", first.address.isNotBlank())
        assertTrue("latitude in valid range", first.latitude in -90.0..90.0)
        assertTrue("longitude in valid range", first.longitude in -180.0..180.0)
        assertTrue("fuelTypes should not be empty", first.fuelTypes.isNotEmpty())
        assertTrue("queueTime >= 0", first.queueTime >= 0)
        assertTrue("reliability in 0..100", first.reliability in 0..100)
    }

    @Test
    fun `loaded stations contain at least one fuel type with positive price`() = runBlocking {
        val stations = repository.getAllStations()
        val anyPositivePrice = stations.any { s ->
            s.fuelTypes.any { it.price > 0.0 && it.available }
        }
        assertTrue("At least one station should have a positive-price available fuel", anyPositivePrice)
    }

    // ==================== searchStations ====================

    @Test
    fun `searchStations matches by name`() = runBlocking {
        // Сначала загружаем, чтобы знать имена
        val all = repository.getAllStations()
        assertTrue("Need at least one station for search test", all.isNotEmpty())

        val target = all.first()
        // Берём кусок имени — searchStations использует contains
        val query = target.name.take(3)
        val results = repository.searchStations(query)

        assertTrue(
            "Search by name fragment '$query' should find at least the original station",
            results.any { it.id == target.id }
        )
    }

    @Test
    fun `searchStations matches by brand`() = runBlocking {
        val all = repository.getAllStations()
        val target = all.first()
        val query = target.brand.take(3)
        val results = repository.searchStations(query)
        assertTrue(
            "Search by brand fragment '$query' should find at least the original",
            results.any { it.id == target.id }
        )
    }

    @Test
    fun `searchStations is case insensitive`() = runBlocking {
        val all = repository.getAllStations()
        val target = all.first()
        val query = target.name.take(3).lowercase()
        val results = repository.searchStations(query)
        assertTrue(
            "Lowercase query '$query' should match station name '${target.name}'",
            results.any { it.id == target.id }
        )
    }

    @Test
    fun `searchStations returns empty for non-matching query`() = runBlocking {
        repository.getAllStations() // warm up cache
        val results = repository.searchStations("zzzzz_nonexistent_xyz_12345")
        assertTrue("Non-matching query should return empty list", results.isEmpty())
    }

    // ==================== getStationsByCity ====================

    @Test
    fun `getStationsByCity filters by address substring`() = runBlocking {
        val all = repository.getAllStations()
        // Берём часть адреса существующей станции
        val target = all.first()
        val cityFragment = target.address.substringBefore(",").ifBlank { target.address.take(5) }

        val results = repository.getStationsByCity(cityFragment)
        assertTrue(
            "Stations with address containing '$cityFragment' should be found",
            results.isNotEmpty()
        )
        assertTrue(
            "All results should contain the city fragment in address",
            results.all { it.address.contains(cityFragment, ignoreCase = true) }
        )
    }

    // ==================== getNearbyStations ====================

    @Test
    fun `getNearbyStations returns stations within radius sorted by distance`() = runBlocking {
        val all = repository.getAllStations()
        assertTrue("Need stations for test", all.isNotEmpty())

        // Берём координаты первой станции как центр
        val center = all.first()
        val lat = center.latitude
        val lon = center.longitude

        // Маленький радиус — должна найтись хотя бы сама станция
        val nearby = repository.getNearbyStations(lat, lon, radiusKm = 1.0)
        assertTrue(
            "At least the center station should be within 1km of itself",
            nearby.any { it.id == center.id }
        )

        // Проверка сортировки по расстоянию
        val distances = nearby.map {
            com.navrot.aifuelassistant.geo.GeoUtils.calculateDistance(lat, lon, it.latitude, it.longitude)
        }
        val sorted = distances.sorted()
        assertEquals(
            "Nearby stations should be sorted by distance ascending",
            sorted, distances
        )
    }

    @Test
    fun `getNearbyStations with very small radius returns few stations`() = runBlocking {
        val all = repository.getAllStations()
        val center = all.first()

        // 0.1 км = 100 метров — только сама станция (если координаты точные)
        val veryNearby = repository.getNearbyStations(center.latitude, center.longitude, radiusKm = 0.1)
        assertTrue(
            "Very small radius should return at most a few stations",
            veryNearby.size <= 5
        )
    }

    // ==================== getCheapestStation ====================

    @Test
    fun `getCheapestStation returns null when no station has matching fuel`() = runBlocking {
        repository.getAllStations() // warm up
        val cheapest = repository.getCheapestStation(
            fuelType = "Несуществующее топливо 999",
            lat = null,
            lon = null,
            radiusKm = 100.0
        )
        assertNull("Should return null for non-existent fuel type", cheapest)
    }

    @Test
    fun `getCheapestStation returns station with minimum price for fuel`() = runBlocking {
        val all = repository.getAllStations()
        // Находим топливо, которое есть хотя бы на 2 станциях
        val fuelTypes = all.flatMap { it.fuelTypes }
            .filter { it.available }
            .groupBy { it.type }
            .filter { it.value.size >= 2 }
        assertTrue("Need at least one fuel type present on 2+ stations", fuelTypes.isNotEmpty())

        val fuelType = fuelTypes.keys.first()
        val cheapest = repository.getCheapestStation(fuelType, lat = null, lon = null, radiusKm = 1000.0)
        assertNotNull("Cheapest should not be null for existing fuel type", cheapest)

        val minPrice = all
            .filter { s -> s.fuelTypes.any { it.type == fuelType && it.available } }
            .minOf { s -> s.fuelTypes.first { it.type == fuelType }.price }
        assertEquals(
            "Cheapest station should have the minimum price for $fuelType",
            minPrice,
            cheapest!!.fuelTypes.first { it.type == fuelType }.price,
            0.001
        )
    }

    // ==================== getStationsSortedByPriceAsc / Desc ====================

    @Test
    fun `getStationsSortedByPriceAsc returns stations sorted by price ascending`() = runBlocking {
        val all = repository.getAllStations()
        val availableFuels = all.flatMap { it.fuelTypes }
            .filter { it.available }
            .groupBy { it.type }
            .filter { it.value.size >= 2 }
        assertTrue("Need fuel type on 2+ stations", availableFuels.isNotEmpty())

        val fuelType = availableFuels.keys.first()
        val sorted = repository.getStationsSortedByPriceAsc(fuelType, lat = null, lon = null, radiusKm = 1000.0)
        assertTrue("Should return at least 2 stations", sorted.size >= 2)

        val prices = sorted.map { it.fuelTypes.first { f -> f.type == fuelType }.price }
        val sortedPrices = prices.sorted()
        assertEquals("Prices should be in ascending order", sortedPrices, prices)
    }

    @Test
    fun `getStationsSortedByPriceDesc returns stations sorted by price descending`() = runBlocking {
        val all = repository.getAllStations()
        val availableFuels = all.flatMap { it.fuelTypes }
            .filter { it.available }
            .groupBy { it.type }
            .filter { it.value.size >= 2 }
        assertTrue("Need fuel type on 2+ stations", availableFuels.isNotEmpty())

        val fuelType = availableFuels.keys.first()
        val sorted = repository.getStationsSortedByPriceDesc(fuelType, lat = null, lon = null, radiusKm = 1000.0)
        assertTrue("Should return at least 2 stations", sorted.size >= 2)

        val prices = sorted.map { it.fuelTypes.first { f -> f.type == fuelType }.price }
        val sortedPrices = prices.sortedDescending()
        assertEquals("Prices should be in descending order", sortedPrices, prices)
    }

    // ==================== getStationsByQueue ====================

    @Test
    fun `getStationsByQueue returns stations sorted by queue time ascending`() = runBlocking {
        val all = repository.getAllStations()
        val availableFuels = all.flatMap { it.fuelTypes }
            .filter { it.available }
            .groupBy { it.type }
            .filter { it.value.size >= 2 }
        assertTrue("Need fuel type on 2+ stations", availableFuels.isNotEmpty())

        val fuelType = availableFuels.keys.first()
        val sorted = repository.getStationsByQueue(fuelType, lat = null, lon = null, radiusKm = 1000.0)
        assertTrue(sorted.isNotEmpty())

        val queues = sorted.map { it.queueTime }
        val sortedQueues = queues.sorted()
        assertEquals("Queue times should be ascending", sortedQueues, queues)
    }

    // ==================== reportUserPrice / clearUserPrice ====================

    @Test
    fun `reportUserPrice calls userPrices_report and updates cache`() = runBlocking {
        val all = repository.getAllStations()
        val target = all.first()
        val fuelType = target.fuelTypes.first().type
        val originalPrice = target.fuelTypes.first { it.type == fuelType }.price
        val newPrice = originalPrice + 100.0 // сильно отличается

        // Мокаем: после report, getAll возвращает наш override
        val overrides = mapOf(Pair(target.id, fuelType) to newPrice)
        whenever(userPrices.getAll()).doReturn(overrides)
        // applyAllPrices вызовет benzonavtProvider.fetchCityPrices — он пустой по умолчанию
        whenever(benzonavtProvider.fetchCityPrices(any())).doReturn(emptyMap())

        val updated = repository.reportUserPrice(target.id, fuelType, newPrice)

        val updatedStation = updated.find { it.id == target.id }
        assertNotNull("Updated station should be in result", updatedStation)
        assertEquals(
            "Fuel price should be overridden by user price",
            newPrice,
            updatedStation!!.fuelTypes.first { it.type == fuelType }.price,
            0.001
        )
    }

    @Test
    fun `clearUserPrice removes override so subsequent load shows original price`() = runBlocking {
        // Контракт: reportUserPrice мутирует cachedStations напрямую (applyAllPrices
        // применяет user override к кешу). clearUserPrice вызывает userPrices.clear()
        // (mock, no-op) и applyAllPrices(cachedStations) — но cachedStations уже
        // содержит overridden price, и с пустым overrides он не восстановится.
        //
        // Чтобы проверить, что clearUserPrice действительно убирает override из
        // user-price хранилища, мы:
        //  1) загружаем станции (originalPrice)
        //  2) устанавливаем override
        //  3) очищаем override (mock getAll() теперь пустой)
        //  4) вызываем refresh() — он перезагружает станции из assets и
        //     применяет applyUserPrices с пустым overrides → оригинальная цена
        val all = repository.getAllStations()
        val target = all.first()
        val fuelType = target.fuelTypes.first().type
        val originalPrice = target.fuelTypes.first { it.type == fuelType }.price

        // 1) Устанавливаем override
        val overriddenPrice = originalPrice + 50.0
        val overrides = mapOf(Pair(target.id, fuelType) to overriddenPrice)
        whenever(userPrices.getAll()).doReturn(overrides)
        val afterReport = repository.reportUserPrice(target.id, fuelType, overriddenPrice)
        val overriddenStation = afterReport.find { it.id == target.id }
        assertEquals(
            "After reportUserPrice, fuel price should be overridden",
            overriddenPrice,
            overriddenStation!!.fuelTypes.first { it.type == fuelType }.price,
            0.001
        )

        // 2) Очищаем override — mock getAll() теперь пустой
        whenever(userPrices.getAll()).doReturn(emptyMap())
        whenever(benzonavtProvider.fetchCityPrices(any())).doReturn(emptyMap())
        repository.clearUserPrice(target.id, fuelType)

        // 3) refresh() перезагружает станции из assets (т.к. lastRemoteCheckMs
        //    обновится, но remote упадёт → fallback на assets) и применяет
        //    applyUserPrices с пустым overrides → оригинальная цена
        val refreshed = repository.refresh()
        val clearedStation = refreshed.find { it.id == target.id }
        assertNotNull("Cleared station should be in result", clearedStation)
        assertEquals(
            "After clearUserPrice + refresh, fuel price should be back to original",
            originalPrice,
            clearedStation!!.fuelTypes.first { it.type == fuelType }.price,
            0.001
        )
    }

    // ==================== applyBenzonavtToStation (через applyAllPrices) ====================

    @Test
    fun `benzonavt price overrides stations_json when benzonavt updatedAt is newer`() = runBlocking {
        val all = repository.getAllStations()
        val target = all.first()
        val fuelType = target.fuelTypes.first().type
        val originalPrice = target.fuelTypes.first { it.type == fuelType }.price

        // Benzonavt возвращает цену, сильно отличающуюся от оригинала
        val benzonavtPrice = originalPrice + 25.0
        val futureTimestamp = java.time.OffsetDateTime.now()
            .plusMinutes(1) // новее любого updatedAt из stations.json (там 0)
            .toString()
        val benzonavtData = mapOf(
            fuelType to FuelPriceInfo(
                median = benzonavtPrice,
                min = benzonavtPrice - 1.0,
                max = benzonavtPrice + 1.0,
                sourceCount = 5,
                updatedAt = futureTimestamp
            )
        )

        whenever(userPrices.getAll()).doReturn(emptyMap())
        whenever(benzonavtProvider.fetchCityPrices(any())).doReturn(benzonavtData)

        // reportUserPrice → applyAllPrices → applyBenzonavtToStation
        val updated = repository.reportUserPrice(target.id, fuelType, originalPrice)
        val updatedStation = updated.find { it.id == target.id }
        assertNotNull(updatedStation)

        val fuel = updatedStation!!.fuelTypes.first { it.type == fuelType }
        assertEquals(
            "Benzonavt price should override stations.json price (newer updatedAt)",
            benzonavtPrice,
            fuel.price,
            0.001
        )
        assertEquals(
            "Fuel source should be BENZONAVT after override",
            FuelDataSource.BENZONAVT,
            fuel.source
        )
        assertTrue(
            "Station dataSources should include BENZONAVT",
            updatedStation.dataSources.contains(FuelDataSource.BENZONAVT)
        )
    }

    @Test
    fun `benzonavt does NOT override when its updatedAt is older than current`() = runBlocking {
        val all = repository.getAllStations()
        val target = all.first()
        val fuelType = target.fuelTypes.first().type

        // Установим user price с актуальным updatedAt (текущее время)
        val userPrice = 99.99
        val userOverrides = mapOf(Pair(target.id, fuelType) to userPrice)
        whenever(userPrices.getAll()).doReturn(userOverrides)

        // Benzonavt с очень старым updatedAt (эпоха)
        val oldBenzonavt = mapOf(
            fuelType to FuelPriceInfo(
                median = 10.0,
                min = 10.0,
                max = 10.0,
                sourceCount = 1,
                updatedAt = "2020-01-01T00:00:00Z"
            )
        )
        whenever(benzonavtProvider.fetchCityPrices(any())).doReturn(oldBenzonavt)

        val updated = repository.reportUserPrice(target.id, fuelType, userPrice)
        val updatedStation = updated.find { it.id == target.id }
        assertNotNull(updatedStation)

        val fuel = updatedStation!!.fuelTypes.first { it.type == fuelType }
        // User price должен победить, т.к. benzonavt устаревший
        assertEquals(
            "User price should win over stale benzonavt",
            userPrice,
            fuel.price,
            0.001
        )
        assertFalse(
            "BENZONAVT should NOT be in dataSources when benzonavt is stale",
            updatedStation.dataSources.contains(FuelDataSource.BENZONAVT)
        )
    }

    // ==================== refresh ====================

    @Test
    fun `refresh returns stations list even when network fails`() = runBlocking {
        // refresh вызывает loadFromRemote (упадёт без сети) → fallback на cache/assets
        val refreshed = repository.refresh()
        assertTrue(
            "refresh should return stations from fallback (assets) when network fails",
            refreshed.isNotEmpty()
        )
    }

    // ==================== getStationById ====================

    @Test
    fun `getStationById returns station for valid id`() = runBlocking {
        val all = repository.getAllStations()
        val target = all.first()
        val found = repository.getStationById(target.id)
        assertNotNull("Should find station by its id", found)
        assertEquals(target.id, found!!.id)
        assertEquals(target.name, found.name)
    }

    @Test
    fun `getStationById returns null for non-existent id`() = runBlocking {
        repository.getAllStations() // warm up
        val found = repository.getStationById(999999)
        assertNull("Should return null for non-existent id", found)
    }

    @Test
    fun `getLastCacheUpdateTime returns valid timestamp when file exists or null when not`() = runBlocking {
        val time = repository.getLastCacheUpdateTime()
        if (time != null) {
            assertTrue("Cache update time should be positive", time > 0)
        }
    }

    // ==================== Overpass Merging & Deduplication ====================

    @Test
    fun `mergeStations combines base and overpass stations and deduplicates within 100m`() {
        val baseStation = GasStation(
            id = 1,
            name = "Base Station",
            brand = "Base",
            address = "Base Street 1",
            latitude = 55.1600,
            longitude = 61.4000,
            fuelTypes = emptyList(),
            queueTime = 0,
            reliability = 80
        )

        val duplicateOverpass = GasStation(
            id = -10,
            name = "Overpass Duplicate",
            brand = "Overpass",
            address = "Base Street 1",
            latitude = 55.1603, // ~30 meters away from baseStation
            longitude = 61.4000,
            fuelTypes = emptyList(),
            queueTime = 0,
            reliability = 0,
            dataSources = setOf(FuelDataSource.OVERPASS)
        )

        val distinctOverpass = GasStation(
            id = -11,
            name = "Overpass Far Away",
            brand = "OSM",
            address = "OSM Street 10",
            latitude = 55.2000, // ~4.4 km away
            longitude = 61.4000,
            fuelTypes = emptyList(),
            queueTime = 0,
            reliability = 0,
            dataSources = setOf(FuelDataSource.OVERPASS)
        )

        val merged = repository.mergeStations(
            baseStations = listOf(baseStation),
            overpassStations = listOf(duplicateOverpass, distinctOverpass)
        )

        assertEquals("Merged list size should be 2 (1 base + 1 distinct overpass)", 2, merged.size)
        assertTrue("Base station should be retained", merged.any { it.id == 1 })
        assertTrue("Distinct overpass station should be added", merged.any { it.id == -11 })
        assertFalse("Duplicate overpass station should be dropped", merged.any { it.id == -10 })
    }

    // ==================== Конкурентный доступ (sanity) ====================

    @Test
    fun `concurrent getAllStations calls do not corrupt cache`() = runBlocking {
        // Запускаем несколько конкурентных вызовов ensureLoaded.
        // Mutex должен гарантировать, что cachedStations не повредится.
        val concurrency = 8
        val results = Array<List<GasStation>?>(concurrency) { null }

        val threads = (0 until concurrency).map { idx ->
            Thread {
                results[idx] = runBlocking { repository.getAllStations() }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Все вызовы должны вернуть непустой список одинакового размера
        results.forEach { r ->
            assertNotNull("Thread result should not be null", r)
            assertTrue("Thread result should not be empty", r!!.isNotEmpty())
        }
        val firstSize = results[0]!!.size
        results.forEach { r ->
            assertEquals(
                "All concurrent calls should return the same number of stations",
                firstSize,
                r!!.size
            )
        }
    }
}
