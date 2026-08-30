package com.navrot.aifuelassistant.data.datasource

import com.navrot.aifuelassistant.data.model.FuelDataSource
import okhttp3.OkHttpClient
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
        assertEquals("АЗС", relStation.brand)
        assertEquals("Окрестности OSM", relStation.address)
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
}
