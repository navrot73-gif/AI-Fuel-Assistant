package com.navrot.aifuelassistant.data.datasource

import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class StationJsonParserImpl @Inject constructor() : StationJsonParser {

    override fun parseJson(jsonString: String): List<GasStation> {
        val jsonArray = JSONArray(jsonString)
        return (0 until jsonArray.length()).map { i ->
            parseStation(jsonArray.getJSONObject(i))
        }.filter { station ->
            val lowerName = station.name.lowercase()
            !listOf("get petrol", "price", "test station").any { it in lowerName }
        }
    }

    private fun parseStation(json: JSONObject): GasStation {
        val fuelArray = json.getJSONArray("fuelTypes")
        val fuelTypes = (0 until fuelArray.length()).map { j ->
            val fuel = fuelArray.getJSONObject(j)
            FuelPrice(
                type = fuel.getString("type"),
                price = fuel.getDouble("price"),
                available = fuel.getBoolean("available")
            )
        }
        return GasStation(
            id = json.getInt("id"),
            name = json.getString("name"),
            brand = json.getString("brand"),
            address = json.getString("address"),
            latitude = json.getDouble("latitude"),
            longitude = json.getDouble("longitude"),
            fuelTypes = fuelTypes,
            queueTime = json.getInt("queueTime"),
            reliability = json.getInt("reliability"),
            monumentPhotoUrl = if (json.has("monumentPhotoUrl")) json.getString("monumentPhotoUrl") else null,
            entrancePhotoUrl = if (json.has("entrancePhotoUrl")) json.getString("entrancePhotoUrl") else null
        )
    }
}
