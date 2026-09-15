package com.navrot.aifuelassistant.domain.predictive

import com.navrot.aifuelassistant.domain.personal.PersonalFuelEvent
import com.navrot.aifuelassistant.domain.predictive.usecase.PersonalStationPreferenceUseCase
import com.navrot.aifuelassistant.domain.predictive.usecase.RecordRecommendationFeedbackUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalStationPreferenceTest {

    private val preferenceUseCase = PersonalStationPreferenceUseCase()
    private val feedbackUseCase = RecordRecommendationFeedbackUseCase()

    @Test
    fun getStationLearning_withRepeatedVisitsAndAcceptedFeedbacks_computesPreferenceScore() {
        val events = listOf(
            PersonalFuelEvent(vehicleId = 1L, timestamp = 1000, stationId = 101, fuelType = "АИ-95", pricePerLiter = 60.0),
            PersonalFuelEvent(vehicleId = 1L, timestamp = 2000, stationId = 101, fuelType = "АИ-95", pricePerLiter = 60.0),
            PersonalFuelEvent(vehicleId = 1L, timestamp = 3000, stationId = 101, fuelType = "АИ-95", pricePerLiter = 60.0)
        )

        val feedbacks = listOf(
            feedbackUseCase.createFeedback(
                recommendationId = "rec-1",
                recommendedStationId = 101L,
                chosenStationId = 101L,
                refuelCompleted = true
            )
        )

        val learning = preferenceUseCase.getStationLearning(stationId = 101, events = events, feedbacks = feedbacks)

        assertEquals(3, learning.visitsCount)
        assertEquals(3, learning.refuelsCount)
        assertEquals(1, learning.chosenAfterRecommendationCount)
        assertEquals(2, learning.repeatVisitsCount)
        assertTrue(learning.personalPreferenceScore > 0.0)
    }

    @Test
    fun feedbackUseCase_noAction_createsUnknownSignal() {
        val feedback = feedbackUseCase.createFeedback(
            recommendationId = "rec-2",
            recommendedStationId = 101L,
            chosenStationId = null // User took no action
        )

        assertEquals(FeedbackSignal.UNKNOWN, feedback.signal)
    }
}
