package com.navrot.aifuelassistant.domain.ingestion

import com.navrot.aifuelassistant.data.model.FuelDataSource

interface FuelDataSourceAdapter {
    val sourceId: FuelDataSource

    suspend fun fetch(
        request: FuelSourceRequest
    ): FuelSourceResult
}
