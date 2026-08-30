package com.navrot.aifuelassistant.ai

import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import com.navrot.aifuelassistant.data.database.entity.VehicleEntity
import com.navrot.aifuelassistant.data.model.GasStation
import com.navrot.aifuelassistant.domain.reliability.FuelAvailabilityStatus
import com.navrot.aifuelassistant.domain.reliability.PriceReliabilityCalculator
import com.navrot.aifuelassistant.geo.GeoUtils
import com.navrot.aifuelassistant.util.Format

object FuelAnalysisPromptBuilder {

    fun build(
        vehicle: VehicleEntity?,
        records: List<FuelRecordEntity>,
        lat: Double? = null,
        lon: Double? = null,
        nearbyStations: List<GasStation> = emptyList()
    ): String {

        if (records.isEmpty()) {
            return buildEmptyRecordsPrompt(vehicle, lat, lon)
        }

        val vehicleData = vehicle?.let {
            """
            Данные автомобиля:
            Марка: ${it.brand}
            Модель: ${it.model}
            Год выпуска: ${it.year}
            Тип топлива: ${it.fuelType}
            Объём бака: ${it.tankCapacity} л
            Текущий пробег: ${it.currentMileage} км
            """.trimIndent()
        } ?: "Данные об автомобиле отсутствуют."

        val fuelData = records.joinToString(separator = "\n") { record ->
            "Пробег: ${record.mileage} км; " +
                    "Литров: ${record.fuelAmount}; " +
                    "Цена: ${record.pricePerLiter} ₽/л; " +
                    "Итого: ${record.totalCost} ₽; " +
                    "Топливо: ${record.fuelType}; " +
                    "АЗС: ${record.stationName.ifBlank { "не указана" }}"
        }

        val locationContext = if (lat != null && lon != null) {
            val city = GeoUtils.hardcodedDetectCity(lat, lon)
            "\nТекущее местоположение пользователя: $city (lat: $lat, lon: $lon). Учитывай цены топлива и пробки в этом регионе при рекомендациях."
        } else ""

        val stationsContext = if (nearbyStations.isNotEmpty() && lat != null && lon != null) {
            val list = nearbyStations.joinToString("\n") { st ->
                val fuel = st.fuelTypes.firstOrNull()
                val price = fuel?.price?.let { "~${it.toInt()}₽" } ?: "нет данных"
                val distKm = GeoUtils.calculateDistance(lat, lon, st.latitude, st.longitude)
                val dist = "${Format.km(distKm)} км"
                val statusStr = when (PriceReliabilityCalculator.calculateFuelAvailability(st, vehicle?.fuelType)) {
                    FuelAvailabilityStatus.AVAILABLE -> "🟢 есть топливо"
                    FuelAvailabilityStatus.NO_FUEL -> "🔴 нет топлива"
                    FuelAvailabilityStatus.UNKNOWN -> "⚪ нет данных"
                }
                "- [${st.id}] ${st.brand} (${st.name}): $price, $dist ($statusStr)"
            }
            "\nБлижайшие АЗС:\n$list"
        } else ""

        return """
            ВАЖНО: Если просят маршрут до заправки — выбери ТОЧНО названную (даже если другая ближе) и добавь в конец ответа тег [ROUTE:id] отдельной строкой.
            НЕ добавляй фразы "маршрут построен", "прокладываю маршрут",
            "открываю карту" — приложение само покажет карту и маршрут.

            Ты AI-ассистент по анализу расхода топлива автомобиля.

            $vehicleData

            История заправок:
            $fuelData
            $locationContext
            $stationsContext

            Проанализируй данные и дай:
            1. Оценку среднего расхода топлива.
            2. Тренды в расходе топлива.
            3. 3 конкретных совета по экономии топлива.
            4. Рекомендации по выбору АЗС.
            5. Учитывай характеристики именно этого автомобиля.

            Если данных недостаточно для точного расчёта среднего расхода,
            честно укажи это и объясни, какие данные нужны.
        """.trimIndent()
    }

    private fun buildEmptyRecordsPrompt(vehicle: VehicleEntity?, lat: Double?, lon: Double?): String {
        val vehicleData = vehicle?.let {
            """
            Автомобиль:
            ${it.brand} ${it.model}, ${it.year} год.
            Топливо: ${it.fuelType}.
            Объём бака: ${it.tankCapacity} л.
            Текущий пробег: ${it.currentMileage} км.
            """.trimIndent()
        } ?: "Данные об автомобиле отсутствуют."

        val locationContext = if (lat != null && lon != null) {
            val city = GeoUtils.hardcodedDetectCity(lat, lon)
            "\nТекущее местоположение пользователя: $city (lat: $lat, lon: $lon)."
        } else ""

        return """
            ВАЖНО: Если просят маршрут до заправки — выбери ТОЧНО названную (даже если другая ближе) и добавь в конец ответа тег [ROUTE:id] отдельной строкой.
            НЕ добавляй фразы "маршрут построен", "прокладываю маршрут",
            "открываю карту" — приложение само покажет карту и маршрут.

            Ты AI-ассистент по анализу расхода топлива.

            $vehicleData
            $locationContext

            История заправок отсутствует.

            Объясни пользователю, что для полноценного анализа нужно
            добавить записи о заправках. Укажи, какие данные желательно
            сохранять для точного расчёта расхода топлива.
        """.trimIndent()
    }
}
