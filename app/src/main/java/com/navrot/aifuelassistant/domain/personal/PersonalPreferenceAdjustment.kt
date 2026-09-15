package com.navrot.aifuelassistant.domain.personal

import com.navrot.aifuelassistant.domain.recommendation.BestStationResult
import com.navrot.aifuelassistant.domain.recommendation.StationRecommendation

class PersonalPreferenceAdjustment {

    fun buildPersonalRecommendation(
        baseResult: BestStationResult,
        events: List<PersonalFuelEvent>
    ): PersonalRecommendation? {
        val baseRec = baseResult.best ?: return null
        val stationId = baseRec.station.id

        val profileUseCase = CalculatePersonalStationProfileUseCase()
        val profile = profileUseCase.execute(stationId, events)

        val extraReasons = mutableListOf<String>()
        var personalScore = baseRec.score

        if (profile.visitCount > 0) {
            extraReasons.add("Вы заправлялись здесь ${profile.visitCount} раз")
            personalScore += (profile.userPreferenceScore * 0.1) // Slight boost, retaining objective order foundation
        }

        return PersonalRecommendation(
            baseRecommendation = baseRec,
            personalScore = personalScore,
            isPersonalized = profile.visitCount > 0,
            personalizedReasons = extraReasons
        )
    }

    /**
     * Ranks multiple stations for the user without modifying objective base scoring.
     */
    fun personalizeRecommendations(
        recommendations: List<StationRecommendation>,
        events: List<PersonalFuelEvent>
    ): List<PersonalRecommendation> {
        val profileUseCase = CalculatePersonalStationProfileUseCase()

        return recommendations.map { rec ->
            val profile = profileUseCase.execute(rec.station.id, events)
            val extraReasons = mutableListOf<String>()
            var personalScore = rec.score

            if (profile.visitCount > 0) {
                extraReasons.add("Вы заправлялись здесь ${profile.visitCount} раз")
                personalScore += (profile.userPreferenceScore * 0.1)
            }

            PersonalRecommendation(
                baseRecommendation = rec,
                personalScore = personalScore,
                isPersonalized = profile.visitCount > 0,
                personalizedReasons = extraReasons
            )
        }.sortedByDescending { it.personalScore }
    }
}
