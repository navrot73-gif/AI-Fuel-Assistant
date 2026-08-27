package com.navrot.aifuelassistant.data.datasource

import com.navrot.aifuelassistant.data.model.GasStation

interface StationFilterAndSorter {
    fun getStationsNearLocation(lat: Double, lon: Double, radiusKm: Double, stations: List<GasStation>): List<GasStation>
    fun filterByCity(stations: List<GasStation>, city: String): List<GasStation>
    fun search(stations: List<GasStation>, query: String): List<GasStation>
    fun getCheapestStation(stations: List<GasStation>, fuelType: String): GasStation?
    fun sortPriceAscending(stations: List<GasStation>, fuelType: String): List<GasStation>
    fun sortPriceDescending(stations: List<GasStation>, fuelType: String): List<GasStation>
    fun sortByQueue(stations: List<GasStation>, fuelType: String): List<GasStation>
}
