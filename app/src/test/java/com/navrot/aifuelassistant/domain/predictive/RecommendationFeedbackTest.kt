package com.navrot.aifuelassistant.domain.predictive

import com.navrot.aifuelassistant.domain.personal.CalculatePersonalStationProfileUseCase
import com.navrot.aifuelassistant.domain.personal.PersonalFuelEvent
import com.navrot.aifuelassistant.domain.predictive.usecase.EvaluateRecommendationOutcomeUseCase
import com.navrot.aifuelassistant.domain.predictive.usecase.PersonalStationPreferenceUseCase
import com.navrot.aifuelassistant.domain.predictive.usecase.RecordRecommendationFeedbackUseCase
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class RecommendationFeedbackTest {

    private val recordFeedbackUseCase = RecordRecommendationFeedbackUseCase()
    private val evaluateOutcomeUseCase = EvaluateRecommendationOutcomeUseCase()
    private val stationPreferenceUseCase = PersonalStationPreferenceUseCase()
    private val stationProfileUseCase = CalculatePersonalStationProfileUseCase()

    @Test
    fun testUserActionsAndOutcomesMapping() {
        val viewedFeedback = recordFeedbackUseCase.createFeedback(
            recommendationId = "rec-1",
            recommendedStationId = 101L,
            chosenStationId = 101L,
            action = UserAction.VIEWED,
            outcome = UserOutcome.UNKNOWN
        )
        assertEquals(UserAction.VIEWED, viewedFeedback.action)
        assertEquals(UserOutcome.UNKNOWN, viewedFeedback.outcome)
        assertFalse(viewedFeedback.userConfirmed)

        val routeFeedback = recordFeedbackUseCase.createFeedback(
            recommendationId = "rec-2",
            recommendedStationId = 101L,
            chosenStationId = 101L,
            routeStarted = true,
            action = UserAction.ROUTE_STARTED,
            outcome = UserOutcome.UNKNOWN
        )
        assertEquals(UserAction.ROUTE_STARTED, routeFeedback.action)
        assertTrue(routeFeedback.routeStarted)

        val refuelFeedback = recordFeedbackUseCase.createFeedback(
            recommendationId = "rec-3",
            recommendedStationId = 101L,
            chosenStationId = 101L,
            action = UserAction.REFUELLED,
            outcome = UserOutcome.SUCCESS,
            actualPrice = 68.50,
            userConfirmed = true
        )
        assertEquals(UserAction.REFUELLED, refuelFeedback.action)
        assertEquals(UserOutcome.SUCCESS, refuelFeedback.outcome)
        assertTrue(refuelFeedback.userConfirmed)
        assertEquals(68.50, refuelFeedback.actualPrice!!, 0.001)
    }

    @Test
    fun testPredictionEvaluation_priceAndAvailability() {
        val feedbackCorrectPrice = recordFeedbackUseCase.createFeedback(
            recommendationId = "rec-p1",
            recommendedStationId = 101L,
            chosenStationId = 101L,
            action = UserAction.REFUELLED,
            outcome = UserOutcome.SUCCESS,
            predictedAvailability = FuelAvailabilityStatus.AVAILABLE,
            actualAvailability = FuelAvailabilityStatus.AVAILABLE,
            predictedPrice = 68.00,
            actualPrice = 68.50,
            userConfirmed = true
        )

        val outcome = evaluateOutcomeUseCase.execute(feedbackCorrectPrice)
        assertEquals(true, outcome.availabilityCorrect)
        assertNotNull(outcome.priceError)
        assertEquals(0.50, outcome.priceError!!, 0.001)
        assertEquals(0.50, outcome.absolutePriceError!!, 0.001)
        assertTrue(outcome.isConfirmedFact)
    }

    @Test
    fun testUnknownSafety_noErrorForUnknowns() {
        val unknownFeedback = recordFeedbackUseCase.createFeedback(
            recommendationId = "rec-unk",
            recommendedStationId = 101L,
            chosenStationId = 101L,
            action = UserAction.SKIPPED,
            outcome = UserOutcome.UNKNOWN,
            predictedAvailability = FuelAvailabilityStatus.UNKNOWN,
            actualAvailability = FuelAvailabilityStatus.UNKNOWN,
            predictedPrice = null,
            actualPrice = null
        )

        val outcome = evaluateOutcomeUseCase.execute(unknownFeedback)
        assertNull(outcome.availabilityCorrect)
        assertNull(outcome.priceError)
        assertNull(outcome.absolutePriceError)
        assertFalse(outcome.isConfirmedFact)
    }

    @Test
    fun testFakeLearningPrevention_viewedAndSkippedDoNotUpdateLearning() {
        val unconfirmedFeedbacks = listOf(
            recordFeedbackUseCase.createFeedback(
                recommendationId = "f-1",
                recommendedStationId = 101L,
                chosenStationId = 101L,
                action = UserAction.VIEWED,
                outcome = UserOutcome.UNKNOWN
            ),
            recordFeedbackUseCase.createFeedback(
                recommendationId = "f-2",
                recommendedStationId = 101L,
                chosenStationId = 101L,
                action = UserAction.ROUTE_STARTED,
                outcome = UserOutcome.UNKNOWN
            ),
            recordFeedbackUseCase.createFeedback(
                recommendationId = "f-3",
                recommendedStationId = 101L,
                chosenStationId = 101L,
                action = UserAction.SKIPPED,
                outcome = UserOutcome.UNKNOWN
            )
        )

        val learning = stationPreferenceUseCase.getStationLearning(
            stationId = 101,
            events = emptyList(),
            feedbacks = unconfirmedFeedbacks
        )

        assertEquals(0, learning.successfulRefuelCount)
        assertEquals(0, learning.failedRefuelCount)
        assertEquals(0.0, learning.personalPreferenceScore, 0.001)
    }

    @Test
    fun testConfirmedFeedback_updatesPersonalLearningAndProfile() {
        val confirmedFeedbacks = listOf(
            recordFeedbackUseCase.createFeedback(
                recommendationId = "f-c1",
                recommendedStationId = 101L,
                chosenStationId = 101L,
                action = UserAction.REFUELLED,
                outcome = UserOutcome.SUCCESS,
                predictedPrice = 68.00,
                actualPrice = 68.00,
                userConfirmed = true
            ),
            recordFeedbackUseCase.createFeedback(
                recommendationId = "f-c2",
                recommendedStationId = 101L,
                chosenStationId = 101L,
                action = UserAction.REFUELLED,
                outcome = UserOutcome.SUCCESS,
                predictedPrice = 68.00,
                actualPrice = 68.50,
                userConfirmed = true
            )
        )

        val learning = stationPreferenceUseCase.getStationLearning(
            stationId = 101,
            events = emptyList(),
            feedbacks = confirmedFeedbacks
        )

        assertEquals(2, learning.successfulRefuelCount)
        assertEquals(0, learning.failedRefuelCount)
        assertTrue(learning.personalPreferenceScore > 0.0)

        val profile = stationProfileUseCase.execute(
            stationId = 101,
            events = emptyList(),
            feedbacks = confirmedFeedbacks
        )

        assertEquals(2, profile.successfulRefuelCount)
        assertEquals(0, profile.failedRefuelCount)
        assertNotNull(profile.averagePaidPrice)
        assertEquals(68.25, profile.averagePaidPrice!!, 0.001)
    }

    @Test
    fun testNegativeFeedback_lowersPersonalScoreWithoutAlteringD1() {
        val negativeFeedbacks = listOf(
            recordFeedbackUseCase.createFeedback(
                recommendationId = "f-neg",
                recommendedStationId = 101L,
                chosenStationId = 101L,
                action = UserAction.ARRIVED,
                outcome = UserOutcome.FAILED,
                actualAvailability = FuelAvailabilityStatus.UNAVAILABLE,
                userConfirmed = true
            )
        )

        val learning = stationPreferenceUseCase.getStationLearning(
            stationId = 101,
            events = emptyList(),
            feedbacks = negativeFeedbacks
        )

        assertEquals(0, learning.successfulRefuelCount)
        assertEquals(1, learning.failedRefuelCount)
        assertTrue("Negative feedback must lower personal score", learning.personalPreferenceScore < 0.0)
    }

    @Test
    fun testDeterminism_sameFeedbacksProduceIdenticalLearningState() {
        val feedback1 = recordFeedbackUseCase.createFeedback(
            recommendationId = "rec-det",
            recommendedStationId = 202L,
            chosenStationId = 202L,
            action = UserAction.REFUELLED,
            outcome = UserOutcome.SUCCESS,
            userConfirmed = true
        )

        val learningA = stationPreferenceUseCase.getStationLearning(202, emptyList(), listOf(feedback1))
        val learningB = stationPreferenceUseCase.getStationLearning(202, emptyList(), listOf(feedback1))

        assertEquals(learningA, learningB)
    }
}
