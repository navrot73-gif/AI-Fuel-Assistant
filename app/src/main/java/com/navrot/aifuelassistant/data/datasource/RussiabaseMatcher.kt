package com.navrot.aifuelassistant.data.datasource

import com.navrot.aifuelassistant.data.model.FuelDataSource
import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.data.model.matchesBrand

object RussiabaseMatcher {

    fun applyObservations(
        stations: List<GasStation>,
        observations: List<FuelObservation>
    ): List<GasStation> {
        if (observations.isEmpty() || stations.isEmpty()) return stations

        val matchedStationMap = stations.associateBy { it.id }.toMutableMap()

        for (obs in observations) {
            val matchingStation = stations.firstOrNull { station ->
                matchesBrandAndAddress(station, obs)
            }

            if (matchingStation != null) {
                val current = matchedStationMap[matchingStation.id] ?: matchingStation
                val updatedFuelTypes = updateFuelPrices(current.fuelTypes, obs)
                val updatedSources = current.dataSources + FuelDataSource.RUSSIABASE
                val now = System.currentTimeMillis()

                matchedStationMap[matchingStation.id] = current.copy(
                    fuelTypes = updatedFuelTypes,
                    dataSources = updatedSources,
                    updatedAt = now
                )
            }
        }

        return stations.map { matchedStationMap[it.id] ?: it }
    }

    fun matchesBrandAndAddress(station: GasStation, obs: FuelObservation): Boolean {
        val brandMatch = station.matchesBrand(obs.brand) ||
                obs.brand.contains(station.brand, ignoreCase = true) ||
                station.brand.contains(obs.brand, ignoreCase = true)

        if (!brandMatch) return false

        return isAddressMatch(station.address, obs.address)
    }

    fun isAddressMatch(baseAddress: String, obsAddress: String): Boolean {
        val normBase = normalizeAddress(baseAddress)
        val normObs = normalizeAddress(obsAddress)

        if (normBase.isBlank() || normObs.isBlank()) return false

        if (normBase == normObs || normBase.contains(normObs) || normObs.contains(normBase)) {
            return true
        }

        if (computeLevenshteinDistance(normBase, normObs) <= 2) {
            return true
        }

        val baseTokens = normBase.split(" ").filter { it.isNotBlank() }
        val obsTokens = normObs.split(" ").filter { it.isNotBlank() }

        if (obsTokens.isEmpty()) return false

        val allObsMatched = obsTokens.all { obsTok ->
            baseTokens.any { baseTok ->
                baseTok == obsTok || baseTok.contains(obsTok) || obsTok.contains(baseTok) ||
                        (baseTok.length >= 3 && obsTok.length >= 3 && computeLevenshteinDistance(baseTok, obsTok) <= 2)
            }
        }

        return allObsMatched
    }

    fun normalizeAddress(address: String): String {
        return address.lowercase()
            .replace("г.", "")
            .replace("город", "")
            .replace("ул.", "")
            .replace("улица", "")
            .replace("пр.", "")
            .replace("проспект", "")
            .replace("пер.", "")
            .replace("переулок", "")
            .replace("ш.", "")
            .replace("шоссе", "")
            .replace("тр.", "")
            .replace("тракт", "")
            .replace("стр.", "")
            .replace("строение", "")
            .replace("д.", "")
            .replace("дом", "")
            .replace(Regex("[.,\\\\/\\-–—_\"']"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun computeLevenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[m][n]
    }

    private fun updateFuelPrices(
        existingList: List<FuelPrice>,
        obs: FuelObservation
    ): List<FuelPrice> {
        val now = System.currentTimeMillis()
        val targetType = obs.fuelType
        val newList = existingList.toMutableList()

        val index = newList.indexOfFirst {
            it.type.equals(targetType, ignoreCase = true) ||
                    (targetType.equals("АИ-95", true) && it.type.equals("AI-95", true)) ||
                    (targetType.equals("АИ-92", true) && it.type.equals("AI-92", true)) ||
                    (targetType.equals("АИ-98", true) && it.type.equals("AI-98", true)) ||
                    (targetType.equals("ДТ", true) && (it.type.equals("Diesel", true) || it.type.equals("ДТ", true)))
        }

        val newPriceVal = if (obs.available && obs.price > 0.0) obs.price else 0.0

        if (index != -1) {
            val existing = newList[index]
            newList[index] = existing.copy(
                price = if (obs.available && obs.price > 0.0) obs.price else if (!obs.available) 0.0 else existing.price,
                available = obs.available,
                source = FuelDataSource.RUSSIABASE,
                updatedAt = now,
                limitNote = obs.limitNote
            )
        } else {
            newList.add(
                FuelPrice(
                    type = targetType,
                    price = newPriceVal,
                    available = obs.available,
                    source = FuelDataSource.RUSSIABASE,
                    updatedAt = now,
                    limitNote = obs.limitNote
                )
            )
        }

        return newList
    }
}
