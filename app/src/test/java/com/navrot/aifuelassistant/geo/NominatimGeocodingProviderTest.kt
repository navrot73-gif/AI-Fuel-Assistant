package com.navrot.aifuelassistant.geo

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NominatimGeocodingProviderTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var provider: NominatimGeocodingProvider

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val baseUrl = mockWebServer.url("/")

        // Custom client or test subclass could override URL, or we test cache logic via provider
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `toCitySlug converts supported Russian city names to expected slugs`() {
        assertEquals("chelyabinsk", GeoUtils.toCitySlug("Челябинск"))
        assertEquals("troitsk", GeoUtils.toCitySlug("Троицк"))
        assertEquals("miass", GeoUtils.toCitySlug("Миасс"))
        assertEquals("zlatoust", GeoUtils.toCitySlug("Златоуст"))
        assertEquals("magnitogorsk", GeoUtils.toCitySlug("Магнитогорск"))
        assertEquals("kopeysk", GeoUtils.toCitySlug("Копейск"))
        assertEquals("moscow", GeoUtils.toCitySlug("Москва"))
        assertEquals("ekaterinburg", GeoUtils.toCitySlug("Екатеринбург"))
        assertEquals("tyumen", GeoUtils.toCitySlug("Тюмень"))
        assertEquals("perm", GeoUtils.toCitySlug("Пермь"))
        assertEquals("chelyabinsk", GeoUtils.toCitySlug(null))
        assertEquals("chelyabinsk", GeoUtils.toCitySlug("Неизвестный Город"))
    }
}
