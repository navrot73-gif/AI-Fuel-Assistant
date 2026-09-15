package com.navrot.aifuelassistant.domain.intelligence

import com.navrot.aifuelassistant.data.model.FuelDataSource

data class SourceReliability(
    val source: FuelDataSource,
    val reliability: Double, // 0.0 .. 1.0
    val freshnessWeight: Double = 1.0
) {
    companion object {
        fun getReliability(source: FuelDataSource): SourceReliability {
            return when (source) {
                FuelDataSource.USER_REPORT -> SourceReliability(source, 0.95, 1.0)
                FuelDataSource.PHOTO_EVIDENCE -> SourceReliability(source, 0.95, 1.0)
                FuelDataSource.BENZONAVT -> SourceReliability(source, 0.85, 1.0)
                FuelDataSource.RUSSIABASE -> SourceReliability(source, 0.85, 1.0)
                FuelDataSource.OFFICIAL_STATION -> SourceReliability(source, 0.90, 1.0)
                FuelDataSource.TWO_GIS, FuelDataSource.YANDEX, FuelDataSource.T_BANK, FuelDataSource.GDEBENZ -> SourceReliability(source, 0.80, 0.9)
                FuelDataSource.TELEGRAM, FuelDataSource.VK -> SourceReliability(source, 0.60, 0.8)
                FuelDataSource.OVERPASS -> SourceReliability(source, 0.40, 0.5)
                FuelDataSource.DEMO -> SourceReliability(source, 0.30, 0.5)
            }
        }

        fun getReliabilityForUnknownSource(): SourceReliability {
            return SourceReliability(FuelDataSource.DEMO, 0.30, 0.5)
        }
    }
}
