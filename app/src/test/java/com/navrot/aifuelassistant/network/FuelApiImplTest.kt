package com.navrot.aifuelassistant.network

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class FuelApiImplTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var fuelApi: FuelApiImpl

    @Before
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()

        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()

        fuelApi = FuelApiImpl(client, baseUrl = mockServer.url("/route").toString())
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun getRoute_multipleRoutesResponse_parsesSuccessfullyAndVerifiesRequest() = runTest {
        val jsonResponse = """
            {
              "distance_m": 12500.5,
              "duration_s": 900.0,
              "points": [[55.75, 37.61], [55.76, 37.62]],
              "routes": [
                {
                  "distance_m": 12500.5,
                  "duration_s": 900.0,
                  "points": [[55.75, 37.61], [55.76, 37.62]]
                },
                {
                  "distance_m": 14000.0,
                  "duration_s": 1020.0,
                  "points": [[55.75, 37.61], [55.77, 37.63]]
                }
              ]
            }
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(jsonResponse)
        )

        val result = fuelApi.getRoute(
            fromLon = 37.61,
            fromLat = 55.75,
            toLon = 37.62,
            toLat = 55.76,
            alternatives = true
        )

        assertTrue("Expected result to be success, but was failure: ${result.exceptionOrNull()}", result.isSuccess)
        val routeResponse = result.getOrNull()!!

        assertEquals(12500.5, routeResponse.distance_m, 0.001)
        assertEquals(900.0, routeResponse.duration_s, 0.001)
        assertEquals(2, routeResponse.points.size)
        assertEquals(2, routeResponse.routes.size)

        assertEquals(14000.0, routeResponse.routes[1].distanceMeters, 0.001)

        val request = mockServer.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("AIFuelAssistant/1.0", request.getHeader("User-Agent"))
        assertEquals("application/json", request.getHeader("Accept"))

        val requestUrl = request.requestUrl!!
        assertEquals("/route", requestUrl.encodedPath)
        assertEquals("37.61,55.75", requestUrl.queryParameter("from"))
        assertEquals("37.62,55.76", requestUrl.queryParameter("to"))
        assertEquals("true", requestUrl.queryParameter("alternatives"))
    }

    @Test
    fun getRoute_singleRouteResponse_parsesSuccessfully() = runTest {
        val jsonResponse = """
            {
              "distance_m": 8200.0,
              "duration_s": 650.0,
              "points": [[55.75, 37.61], [55.755, 37.615]]
            }
        """.trimIndent()

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(jsonResponse)
        )

        val result = fuelApi.getRoute(
            fromLon = 37.61,
            fromLat = 55.75,
            toLon = 37.62,
            toLat = 55.76,
            alternatives = false
        )

        assertTrue("Expected result to be success, but was failure: ${result.exceptionOrNull()}", result.isSuccess)
        val routeResponse = result.getOrNull()!!

        assertEquals(8200.0, routeResponse.distance_m, 0.001)
        assertEquals(650.0, routeResponse.duration_s, 0.001)
        assertEquals(2, routeResponse.points.size)
        assertEquals(1, routeResponse.routes.size)
    }

    @Test
    fun getRoute_httpErrorStatus_returnsFailureResult() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )

        val result = fuelApi.getRoute(37.61, 55.75, 37.62, 55.76)

        assertTrue("Expected result to be failure", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue("Unexpected exception message: ${exception?.message}", exception?.message?.contains("HTTP 500") == true)
    }

    @Test
    fun getRoute_emptyResponseBody_returnsFailureResult() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("")
        )

        val result = fuelApi.getRoute(37.61, 55.75, 37.62, 55.76)

        assertTrue("Expected result to be failure", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue("Unexpected exception message: ${exception?.message}", exception?.message != null)
    }

    @Test
    fun getRoute_malformedJsonBody_returnsFailureResult() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{ corrupted json: true ")
        )

        val result = fuelApi.getRoute(37.61, 55.75, 37.62, 55.76)

        assertTrue("Expected result to be failure", result.isFailure)
    }
}
