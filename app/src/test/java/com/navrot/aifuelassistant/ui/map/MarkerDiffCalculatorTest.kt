package com.navrot.aifuelassistant.ui.map

import com.navrot.aifuelassistant.data.model.GasStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerDiffCalculatorTest {

    private fun createStation(id: Int, name: String) = GasStation(
        id = id,
        name = name,
        brand = "Brand",
        address = "Address",
        latitude = 55.0,
        longitude = 61.0,
        fuelTypes = emptyList(),
        queueTime = 0,
        reliability = 100
    )

    @Test
    fun testMarkerDiff_addUpdateRemoveSetsCalculatedCorrectly() {
        val existingIds = setOf(1, 2, 3)
        val newStations = listOf(
            createStation(2, "Station 2 Updated"),
            createStation(3, "Station 3 Updated"),
            createStation(4, "Station 4 New")
        )

        val diff = MarkerDiffCalculator.calculateDiff(existingIds, newStations) { it.id }

        // Station 1 was removed
        assertEquals(setOf(1), diff.toRemoveIds)

        // Station 4 was added
        assertEquals(1, diff.toAdd.size)
        assertEquals(4, diff.toAdd[0].id)

        // Stations 2 and 3 were updated
        assertEquals(2, diff.toUpdate.size)
        assertEquals(setOf(2, 3), diff.toUpdate.map { it.id }.toSet())
    }

    @Test
    fun testMarkerDiff_emptyExisting() {
        val existingIds = emptySet<Int>()
        val newStations = listOf(
            createStation(1, "Station 1"),
            createStation(2, "Station 2")
        )

        val diff = MarkerDiffCalculator.calculateDiff(existingIds, newStations) { it.id }

        assertTrue(diff.toRemoveIds.isEmpty())
        assertEquals(2, diff.toAdd.size)
        assertTrue(diff.toUpdate.isEmpty())
    }

    @Test
    fun testMarkerDiff_emptyNewList() {
        val existingIds = setOf(10, 20)
        val newStations = emptyList<GasStation>()

        val diff = MarkerDiffCalculator.calculateDiff(existingIds, newStations) { it.id }

        assertEquals(setOf(10, 20), diff.toRemoveIds)
        assertTrue(diff.toAdd.isEmpty())
        assertTrue(diff.toUpdate.isEmpty())
    }
}
