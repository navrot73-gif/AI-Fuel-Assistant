package com.navrot.aifuelassistant.data.datasource

import com.navrot.aifuelassistant.data.model.GasStation

interface StationJsonParser {
    fun parseJson(jsonString: String): List<GasStation>
}
