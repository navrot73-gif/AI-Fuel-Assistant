package com.navrot.aifuelassistant.data.aggregator

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.data.model.PhotoEvidence

/**
 * Normalizes facts from different sources before they reach FuelDispatcher.
 * Network connectors are intentionally kept out of this class for now.
 */
object FuelDataAggregator {

    fun mergePrices(
        existing: List<FuelPrice>,
        incoming: List<FuelPrice>
    ): List<FuelPrice> {
        return (existing + incoming)
            .groupBy { it.type.uppercase() }
            .map { (_, values) -> chooseBestPrice(values) }
    }

    fun enrichStation(
        station: GasStation,
        incomingPrices: List<FuelPrice> = emptyList(),
        source: FuelDataSource? = null,
        evidence: List<PhotoEvidence> = emptyList()
    ): GasStation {
        val prices = mergePrices(station.fuelTypes, incomingPrices)
        val sources = buildSet {
            addAll(station.dataSources)
            source?.let(::add)
            prices.mapNotNullTo(this) {
                if (it.source != FuelDataSource.DEMO) it.source else null
            }
        }

        val allEvidence = station.photoEvidence + evidence
        val confidence = calculateConfidence(prices, station.reliability, allEvidence)

        return station.copy(
            fuelTypes = prices,
            dataSources = sources,
            updatedAt = prices.maxOfOrNull { it.updatedAt } ?: station.updatedAt,
            confidence = confidence,
            photoEvidence = allEvidence
        )
    }

    private fun chooseBestPrice(values: List<FuelPrice>): FuelPrice {
        return values.maxByOrNull { score(it) } ?: values.first()
    }

    private fun score(price: FuelPrice): Int {
        var score = price.confidence
        if (price.available) score += 20
        if (price.photoEvidence != null) score += 20
        if (price.source != FuelDataSource.DEMO) score += 10
        return score
    }

    private fun calculateConfidence(
        prices: List<FuelPrice>,
        reliability: Int,
        evidence: List<PhotoEvidence>
    ): Int {
        val priceConfidence = prices.map { it.confidence }.average().takeIf { !it.isNaN() } ?: 0.0
        val photoConfidence = evidence.mapNotNull { it.ocrConfidence }.average().takeIf { !it.isNaN() } ?: 0.0
        return (priceConfidence * 0.55 + photoConfidence * 0.25 + reliability * 0.20)
            .toInt()
            .coerceIn(0, 100)
    }
}
