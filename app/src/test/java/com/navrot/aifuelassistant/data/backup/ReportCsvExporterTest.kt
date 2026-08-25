package com.navrot.aifuelassistant.data.backup

import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportCsvExporterTest {

    @Test
    fun generateCsv_withEmptyList_returnsHeaderOnly() {
        val records = emptyList<FuelRecordEntity>()
        val csv = ReportCsvExporter.generateCsv(records)
        val expectedHeader = "Дата;Пробег (км);Объём (л);Цена за л (руб);Сумма (руб);Тип топлива;АЗС;Заметки"
        assertEquals("$expectedHeader\n", csv)
    }

    @Test
    fun generateCsv_withRecords_formatsRowsAndEscapesCorrectly() {
        val recordDate = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val dateStr = dateFormat.format(Date(recordDate))

        val record1 = FuelRecordEntity(
            id = 1L,
            vehicleId = 10L,
            date = recordDate,
            mileage = 15000.0,
            fuelAmount = 40.5,
            pricePerLiter = 55.0,
            totalCost = 2227.5,
            fuelType = "АИ-95",
            stationName = "Лукойл; №1",
            notes = "Заправка \"до полного\""
        )

        val csv = ReportCsvExporter.generateCsv(listOf(record1))
        val lines = csv.trim().split("\n")

        assertEquals(2, lines.size)
        assertEquals("Дата;Пробег (км);Объём (л);Цена за л (руб);Сумма (руб);Тип топлива;АЗС;Заметки", lines[0])

        val expectedRow = "$dateStr;15000;40.50;55;2227.50;АИ-95;\"Лукойл; №1\";\"Заправка \"\"до полного\"\"\""
        assertEquals(expectedRow, lines[1])
    }
}
