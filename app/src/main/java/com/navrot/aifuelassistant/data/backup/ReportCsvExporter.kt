package com.navrot.aifuelassistant.data.backup

import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportCsvExporter {

    private const val HEADER = "Дата;Пробег (км);Объём (л);Цена за л (руб);Сумма (руб);Тип топлива;АЗС;Заметки"

    fun generateCsv(records: List<FuelRecordEntity>): String {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val sb = StringBuilder()
        sb.append(HEADER).append("\n")

        for (record in records) {
            val dateStr = dateFormat.format(Date(record.date))
            val mileageStr = formatNumber(record.mileage)
            val amountStr = formatNumber(record.fuelAmount)
            val priceStr = formatNumber(record.pricePerLiter)
            val costStr = formatNumber(record.totalCost)
            val fuelTypeStr = esc(record.fuelType)
            val stationStr = esc(record.stationName)
            val notesStr = esc(record.notes)

            sb.append(dateStr).append(";")
                .append(mileageStr).append(";")
                .append(amountStr).append(";")
                .append(priceStr).append(";")
                .append(costStr).append(";")
                .append(fuelTypeStr).append(";")
                .append(stationStr).append(";")
                .append(notesStr).append("\n")
        }

        return sb.toString()
    }

    private fun formatNumber(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", value)
        }
    }

    private fun esc(s: String): String =
        if (s.any { it == ';' || it == '"' || it == '\n' || it == '\r' })
            "\"${s.replace("\"", "\"\"")}\"" else s
}
