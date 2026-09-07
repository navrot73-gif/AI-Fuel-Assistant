package com.navrot.aifuelassistant.data.datasource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RussiabaseHtmlParserTest {

    @Test
    fun parseHtml_withFixture_returnsCorrectObservations() {
        val fixtureStream = javaClass.classLoader?.getResourceAsStream("russiabase_fixture.html")
            ?: error("russiabase_fixture.html resource not found")
        val html = fixtureStream.bufferedReader().use { it.readText() }

        val observations = RussiabaseHtmlParser.parseHtml(html, "ai95")

        assertEquals(3, observations.size)

        // 1. Газпромнефть — Отсутствует
        val obs1 = observations[0]
        assertEquals("Газпромнефть", obs1.brand)
        assertEquals("Свердловский тракт, 12в", obs1.address)
        assertEquals("АИ-95", obs1.fuelType)
        assertFalse(obs1.available)
        assertEquals(0.0, obs1.price, 0.001)
        assertNull(obs1.limitNote)
        assertEquals("Отсутствует", obs1.statusText)

        // 2. Башнефть — Лимит до 30 л
        val obs2 = observations[1]
        assertEquals("Башнефть", obs2.brand)
        assertEquals("ул. Труда, 15", obs2.address)
        assertEquals("АИ-95", obs2.fuelType)
        assertTrue(obs2.available)
        assertEquals(53.50, obs2.price, 0.001)
        assertNotNull(obs2.limitNote)
        assertTrue(obs2.limitNote!!.contains("Лимит до 30 л"))

        // 3. Лукойл — В наличии
        val obs3 = observations[2]
        assertEquals("Лукойл", obs3.brand)
        assertEquals("пр. Победы, 300", obs3.address)
        assertEquals("АИ-95", obs3.fuelType)
        assertTrue(obs3.available)
        assertEquals(56.90, obs3.price, 0.001)
        assertNull(obs3.limitNote)
    }

    @Test
    fun parseHtml_emptyOrInvalid_returnsEmptyList() {
        val result = RussiabaseHtmlParser.parseHtml("", "ai95")
        assertTrue(result.isEmpty())
    }

    @Test
    fun mapMarkToFuelType_mapsCorrectly() {
        assertEquals("АИ-92", RussiabaseHtmlParser.mapMarkToFuelType("ai92"))
        assertEquals("АИ-95", RussiabaseHtmlParser.mapMarkToFuelType("ai95"))
        assertEquals("АИ-98", RussiabaseHtmlParser.mapMarkToFuelType("ai98"))
        assertEquals("АИ-100", RussiabaseHtmlParser.mapMarkToFuelType("ai100"))
        assertEquals("ДТ", RussiabaseHtmlParser.mapMarkToFuelType("dt"))
    }
}
