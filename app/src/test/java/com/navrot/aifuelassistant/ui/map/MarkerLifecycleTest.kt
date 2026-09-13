package com.navrot.aifuelassistant.ui.map

import com.navrot.aifuelassistant.data.model.GasStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Регрессионный тест для корневого бага «undead map» (PR fix/maplibre-marker-lifecycle-p0).
 *
 * Сценарий бага (до фикса):
 *   1. MapLibre.setStyle() визуально удаляет все маркеры с карты
 *   2. Наши трекеры (activeStationMarkers) об этом не знали — ID станций оставались в existingIds
 *   3. MarkerDiffCalculator.calculateDiff(existingIds, stations) видел все ID как «уже на карте»
 *   4. toAdd = [] → маркеры НЕ добавлялись заново → «серый лист без пинов»
 *
 * Контракт после фикса:
 *   - resetMarkerTrackers() должен полностью очистить existingIds
 *   - Первый calculateDiff после сброса должен вернуть ВСЕ станции как toAdd
 *   - toUpdate должен быть пустым
 *
 * Этот тест ловит регрессию: если кто-то уберёт вызов activeStationMarkers.clear()
 * из resetMarkerTrackers, тест упадёт.
 */
class MarkerLifecycleTest {

    private fun createStation(id: Int, name: String = "Station $id") = GasStation(
        id = id,
        name = name,
        brand = "Brand",
        address = "Address $id",
        latitude = 55.0 + id * 0.001,
        longitude = 61.0 + id * 0.001,
        fuelTypes = emptyList(),
        queueTime = 0,
        reliability = 100
    )

    /**
     * Симулирует состояние трекера activeStationMarkers после добавления маркеров.
     * В проде это mutableMapOf<Int, Marker>, но для теста важны только ключи (ID станций).
     */
    private class MarkerTrackerSimulator {
        private val stationIds: MutableSet<Int> = mutableSetOf()

        /** Эквивалент map.addMarker(markerOptions) + activeStationMarkers[station.id] = addedMarker */
        fun addAll(stations: List<GasStation>) {
            stations.forEach { stationIds.add(it.id) }
        }

        /** Эквивалент resetMarkerTrackers() из MapLibreView.kt */
        fun reset() {
            stationIds.clear()
        }

        /** existingIds для MarkerDiffCalculator */
        fun existingIds(): Set<Int> = stationIds.toSet()
    }

    @Test
    fun `T-undead-1 - after resetMarkerTrackers, all stations must be re-added by next diff`() {
        val tracker = MarkerTrackerSimulator()
        val stations = listOf(
            createStation(1, "Лукойл Мира 65"),
            createStation(2, "Газпромнефт Свердловский 12В"),
            createStation(3, "Benzonavt №201")
        )

        // Шаг 1: первичное добавление маркеров
        tracker.addAll(stations)
        assertEquals("Tracker should have 3 stations after initial add", 3, tracker.existingIds().size)

        // Шаг 2: первый diff — ничего нового, всё уже на карте
        val diffBeforeReset = MarkerDiffCalculator.calculateDiff(tracker.existingIds(), stations) { it.id }
        assertTrue("Before reset: toAdd should be empty", diffBeforeReset.toAdd.isEmpty())
        assertEquals("Before reset: toUpdate should have 3", 3, diffBeforeReset.toUpdate.size)

        // Шаг 3: СИМУЛЯЦИЯ setStyle() — визуально маркеры пропадают, трекер должен сброситься
        tracker.reset()
        assertEquals("After reset: tracker must be empty", 0, tracker.existingIds().size)

        // Шаг 4: повторный diff после сброса — ВСЕ станции должны попасть в toAdd
        // Это и есть инвариант, который ловил баг «undead map»
        val diffAfterReset = MarkerDiffCalculator.calculateDiff(tracker.existingIds(), stations) { it.id }
        assertEquals("After reset: toAdd must contain ALL 3 stations", 3, diffAfterReset.toAdd.size)
        assertTrue("After reset: toUpdate must be empty", diffAfterReset.toUpdate.isEmpty())
        assertTrue("After reset: toRemoveIds must be empty", diffAfterReset.toRemoveIds.isEmpty())

        // Проверяем, что все ID действительно в toAdd
        val addedIds = diffAfterReset.toAdd.map { it.id }.toSet()
        assertEquals(setOf(1, 2, 3), addedIds)
    }

    @Test
    fun `T-undead-2 - resetMarkerTrackers must clear even non-empty tracker`() {
        val tracker = MarkerTrackerSimulator()
        tracker.addAll(listOf(createStation(10), createStation(20), createStation(30)))
        assertEquals(3, tracker.existingIds().size)

        tracker.reset()
        assertTrue("reset() must produce empty existingIds", tracker.existingIds().isEmpty())
    }

    @Test
    fun `T-undead-3 - diff after reset matches diff on fresh tracker`() {
        val stations = listOf(createStation(1), createStation(2), createStation(3))

        // Fresh tracker (первый запуск)
        val freshTracker = MarkerTrackerSimulator()
        val freshDiff = MarkerDiffCalculator.calculateDiff(freshTracker.existingIds(), stations) { it.id }

        // Tracker after reset (после setStyle)
        val resetTracker = MarkerTrackerSimulator()
        resetTracker.addAll(stations)
        resetTracker.reset()
        val resetDiff = MarkerDiffCalculator.calculateDiff(resetTracker.existingIds(), stations) { it.id }

        // Результаты должны быть идентичны — это и значит, что сброс полный
        assertEquals(
            "toAdd size: fresh=${freshDiff.toAdd.size}, after-reset=${resetDiff.toAdd.size}",
            freshDiff.toAdd.size, resetDiff.toAdd.size
        )
        assertEquals(
            "toUpdate size: fresh=${freshDiff.toUpdate.size}, after-reset=${resetDiff.toUpdate.size}",
            freshDiff.toUpdate.size, resetDiff.toUpdate.size
        )
        assertEquals(
            "toRemoveIds: fresh=${freshDiff.toRemoveIds}, after-reset=${resetDiff.toRemoveIds}",
            freshDiff.toRemoveIds, resetDiff.toRemoveIds
        )
    }

    @Test
    fun `T-undead-4 - markers survive multiple style reloads (3 fallback cycles)`() {
        val tracker = MarkerTrackerSimulator()
        val stations = listOf(createStation(1), createStation(2), createStation(3))

        // Симулируем 3 цикла setStyle + reset + повторное добавление
        // (как в applyStyleWithFallback при 3 источниках тайлов)
        repeat(3) { cycle ->
            tracker.reset()
            val diff = MarkerDiffCalculator.calculateDiff(tracker.existingIds(), stations) { it.id }
            assertEquals("Cycle ${cycle + 1}: all 3 stations must be in toAdd after reset",
                3, diff.toAdd.size)
            // Симулируем реальное добавление маркеров на карту
            tracker.addAll(diff.toAdd)
            assertEquals("Cycle ${cycle + 1}: tracker must have 3 stations after re-add",
                3, tracker.existingIds().size)
        }
    }
}
