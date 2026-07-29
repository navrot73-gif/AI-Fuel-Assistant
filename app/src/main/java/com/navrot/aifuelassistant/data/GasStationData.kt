package com.navrot.aifuelassistant.data

import com.navrot.aifuelassistant.data.model.FuelPrice
import com.navrot.aifuelassistant.data.model.GasStation

object GasStationData {
    val stations = listOf(
        // ===== МОСКВА =====
        GasStation(
            id = 1,
            name = "АЗС ТТК",
            brand = "Татнефть",
            address = "Москва, ш. Энтузиастов, 86А",
            latitude = 55.7761,
            longitude = 37.8419,
            fuelTypes = listOf(
                FuelPrice("АИ-92", 59.89, true),
                FuelPrice("АИ-95", 66.23, true),
                FuelPrice("АИ-98", 78.50, true),
                FuelPrice("АИ-100", 99.99, true),
                FuelPrice("ДТ", 77.34, true)
            ),
            queueTime = 5,
            reliability = 90
        ),
        GasStation(
            id = 2,
            name = "АЗС №45",
            brand = "Лукойл",
            address = "Москва, Ленинградский пр-т, 62",
            latitude = 55.8072,
            longitude = 37.5394,
            fuelTypes = listOf(
                FuelPrice("АИ-92", 62.50, true),
                FuelPrice("АИ-95", 69.10, true),
                FuelPrice("ДТ", 79.00, true)
            ),
            queueTime = 12,
            reliability = 85
        ),
        GasStation(
            id = 3,
            name = "АЗС Север",
            brand = "Газпромнефть",
            address = "Москва, Дмитровское ш., 100",
            latitude = 55.8742,
            longitude = 37.5408,
            fuelTypes = listOf(
                FuelPrice("АИ-92", 60.50, true),
                FuelPrice("АИ-95", 67.20, true),
                FuelPrice("АИ-98", 79.80, true),
                FuelPrice("ДТ", 78.10, true),
                FuelPrice("Газ", 34.50, true)
            ),
            queueTime = 8,
            reliability = 92
        ),
        GasStation(
            id = 4,
            name = "АЗС Юг",
            brand = "Роснефть",
            address = "Москва, Варшавское ш., 152",
            latitude = 55.5832,
            longitude = 37.5965,
            fuelTypes = listOf(
                FuelPrice("АИ-92", 61.00, true),
                FuelPrice("АИ-95", 68.50, true),
                FuelPrice("ДТ", 78.50, true)
            ),
            queueTime = 15,
            reliability = 80
        ),
        GasStation(
            id = 5,
            name = "АЗС Восток",
            brand = "Shell",
            address = "Москва, МКАД, 32 км",
            latitude = 55.7013,
            longitude = 37.8500,
            fuelTypes = listOf(
                FuelPrice("АИ-95", 70.50, true),
                FuelPrice("АИ-98", 82.00, true),
                FuelPrice("ДТ", 80.00, true)
            ),
            queueTime = 3,
            reliability = 95
        ),

        // ===== ЧЕЛЯБИНСК =====
        GasStation(
            id = 10,
            name = "АЗС №1",
            brand = "Лукойл",
            address = "Челябинск, ул. Мира, 65 ст1",
            latitude = 55.25349,
            longitude = 61.37002,
            fuelTypes = listOf(
                FuelPrice("АИ-92", 62.94, true),
                FuelPrice("АИ-95", 69.73, true),
                FuelPrice("ДТ", 80.10, true)
            ),
            queueTime = 5,
            reliability = 90
        ),
        GasStation(
            id = 11,
            name = "Свердловский тракт, 16/3",
            brand = "Татнефть",
            address = "Челябинск, Свердловский тракт, 16/3",
            latitude = 55.1778,
            longitude = 61.4194,
            fuelTypes = listOf(
                FuelPrice("АИ-92", 59.89, true),
                FuelPrice("АИ-95", 66.23, true),
                FuelPrice("АИ-98", 78.50, true),
                FuelPrice("АИ-100", 99.99, true),
                FuelPrice("ДТ", 77.34, true)
            ),
            queueTime = 15,
            reliability = 85
        ),
        GasStation(
            id = 12,
            name = "Свердловский тракт, 40/1",
            brand = "Татнефть",
            address = "Челябинск, Свердловский тракт, 40/1",
            latitude = 55.1847,
            longitude = 61.4098,
            fuelTypes = listOf(
                FuelPrice("АИ-92", 59.89, true),
                FuelPrice("АИ-95", 66.23, true),
                FuelPrice("АИ-98", 78.50, true),
                FuelPrice("АИ-100", 99.99, true),
                FuelPrice("ДТ", 77.34, true)
            ),
            queueTime = 20,
            reliability = 82
        ),
        GasStation(
            id = 13,
            name = "ул. Курчатова, 2/1",
            brand = "Газпромнефть",
            address = "Челябинск, ул. Курчатова, 2/1",
            latitude = 55.1603,
            longitude = 61.4007,
            fuelTypes = listOf(
                FuelPrice("АИ-92", 61.50, true),
                FuelPrice("АИ-95", 68.20, true),
                FuelPrice("АИ-98", 80.00, true),
                FuelPrice("ДТ", 79.00, true)
            ),
            queueTime = 8,
            reliability = 93
        ),
        GasStation(
            id = 14,
            name = "Троицкий тракт, 9640",
            brand = "Газпромнефть",
            address = "Челябинск, Троицкий тракт, 9640",
            latitude = 55.1194,
            longitude = 61.3512,
            fuelTypes = listOf(
                FuelPrice("АИ-92", 61.50, true),
                FuelPrice("АИ-95", 68.20, true),
                FuelPrice("ДТ", 79.00, true)
            ),
            queueTime = 10,
            reliability = 91
        ),
        GasStation(
            id = 15,
            name = "ул. Артиллерийская, 138",
            brand = "Лукойл",
            address = "Челябинск, ул. Артиллерийская, 138 к. 1",
            latitude = 55.1895,
            longitude = 61.4498,
            fuelTypes = listOf(
                FuelPrice("АИ-92", 62.50, true),
                FuelPrice("АИ-95", 69.10, true)
            ),
            queueTime = 6,
            reliability = 88
        ),
        GasStation(
            id = 16,
            name = "Копейское шоссе, 56в",
            brand = "РегионUno",
            address = "Челябинск, Копейское шоссе, 56в",
            latitude = 55.1498,
            longitude = 61.4995,
            fuelTypes = listOf(
                FuelPrice("АИ-92", 60.00, true),
                FuelPrice("АИ-95", 67.00, true),
                FuelPrice("ДТ", 78.00, true),
                FuelPrice("Газ", 33.50, true)
            ),
            queueTime = 12,
            reliability = 84
        ),
        GasStation(
            id = 17,
            name = "Копейское шоссе, 1П/1",
            brand = "Get petrol",
            address = "Челябинск, Копейское шоссе, 1П/1",
            latitude = 55.1452,
            longitude = 61.5198,
            fuelTypes = listOf(
                FuelPrice("АИ-92", 59.50, true),
                FuelPrice("АИ-95", 66.00, true),
                FuelPrice("ДТ", 76.50, true),
                FuelPrice("Газ", 32.00, true)
            ),
            queueTime = 18,
            reliability = 79
        ),
        GasStation(
            id = 18,
            name = "ул. Механическая, 105/1",
            brand = "Get petrol",
            address = "Челябинск, ул. Механическая, 105/1",
            latitude = 55.192317,
            longitude = 61.421625,
            fuelTypes = listOf(
                FuelPrice("АИ-92", 59.50, true),
                FuelPrice("АИ-95", 66.00, true),
                FuelPrice("ДТ", 76.50, true)
            ),
            queueTime = 7,
            reliability = 81
        ),
        GasStation(
            id = 19,
            name = "ул. Игуменка, 119",
            brand = "Челябнефтепродукт",
            address = "Челябинск, ул. Игуменка, 119",
            latitude = 55.2298,
            longitude = 61.2995,
            fuelTypes = listOf(
                FuelPrice("АИ-92", 58.50, true),
                FuelPrice("АИ-95", 65.00, true),
                FuelPrice("ДТ", 75.00, true)
            ),
            queueTime = 25,
            reliability = 75
        ),
        GasStation(
            id = 20,
            name = "пр. Победы, 290/1",
            brand = "Мега",
            address = "Челябинск, пр. Победы, 290/1",
            latitude = 55.1695,
            longitude = 61.2798,
            fuelTypes = listOf(
                FuelPrice("АИ-92", 61.00, true),
                FuelPrice("АИ-95", 68.00, true),
                FuelPrice("ДТ", 78.50, true)
            ),
            queueTime = 9,
            reliability = 87
        ),
        GasStation(
            id = 21,
            name = "пер. Бажова и Комарова",
            brand = "Смарт",
            address = "Челябинск, перекрёсток ул. Бажова и ул. Комарова",
            latitude = 55.1995,
            longitude = 61.4198,
            fuelTypes = listOf(
                FuelPrice("Газ", 31.00, true)
            ),
            queueTime = 4,
            reliability = 86
        ),
        GasStation(
            id = 22,
            name = "ул. Энгельса, 21/1",
            brand = "АЗС 74-026",
            address = "Челябинск, ул. Энгельса, 21/1",
            latitude = 55.1798,
            longitude = 61.4595,
            fuelTypes = listOf(
                FuelPrice("АИ-92", 59.00, true),
                FuelPrice("АИ-95", 65.50, true)
            ),
            queueTime = 14,
            reliability = 78
        ),
        GasStation(
            id = 23,
            name = "ул. Первой пятилетки, 18",
            brand = "Price",
            address = "Челябинск, ул. Первой пятилетки, 18 к. 1",
            latitude = 55.1648,
            longitude = 61.3895,
            fuelTypes = listOf(
                FuelPrice("АИ-95", 67.50, true),
                FuelPrice("Газ", 33.00, true)
            ),
            queueTime = 11,
            reliability = 83
        ),
        GasStation(
            id = 24,
            name = "ул. Городская, 1/4",
            brand = "Price",
            address = "Челябинск, ул. Городская, 1/4",
            latitude = 55.1545,
            longitude = 61.4398,
            fuelTypes = listOf(
                FuelPrice("АИ-95", 67.50, true),
                FuelPrice("Газ", 33.00, true)
            ),
            queueTime = 13,
            reliability = 82
        ),

        // ===== ЧЕЛЯБИНСКАЯ ОБЛАСТЬ =====
        GasStation(
            id = 30,
            name = "М-5 Урал, 1882 км",
            brand = "Газпромнефть",
            address = "Челябинская обл., М-5 Урал, 1882 км",
            latitude = 54.8995,
            longitude = 61.4998,
            fuelTypes = listOf(
                FuelPrice("АИ-92", 62.00, true),
                FuelPrice("АИ-95", 69.00, true),
                FuelPrice("ДТ", 79.50, true)
            ),
            queueTime = 5,
            reliability = 92
        ),
        GasStation(
            id = 31,
            name = "М-5 Урал, 1890 км",
            brand = "Татнефть",
            address = "Челябинская обл., М-5 Урал, 1890 км",
            latitude = 54.8495,
            longitude = 61.5498,
            fuelTypes = listOf(
                FuelPrice("АИ-92", 59.50, true),
                FuelPrice("АИ-95", 66.00, true),
                FuelPrice("ДТ", 77.00, true)
            ),
            queueTime = 8,
            reliability = 88
        ),
        GasStation(
            id = 32,
            name = "АЗС Миасс",
            brand = "Лукойл",
            address = "Миасс, ул. Московская, 45",
            latitude = 55.0450,
            longitude = 60.1000,
            fuelTypes = listOf(
                FuelPrice("АИ-92", 61.50, true),
                FuelPrice("АИ-95", 68.00, true),
                FuelPrice("ДТ", 78.00, true)
            ),
            queueTime = 6,
            reliability = 89
        ),
        GasStation(
            id = 33,
            name = "АЗС Златоуст",
            brand = "Роснефть",
            address = "Златоуст, пр. имени Ю.А. Гагарина, 20",
            latitude = 55.1700,
            longitude = 59.6500,
            fuelTypes = listOf(
                FuelPrice("АИ-92", 60.50, true),
                FuelPrice("АИ-95", 67.00, true),
                FuelPrice("ДТ", 77.50, true)
            ),
            queueTime = 10,
            reliability = 85
        ),
        GasStation(
            id = 34,
            name = "АЗС Магнитогорск",
            brand = "Газпромнефть",
            address = "Магнитогорск, пр. Ленина, 100",
            latitude = 53.3800,
            longitude = 59.0500,
            fuelTypes = listOf(
                FuelPrice("АИ-92", 61.00, true),
                FuelPrice("АИ-95", 68.50, true),
                FuelPrice("АИ-98", 80.00, true),
                FuelPrice("ДТ", 78.50, true)
            ),
            queueTime = 7,
            reliability = 91
        ),
        GasStation(
            id = 35,
            name = "АЗС Копейск",
            brand = "РегионUno",
            address = "Копейск, пр. Коммунистический, 25",
            latitude = 55.1000,
            longitude = 61.6200,
            fuelTypes = listOf(
                FuelPrice("АИ-92", 59.00, true),
                FuelPrice("АИ-95", 65.50, true),
                FuelPrice("ДТ", 76.00, true),
                FuelPrice("Газ", 32.00, true)
            ),
            queueTime = 15,
            reliability = 80
        ),
        GasStation(
            id = 36,
            name = "АЗС Троицк",
            brand = "Татнефть",
            address = "Троицк, ул. Советская, 80",
            latitude = 54.0800,
            longitude = 61.5500,
            fuelTypes = listOf(
                FuelPrice("АИ-92", 59.50, true),
                FuelPrice("АИ-95", 66.00, true),
                FuelPrice("ДТ", 77.00, true)
            ),
            queueTime = 9,
            reliability = 86
        ),
        GasStation(
            id = 37,
            name = "АЗС Снежинск",
            brand = "Лукойл",
            address = "Снежинск, ул. Ленина, 15",
            latitude = 56.0800,
            longitude = 60.7300,
            fuelTypes = listOf(
                FuelPrice("АИ-92", 62.00, true),
                FuelPrice("АИ-95", 69.00, true),
                FuelPrice("ДТ", 79.00, true)
            ),
            queueTime = 4,
            reliability = 90
        ),
        GasStation(
            id = 38,
            name = "АЗС Озёрск",
            brand = "Газпромнефть",
            address = "Озёрск, пр. Ленина, 50",
            latitude = 55.7500,
            longitude = 60.7000,
            fuelTypes = listOf(
                FuelPrice("АИ-92", 61.50, true),
                FuelPrice("АИ-95", 68.00, true),
                FuelPrice("ДТ", 78.50, true)
            ),
            queueTime = 6,
            reliability = 92
        ),
        GasStation(
            id = 39,
            name = "АЗС Южноуральск",
            brand = "Роснефть",
            address = "Южноуральск, ул. Строителей, 30",
            latitude = 54.4500,
            longitude = 61.2500,
            fuelTypes = listOf(
                FuelPrice("АИ-92", 60.00, true),
                FuelPrice("АИ-95", 66.50, true),
                FuelPrice("ДТ", 77.00, true)
            ),
            queueTime = 12,
            reliability = 83
        ),
        GasStation(
            id = 40,
            name = "АЗС Аша",
            brand = "Татнефть",
            address = "Аша, ул. Ленина, 10",
            latitude = 54.9900,
            longitude = 57.2700,
            fuelTypes = listOf(
                FuelPrice("АИ-92", 60.50, true),
                FuelPrice("АИ-95", 67.00, true),
                FuelPrice("ДТ", 77.50, true)
            ),
            queueTime = 8,
            reliability = 87
        )
    )

