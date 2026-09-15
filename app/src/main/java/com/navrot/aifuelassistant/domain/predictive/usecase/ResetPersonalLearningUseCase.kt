package com.navrot.aifuelassistant.domain.predictive.usecase

import com.navrot.aifuelassistant.domain.predictive.PersonalModelMetadata
import javax.inject.Inject

class ResetPersonalLearningUseCase @Inject constructor() {

    fun resetModel(vehicleId: Long): PersonalModelMetadata {
        return PersonalModelMetadata(
            vehicleId = vehicleId,
            modelVersion = PersonalModelMetadata.CURRENT_VERSION,
            trainedSamples = 0,
            biasAdjustment = 0.0,
            lastUpdatedAt = System.currentTimeMillis()
        )
    }
}
