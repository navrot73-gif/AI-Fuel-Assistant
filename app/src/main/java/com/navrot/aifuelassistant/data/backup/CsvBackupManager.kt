package com.navrot.aifuelassistant.data.backup

import android.content.Context
import android.net.Uri
import com.navrot.aifuelassistant.data.FuelRecordRepository
import com.navrot.aifuelassistant.data.VehicleRepository
import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import com.navrot.aifuelassistant.data.database.entity.VehicleEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Экспорт/импорт заправок и автомобилей в CSV.
 *
 * Формат (разделитель ; , UTF-8):
 *   # AI-FUEL-BACKUP v1
 *   # VEHICLES
 *   id;name;brand;model;year;fuelType;tankCapacity;currentMileage
 *   # FILLS
 *   id;vehicleId;date;mileage;fuelAmount;totalCost;fuelType;stationName
 */
data class CsvImportResult(val imported: Int, val skipped: Int)

@Singleton
class CsvBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fuelRecordRepository: FuelRecordRepository,
    private val vehicleRepository: VehicleRepository
) {

    suspend fun exportToUri(uri: Uri): Int = withContext(Dispatchers.IO) {
        val vehicles = vehicleRepository.getAllVehicles().first()
        val fills = fuelRecordRepository.getAll().first()

        context.contentResolver.openOutputStream(uri, "w")?.use { out ->
            val w = OutputStreamWriter(out, Charsets.UTF_8)
            w.write("# AI-FUEL-BACKUP v1\n")
            w.write("# VEHICLES\n")
            w.write("id;name;brand;model;year;fuelType;tankCapacity;currentMileage\n")
            for (v in vehicles) {
                w.write("${v.id};${esc(v.name)};${esc(v.brand)};${esc(v.model)};" +
                        "${v.year};${esc(v.fuelType)};${v.tankCapacity};${v.currentMileage}\n")
            }
            w.write("# FILLS\n")
            w.write("id;vehicleId;date;mileage;fuelAmount;totalCost;fuelType;stationName\n")
            for (f in fills) {
                w.write("${f.id};${f.vehicleId};${f.date};${f.mileage};" +
                        "${f.fuelAmount};${f.totalCost};${esc(f.fuelType ?: "")};" +
                        "${esc(f.stationName ?: "")}\n")
            }
            w.flush()
        }
        fills.size
    }

    suspend fun importFromUri(uri: Uri): CsvImportResult = withContext(Dispatchers.IO) {
        var section = ""
        var importedFills = 0
        var skipped = 0

        val lines = context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readLines()
        } ?: emptyList()

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("# VEHICLES") -> section = "V"
                line.startsWith("# FILLS") -> section = "F"
                line.startsWith("#") -> { }
                section == "V" -> {
                    val v = parseVehicle(line)
                    if (v == null) skipped++ else vehicleRepository.insertVehicle(v)
                }
                section == "F" -> {
                    val f = parseFill(line)
                    if (f == null) skipped++ else {
                        fuelRecordRepository.insert(f)
                        importedFills++
                    }
                }
            }
        }
        CsvImportResult(importedFills, skipped)
    }

    private fun parseVehicle(line: String): VehicleEntity? = try {
        val p = splitLine(line)
        if (p.size < 8) null else VehicleEntity(
            id = p[0].toLongOrNull() ?: 0L,
            name = p[1],
            brand = p[2],
            model = p[3],
            year = p[4].toIntOrNull() ?: 0,
            fuelType = p[5],
            tankCapacity = p[6].toDoubleOrNull() ?: 0.0,
            currentMileage = p[7].toDoubleOrNull() ?: 0.0
        )
    } catch (_: Exception) { null }

    private fun parseFill(line: String): FuelRecordEntity? = try {
        val p = splitLine(line)
        if (p.size < 8) null else FuelRecordEntity(
            id = p[0].toLongOrNull() ?: 0L,
            vehicleId = p[1].toLongOrNull() ?: 0L,
            date = p[2].toLongOrNull() ?: 0L,
            mileage = p[3].toDoubleOrNull() ?: 0.0,
            fuelAmount = p[4].toDoubleOrNull() ?: 0.0,
            totalCost = p[5].toDoubleOrNull() ?: 0.0,
            fuelType = p[6],
            stationName = p[7]
        )
    } catch (_: Exception) { null }

    /** Парсит строку CSV с учётом кавычек. */
    private fun splitLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        cur.append('"'); i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ';' && !inQuotes -> { out.add(cur.toString()); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        out.add(cur.toString())
        return out
    }

    private fun esc(s: String): String =
        if (s.any { it == ';' || it == '"' || it == '\n' || it == '\r' })
            "\"${s.replace("\"", "\"\"")}\"" else s
}
