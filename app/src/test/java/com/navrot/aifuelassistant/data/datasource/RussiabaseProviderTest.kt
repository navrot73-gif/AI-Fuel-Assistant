package com.navrot.aifuelassistant.data.datasource

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class RussiabaseProviderTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var okHttpClient: OkHttpClient

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .writeTimeout(1, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun fetchObservations_unmappedCity_returnsEmptyListSilently() = runTest {
        val provider = RussiabaseProviderImpl(okHttpClient, null)
        val result = provider.fetchObservations("non_existent_city_12345", listOf("ai95"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun fetchObservations_onNetworkError_degradesGracefullyToEmptyList() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val provider = RussiabaseProviderImpl(okHttpClient, null)
        val result = provider.fetchObservations("chelyabinsk", listOf("ai95"))
        assertTrue(result.isEmpty())
    }
}
