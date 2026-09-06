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
    fun parseHtml_withReal2026FixtureText_returnsCorrectStatusesFor201And260() {
        val fixtureText = """
            Газпромнефть №201
            Челябинск, Свердловский тракт, 12В (Авторынок на ЧМЗ, поворот на Радонежская)
            Очередь
            Аи-92 / 61.05р. / Лимит до 40 л. / Доступно
            Аи-95 / 66.54р. / Лимит до 40 л. / Отсутствует
            ДТ / 79.03р. / Лимит до 40 л. / Доступно
            ---
            Газпромнефть №260
            Челябинск, Курчатова, 2/1
            АЗС закрыта
            Топлива нет, АЗС не работает
        """.trimIndent()

        // Test #201 for AI-95
        val obs201_95 = RussiabaseHtmlParser.parseHtml(fixtureText, "ai95")
        val station201_95 = obs201_95.find { it.brand.contains("201") }
        assertNotNull("Station 201 observation for AI-95 should not be null", station201_95)
        assertFalse("Station 201 AI-95 should be unavailable (NO_FUEL)", station201_95!!.available)

        // Test #201 for AI-92
        val obs201_92 = RussiabaseHtmlParser.parseHtml(fixtureText, "ai92")
        val station201_92 = obs201_92.find { it.brand.contains("201") }
        assertNotNull("Station 201 observation for AI-92 should not be null", station201_92)
        assertTrue("Station 201 AI-92 should be available", station201_92!!.available)
        assertEquals(61.05, station201_92.price, 0.001)

        // Test #260 (closed)
        val obs260 = RussiabaseHtmlParser.parseHtml(fixtureText, "ai95")
        val station260 = obs260.find { it.brand.contains("260") }
        assertNotNull("Station 260 observation should not be null", station260)
        assertFalse("Station 260 (closed) should be unavailable for all fuels", station260!!.available)
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
