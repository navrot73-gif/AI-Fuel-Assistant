package com.navrot.aifuelassistant.data.datasource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StationJsonParserTest {

    private lateinit var parser: StationJsonParser

    @Before
    fun setUp() {
        parser = StationJsonParserImpl()
    }

    @Test
    fun `parseJson parses valid JSON station list`() {
        val json = """
            [
              {
                "id": 1,
                "name": "Lukoil Station",
                "brand": "Lukoil",
                "address": "Main St 1",
                "latitude": 55.75,
                "longitude": 37.61,
                "fuelTypes": [
                  {"type": "AI-95", "price": 55.0, "available": true}
                ],
                "queueTime": 5,
                "reliability": 90,
                "monumentPhotoUrl": "http://example.com/photo.jpg",
                "entrancePhotoUrl": "http://example.com/entrance.jpg"
              }
            ]
        """.trimIndent()

        val stations = parser.parseJson(json)
        assertEquals(1, stations.size)
        val station = stations.first()
        assertEquals(1, station.id)
        assertEquals("Lukoil Station", station.name)
        assertEquals("Lukoil", station.brand)
        assertEquals("Main St 1", station.address)
        assertEquals(55.75, station.latitude, 0.001)
        assertEquals(37.61, station.longitude, 0.001)
        assertEquals(1, station.fuelTypes.size)
        assertEquals("AI-95", station.fuelTypes.first().type)
        assertEquals(55.0, station.fuelTypes.first().price, 0.001)
        assertTrue(station.fuelTypes.first().available)
        assertEquals(5, station.queueTime)
        assertEquals(90, station.reliability)
        assertEquals("http://example.com/photo.jpg", station.monumentPhotoUrl)
        assertEquals("http://example.com/entrance.jpg", station.entrancePhotoUrl)
    }

    @Test
    fun `parseJson filters blacklisted names`() {
        val json = """
            [
              {"id": 1, "name": "Get Petrol #1", "brand": "X", "address": "A", "latitude": 0.0, "longitude": 0.0, "fuelTypes": [], "queueTime": 0, "reliability": 100},
              {"id": 2, "name": "Best Price Station", "brand": "X", "address": "A", "latitude": 0.0, "longitude": 0.0, "fuelTypes": [], "queueTime": 0, "reliability": 100},
              {"id": 3, "name": "Test Station 123", "brand": "X", "address": "A", "latitude": 0.0, "longitude": 0.0, "fuelTypes": [], "queueTime": 0, "reliability": 100},
              {"id": 4, "name": "Rosneft #5", "brand": "Rosneft", "address": "A", "latitude": 0.0, "longitude": 0.0, "fuelTypes": [], "queueTime": 0, "reliability": 100}
            ]
        """.trimIndent()

        val stations = parser.parseJson(json)
        assertEquals(1, stations.size)
        assertEquals(4, stations.first().id)
        assertEquals("Rosneft #5", stations.first().name)
    }
}
