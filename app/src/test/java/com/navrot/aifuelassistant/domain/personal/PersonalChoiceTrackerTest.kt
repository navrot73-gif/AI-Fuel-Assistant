package com.navrot.aifuelassistant.domain.personal

import org.junit.Assert.assertEquals
import org.junit.Test

class PersonalChoiceTrackerTest {

    @Test
    fun `recordChoice correctly logs user choice without modifying original recommendation engine`() {
        val tracker = PersonalChoiceTracker()
        tracker.recordChoice(recommendedStationId = 1, chosenStationId = 2)

        val choices = tracker.getChoices()
        assertEquals(1, choices.size)
        assertEquals(1, choices.first().recommendedStationId)
        assertEquals(2, choices.first().chosenStationId)
    }
}