    fun searchStations(query: String): List<GasStation> {
        val q = query.lowercase()
        return stations.filter {
            it.name.lowercase().contains(q) ||
                    it.brand.lowercase().contains(q) ||
                    it.address.lowercase().contains(q)
        }
    }

    fun getBestStation(fuelType: String): GasStation? {
        return stations
            .filter { station ->
                station.fuelTypes.any { it.type == fuelType && it.available }
            }
            .minByOrNull { station ->
                val fuel = station.fuelTypes.find { it.type == fuelType }
                val queuePenalty = station.queueTime * 0.5
                val reliabilityBonus = (100 - station.reliability) * 0.2
                (fuel?.price ?: Double.MAX_VALUE) + queuePenalty - reliabilityBonus
            }
    }

    fun getBestStations(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): List<GasStation> {
        val nearby = if (lat != null && lon != null) {
            getStationsNearLocation(lat, lon, radiusKm)
        } else stations

        return nearby.filter { s -> s.fuelTypes.any { it.type == fuelType && it.available } }
            .sortedBy { s ->
                val fuel = s.fuelTypes.find { it.type == fuelType }!!
                val queuePenalty = s.queueTime * 0.5
                val reliabilityBonus = (100 - s.reliability) * 0.2
                fuel.price + queuePenalty - reliabilityBonus
            }
    }

