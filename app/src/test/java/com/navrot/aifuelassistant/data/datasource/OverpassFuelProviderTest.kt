package com.navrot.aifuelassistant.data.datasource

import com.navrot.aifuelassistant.data.model.FuelDataSource
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OverpassFuelProviderTest {

    private lateinit var httpClient: OkHttpClient
    private lateinit var provider: OverpassFuelProviderImpl

    @Before
    fun setUp() {
        httpClient = OkHttpClient()
        provider = OverpassFuelProviderImpl(httpClient)
    }

    @Test
    fun `parseOverpassResponse correctly parses node, way, and relation elements`() {
        val sampleJson = """
            {
              "elements": [
                {
                  "type": "node",
                  "id": 1234567,
                  "lat": 55.16,
                  "lon": 61.40,
                  "tags": {
                    "amenity": "fuel",
                    "name": "Лукойл",
                    "brand": "Лукойл",
                    "addr:street": "проспект Ленина",
                    "addr:housenumber": "25"
                  }
                },
                {
                  "type": "way",
                  "id": 7654321,
                  "center": {
                    "lat": 55.18,
                    "lon": 61.42
                  },
                  "tags": {
                    "amenity": "fuel",
                    "operator": "Газпромнефть",
                    "addr:street": "улица Свободы"
                  }
                },
                {
                  "type": "relation",
                  "id": 99999,
                  "center": {
                    "lat": 55.20,
                    "lon": 61.45
                  },
                  "tags": {
                    "amenity": "fuel"
                  }
                }
              ]
            }
        """.trimIndent()

        val stations = provider.parseOverpassResponse(sampleJson)

        assertEquals(3, stations.size)

        // Node station
        val nodeStation = stations[0]
        assertTrue("Node ID should be negative", nodeStation.id < 0)
        assertEquals("Лукойл", nodeStation.name)
        assertEquals("Лукойл", nodeStation.brand)
        assertEquals("проспект Ленина, 25", nodeStation.address)
        assertEquals(55.16, nodeStation.latitude, 0.0001)
        assertEquals(61.40, nodeStation.longitude, 0.0001)
        assertTrue(nodeStation.dataSources.contains(FuelDataSource.OVERPASS))

        // Check fuel prices for "no data" status
        assertEquals(5, nodeStation.fuelTypes.size)
        assertTrue(nodeStation.fuelTypes.all { it.price == 0.0 && !it.available && it.source == FuelDataSource.OVERPASS })

        // Way station
        val wayStation = stations[1]
        assertTrue("Way ID should be negative", wayStation.id < 0)
        assertEquals("Газпромнефть", wayStation.name)
        assertEquals("Газпромнефть", wayStation.brand)
        assertEquals("улица Свободы", wayStation.address)
        assertEquals(55.18, wayStation.latitude, 0.0001)
        assertEquals(61.42, wayStation.longitude, 0.0001)

        // Relation station without name/brand/address tags
        val relStation = stations[2]
        assertTrue("Relation ID should be negative", relStation.id < 0)
        assertEquals("АЗС (без названия)", relStation.name)
        assertEquals("Прочие", relStation.brand)
        assertEquals("Окрестности OSM", relStation.address)
    }

    @Test
    fun `fetchStations falls back to second mirror when first mirror fails`() = org.junit.Assert.assertNotNull {
        kotlinx.coroutines.runBlocking {
            val mockClient = OkHttpClient.Builder().addInterceptor { chain ->
                val url = chain.request().url.toString()
                if (url.contains("overpass-api.de")) {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(500)
                        .message("Internal Server Error")
                        .body("Error".toResponseBody("text/plain".toMediaType()))
                        .build()
                } else if (url.contains("overpass.kumi.systems")) {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""
                            {
                              "elements": [
                                {
                                  "type": "node",
                                  "id": 8888,
                                  "lat": 55.15,
                                  "lon": 61.40,
                                  "tags": {
                                    "amenity": "fuel",
                                    "name": "Kumi Gas Station"
                                  }
                                }
                              ]
                            }
                        """.trimIndent().toResponseBody("application/json".toMediaType()))
                        .build()
                } else {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("".toResponseBody("text/plain".toMediaType()))
                        .build()
                }
            }.build()

            val providerWithMirrors = OverpassFuelProviderImpl(mockClient)
            val stations = providerWithMirrors.fetchStations(55.15, 61.40, 5000.0)

            assertEquals(1, stations.size)
            assertEquals("Kumi Gas Station", stations[0].name)
            assertEquals("Kumi Gas Station", stations[0].brand)
        }
    }

    @Test
    fun `parseOverpassResponse preserves station without brand tag using name as brand`() {
        val sampleJson = """
            {
              "elements": [
                {
                  "type": "node",
                  "id": 5555,
                  "lat": 55.16,
                  "lon": 61.40,
                  "tags": {
                    "amenity": "fuel",
                    "name": "Газпромнефть"
                  }
                }
              ]
            }
        """.trimIndent()

        val stations = provider.parseOverpassResponse(sampleJson)
        assertEquals(1, stations.size)
        assertEquals("Газпромнефть", stations[0].name)
        assertEquals("Газпромнефть", stations[0].brand)
    }

    @Test
    fun `parseOverpassResponse returns empty list for invalid or empty JSON`() {
        val emptyJson = "{\"elements\": []}"
        val invalidJson = "invalid json string"

        assertTrue(provider.parseOverpassResponse(emptyJson).isEmpty())
        assertTrue(provider.parseOverpassResponse(invalidJson).isEmpty())
    }

    @Test
    fun `parseOverpassResponse generates deterministic unique negative IDs`() {
        val json = """
            {
              "elements": [
                {
                  "type": "node",
                  "id": 100,
                  "lat": 55.0,
                  "lon": 61.0,
                  "tags": { "name": "Test" }
                }
              ]
            }
        """.trimIndent()

        val list1 = provider.parseOverpassResponse(json)
        val list2 = provider.parseOverpassResponse(json)

        assertEquals(1, list1.size)
        assertEquals(1, list2.size)
        assertEquals(list1[0].id, list2[0].id)
        assertTrue(list1[0].id < 0)
    }

    @Test
    fun `overpass_persisted_cache_survives_restart_and_skips_network_when_fresh`() = org.junit.Assert.assertNotNull {
        kotlinx.coroutines.runBlocking {
            val context = org.robolectric.RuntimeEnvironment.getApplication()
            var networkCallCount = 0
            val mockClient = OkHttpClient.Builder().addInterceptor { chain ->
                networkCallCount++
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""
                        {
                          "elements": [
                            {
                              "type": "node",
                              "id": 7777,
                              "lat": 55.15,
                              "lon": 61.40,
                              "tags": { "name": "Persisted Station" }
                            }
                          ]
                        }
                    """.trimIndent().toResponseBody("application/json".toMediaType()))
                    .build()
            }.build()

            // First provider instance fetches from network and persists to disk
            val provider1 = OverpassFuelProviderImpl(mockClient, context)
            val stations1 = provider1.fetchStations(55.15, 61.40, 5000.0)
            assertEquals(1, networkCallCount)
            assertEquals(1, stations1.size)
            assertEquals("Persisted Station", stations1[0].name)

            // Second provider instance (simulating app restart) reads from disk cache without hitting network
            val failingClient = OkHttpClient.Builder().addInterceptor {
                throw java.io.IOException("Network should not be called when disk cache is fresh")
            }.build()

            val provider2 = OverpassFuelProviderImpl(failingClient, context)
            val stations2 = provider2.fetchStations(55.15, 61.40, 5000.0)

            assertEquals("Should receive 1 station from persisted disk cache", 1, stations2.size)
            assertEquals("Persisted Station", stations2[0].name)
            assertEquals("Station IDs should match across restart", stations1[0].id, stations2[0].id)
        }
    }

    @Test
    fun `enrichment_budget_times_out_after_6_seconds_and_retains_cached_data`() = org.junit.Assert.assertNotNull {
        kotlinx.coroutines.runBlocking {
            val context = org.robolectric.RuntimeEnvironment.getApplication()

            // Pre-seed disk cache with an expired item
            val setupClient = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""
                        {
                          "elements": [
                            {
                              "type": "node",
                              "id": 1234,
                              "lat": 55.15,
                              "lon": 61.40,
                              "tags": { "name": "Initial Cache Station" }
                            }
                          ]
                        }
                    """.trimIndent().toResponseBody("application/json".toMediaType()))
                    .build()
            }.build()

            val initialProvider = OverpassFuelProviderImpl(setupClient, context)
            initialProvider.fetchStations(55.15, 61.40, 5000.0)

            // Modify file timestamp on disk to simulate expired (>24h) cache
            val cacheFile = java.io.File(context.filesDir, "overpass_cache.json")
            assertTrue("Cache file should exist", cacheFile.exists())
            val oldContent = cacheFile.readText().replace(Regex("\"timestamp\":\\d+"), "\"timestamp\":1000")
            cacheFile.writeText(oldContent)

            // Setup slow client that delays beyond mirror timeouts (e.g. 10s delay per request)
            val slowClient = OkHttpClient.Builder().addInterceptor { chain ->
                Thread.sleep(10_000L)
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }.build()

            val providerWithSlowMirrors = OverpassFuelProviderImpl(slowClient, context)
            val startMs = System.currentTimeMillis()
            val result = providerWithSlowMirrors.fetchStations(55.15, 61.40, 5000.0)
            val durationMs = System.currentTimeMillis() - startMs

            assertTrue("Enrichment should terminate within ~6.5 seconds total budget", durationMs <= 7000L)
            assertEquals("Should fallback to expired cached station when enrichment times out", 1, result.size)
            assertEquals("Initial Cache Station", result[0].name)
        }
    }

    @Test
    fun `fetchStations does not hit network when cache is younger than TTL`() = org.junit.Assert.assertNotNull {
        kotlinx.coroutines.runBlocking {
            var networkCallCount = 0
            val mockClient = OkHttpClient.Builder().addInterceptor { chain ->
                networkCallCount++
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""
                        {
                          "elements": [
                            {
                              "type": "node",
                              "id": 9999,
                              "lat": 55.15,
                              "lon": 61.40,
                              "tags": { "name": "Cached Station" }
                            }
                          ]
                        }
                    """.trimIndent().toResponseBody("application/json".toMediaType()))
                    .build()
            }.build()

            val providerWithCache = OverpassFuelProviderImpl(mockClient)
            val firstFetch = providerWithCache.fetchStations(55.15, 61.40, 5000.0)
            val secondFetch = providerWithCache.fetchStations(55.15, 61.40, 5000.0)

            assertEquals(1, networkCallCount)
            assertEquals(1, firstFetch.size)
            assertEquals(1, secondFetch.size)
            assertEquals("Cached Station", firstFetch[0].name)
            assertEquals(firstFetch[0].id, secondFetch[0].id)
        }
    }
}
