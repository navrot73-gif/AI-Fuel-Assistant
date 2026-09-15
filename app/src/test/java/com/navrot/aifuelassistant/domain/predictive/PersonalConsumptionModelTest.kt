package com.navrot.aifuelassistant.domain.predictive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalConsumptionModelTest {

    @Test
    fun calculateBaseline_insufficientSamples_returnsUnknownConfidence() {
        val calc = PersonalConsumptionModel.calculateBaseline(listOf(8.2, 8.4))
        assertNull(calc.baselineConsumption)
        assertEquals(PredictionConfidence.UNKNOWN, calc.confidence)
        assertEquals(2, calc.validCount)
    }

    @Test
    fun filterOutliers_identifiesExtremeAnomaliesWithoutDeletingRawData() {
        // Raw values: 8.1, 8.4, 8.2, 8.3, 22.7, 8.0
        val consumptions = listOf(8.1, 8.4, 8.2, 8.3, 22.7, 8.0)
        val filterResult = PersonalConsumptionModel.filterOutliers(consumptions)

        assertEquals(5, filterResult.validConsumptions.size)
        assertEquals(1, filterResult.outliers.size)
        assertEquals(22.7, filterResult.outliers.first(), 0.01)
    }

    @Test
    fun calculateBaseline_withOutlier_filtersOutlierAndReturnsValidBaseline() {
        val raw = listOf(8.1, 8.4, 8.2, 8.3, 22.7, 8.0)
        val calc = PersonalConsumptionModel.calculateBaseline(raw)

        assertNotNull(calc.baselineConsumption)
        assertTrue(calc.baselineConsumption!! < 10.0)
        assertEquals(1, calc.outlierCount)
        assertEquals(PredictionConfidence.MEDIUM, calc.confidence)
    }

    @Test
    fun calculateWeightedMovingAverage_weightsRecentDataHigher() {
        val increasing = listOf(8.0, 8.0, 8.0, 10.0, 10.0)
        val weightedAvg = PersonalConsumptionModel.calculateWeightedMovingAverage(increasing)
        val simpleAvg = increasing.average()

        assertNotNull(weightedAvg)
        // Weighted average should be higher than simple average because recent values are 10.0
        assertTrue(weightedAvg!! > simpleAvg)
    }

    @Test
    fun evaluateConfidence_evaluatesGatesCorrectly() {
        val policy = LearningPolicy.DEFAULT
        assertEquals(PredictionConfidence.UNKNOWN, PersonalConsumptionModel.evaluateConfidence(2, policy))
        assertEquals(PredictionConfidence.LOW, PersonalConsumptionModel.evaluateConfidence(3, policy))
        assertEquals(PredictionConfidence.MEDIUM, PersonalConsumptionModel.evaluateConfidence(5, policy))
        assertEquals(PredictionConfidence.HIGH, PersonalConsumptionModel.evaluateConfidence(10, policy))
    }
}
