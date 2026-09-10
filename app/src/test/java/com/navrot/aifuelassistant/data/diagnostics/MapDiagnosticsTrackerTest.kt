package com.navrot.aifuelassistant.data.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MapDiagnosticsTrackerTest {

    @Before
    fun setUp() {
        MapDiagnosticsTracker.resetStartupTimings()
    }

    @Test
    fun `verify default values after reset`() {
        assertEquals(0, MapDiagnosticsTracker.registryCount)
        assertTrue(MapDiagnosticsTracker.perSourceCount.isEmpty())
        assertEquals(0, MapDiagnosticsTracker.mergeConflicts)
        assertEquals(0L, MapDiagnosticsTracker.firstEmitMs)
        assertEquals("cache", MapDiagnosticsTracker.firstEmitSource)
    }

    @Test
    fun `verify setting counters and plain text export`() {
        MapDiagnosticsTracker.registryCount = 120
        MapDiagnosticsTracker.perSourceCount = mapOf(
            "overpass" to 5,
            "benzonavt" to 110,
            "russiabase" to 15,
            "user" to 2
        )
        MapDiagnosticsTracker.mergeConflicts = 3
        MapDiagnosticsTracker.firstEmitMs = 45L
        MapDiagnosticsTracker.firstEmitSource = "assets"

        assertEquals(120, MapDiagnosticsTracker.registryCount)
        assertEquals(5, MapDiagnosticsTracker.perSourceCount["overpass"])
        assertEquals(3, MapDiagnosticsTracker.mergeConflicts)
        assertEquals(45L, MapDiagnosticsTracker.firstEmitMs)
        assertEquals("assets", MapDiagnosticsTracker.firstEmitSource)

        val exportedText = MapDiagnosticsTracker.exportDiagnosticsText()
        assertTrue(exportedText.contains("=== MAP DIAGNOSTICS BASELINE ==="))
        assertTrue(exportedText.contains("registryCount: 120"))
        assertTrue(exportedText.contains("overpass: 5"))
        assertTrue(exportedText.contains("benzonavt: 110"))
        assertTrue(exportedText.contains("mergeConflicts: 3"))
        assertTrue(exportedText.contains("firstEmitMs: 45ms"))
        assertTrue(exportedText.contains("firstEmitSource: assets"))
    }
}
