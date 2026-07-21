package com.navrot.aifuelassistant.ai

import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity

object FuelAnalysisPromptBuilder {
    fun build(records: List<FuelRecordEntity>): String {
        if (records.isEmpty()) {
            return "Нет данных о заправках для анализа. Добавьте хотя бы одну запись."
        }

        val data = records.joinToString(separator = "\n") { record ->
            "Пробег: ${record.mileage} км; " +
                    "Литров: ${record.fuelAmount}; " +
                    "Цена: ${record.pricePerLiter} ₽/л; " +
                    "Итого: ${record.totalCost} ₽; " +
                    "Топливо: ${record.fuelType}; " +
                    "АЗС: ${record.stationName.ifBlank { "не указана" }}"
        }

        return """
            Ты AI-ассистент по анализу расхода топлива автомобиля.
            
            Данные о заправках:
            $data
            
            Проанализируй эти данные и дай:
            1. Оценку среднего расхода топлива
            2. Тренды в расходе топлива
            3. 3 конкретных совета по экономии топлива
            4. Рекомендации по выбору АЗС
        """.trimIndent()
    }
}