    fun getStationsSortedByPriceAsc(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): List<GasStation> {
        val nearby = if (lat != null && lon != null) {
            getStationsNearLocation(lat, lon, radiusKm)
        } else stations

        return nearby.filter { s -> s.fuelTypes.any { it.type == fuelType && it.available } }
            .sortedBy { s -> s.fuelTypes.find { it.type == fuelType }?.price ?: Double.MAX_VALUE }
    }

    fun getStationsSortedByPriceDesc(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): List<GasStation> {
        val nearby = if (lat != null && lon != null) {
            getStationsNearLocation(lat, lon, radiusKm)
        } else stations

        return nearby.filter { s -> s.fuelTypes.any { it.type == fuelType && it.available } }
            .sortedByDescending { s -> s.fuelTypes.find { it.type == fuelType }?.price ?: 0.0 }
    }

    fun getStationsByQueue(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): List<GasStation> {
        val nearby = if (lat != null && lon != null) {
            getStationsNearLocation(lat, lon, radiusKm)
        } else stations

        return nearby.filter { s -> s.fuelTypes.any { it.type == fuelType && it.available } }
            .sortedBy { it.queueTime }
    }

    fun getCheapestStation(fuelType: String, lat: Double?, lon: Double?, radiusKm: Double): GasStation? {
        return getStationsSortedByPriceAsc(fuelType, lat, lon, radiusKm).firstOrNull()
    }

    fun getStationsByCity(city: String): List<GasStation> {
        return stations.filter { it.address.contains(city, ignoreCase = true) }
    }

    fun getStationsNearLocation(lat: Double, lon: Double, radiusKm: Double = 10.0): List<GasStation> {
        return stations.filter { station ->
            val distance = calculateDistance(lat, lon, station.latitude, station.longitude)
            distance <= radiusKm
        }.sortedBy { station ->
            calculateDistance(lat, lon, station.latitude, station.longitude)
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }
}