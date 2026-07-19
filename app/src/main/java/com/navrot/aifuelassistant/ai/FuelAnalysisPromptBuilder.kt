package com.navrot.aifuelassistant.ai

import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity

object FuelAnalysisPromptBuilder {
    fun build(records: List<FuelRecordEntity>): String {
        if (records.isEmpty()) {
            return "Проанализируй данные о топливе. Пока записей о заправках нет. Ответь, какие данные нужно начать собирать для точного анализа расхода топлива."
        }

        val data = records.joinToString("\n") { record ->
            "Пробег: ${record.mileage} км; топлива: ${record.fuelAmount} л; цена: ${record.pricePerLiter}; сумма: ${record.totalCost}; тип: ${record.fuelType}; АЗС: ${record.stationName}"
        }

        return """
            Ты — AI-ассистент по анализу топлива и эксплуатации автомобиля.
            Проанализируй следующие записи:
            $data

            Дай краткий практический вывод:
            1. средний расход топлива, если его можно вычислить;
            2. динамика расходов;
            3. подозрительные или необычные значения;
            4. конкретные рекомендации владельцу.
            Не выдумывай отсутствующие данные.
        """.trimIndent()
    }
}
