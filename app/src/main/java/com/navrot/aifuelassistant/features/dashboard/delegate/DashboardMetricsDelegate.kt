package com.navrot.aifuelassistant.features.dashboard.delegate

import com.navrot.aifuelassistant.data.FuelRecordRepository
import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import com.navrot.aifuelassistant.features.dashboard.DashboardMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Calendar
import javax.inject.Inject

class DashboardMetricsDelegate @Inject constructor(
    private val fuelRecordRepository: FuelRecordRepository,
) {
    private val _metrics = MutableStateFlow(DashboardMetrics())
    val metrics: StateFlow<DashboardMetrics> = _metrics.asStateFlow()

    private val _weeklyConsumption = MutableStateFlow<List<Pair<String, Float>>>(emptyList())
    val weeklyConsumption: StateFlow<List<Pair<String, Float>>> = _weeklyConsumption.asStateFlow()

    fun loadMetrics(scope: CoroutineScope, selectedVehicleId: Long?) {
        scope.launch {
            try {
                val flow = if (selectedVehicleId != null && selectedVehicleId > 0) {
                    fuelRecordRepository.getByVehicleId(selectedVehicleId)
                } else {
                    fuelRecordRepository.getAll()
                }
                flow
                    .catch { e ->
                        Timber.tag("Metrics").e(e, "Error loading fuel records")
                        _metrics.value = DashboardMetrics()
                        _weeklyConsumption.value = emptyList()
                    }
                    .collect { records ->
                        try {
                            _metrics.value = computeMetrics(records)
                            _weeklyConsumption.value = computeWeeklyConsumption(records, selectedVehicleId)
                        } catch (e: Exception) {
                            Timber.tag("Metrics").e(e, "Error computing metrics")
                            _metrics.value = DashboardMetrics()
                            _weeklyConsumption.value = emptyList()
                        }
                    }
            } catch (e: Exception) {
                Timber.tag("Metrics").e(e, "Error in loadMetrics")
                _metrics.value = DashboardMetrics()
                _weeklyConsumption.value = emptyList()
            }
        }
    }

    private fun computeMetrics(records: List<FuelRecordEntity>): DashboardMetrics {
        if (records.isEmpty()) return DashboardMetrics()

        val sorted = records.sortedByDescending { it.date }
        var totalLiters = 0.0
        var totalKm = 0.0
        for (i in 0 until sorted.size - 1) {
            val curr = sorted[i]
            val prev = sorted[i + 1]
            val km = curr.mileage - prev.mileage
            if (km > 0) {
                totalLiters += curr.fuelAmount
                totalKm += km
            }
        }
        val consumption = if (totalKm > 0) (totalLiters / totalKm * 100).toFloat() else 0f

        val efficiency = (100f - (consumption - 6f) * 10f).coerceIn(0f, 100f).toInt()

        val totalCost = records.sumOf { it.totalCost }
        val sortedByDate = records.sortedBy { it.date }
        val totalKmAll = if (sortedByDate.size >= 2) {
            (sortedByDate.last().mileage - sortedByDate.first().mileage).coerceAtLeast(0.0)
        } else 0.0
        val rubPerKm = if (totalKmAll > 0) (totalCost / totalKmAll).toFloat() else 0f

        val sparkline = computeSparkline(records.sortedByDescending { it.date }.take(14))

        return DashboardMetrics(
            fillCount = records.size,
            consumption = consumption,
            efficiency = efficiency,
            rubPerKm = rubPerKm,
            sparklineData = sparkline,
        )
    }

    private fun computeSparkline(records: List<FuelRecordEntity>): List<Float> {
        if (records.size < 2) return emptyList()

        val sorted = records.sortedBy { it.date }
        val result = mutableListOf<Float>()

        for (i in 1 until sorted.size) {
            val curr = sorted[i]
            val prev = sorted[i - 1]
            val km = curr.mileage - prev.mileage
            val liters = curr.fuelAmount
            if (km > 0 && liters > 0) {
                val consumption = (liters / km * 100).toFloat()
                if (consumption in 2f..50f) {
                    result.add(consumption)
                }
            }
        }

        if (result.isEmpty()) {
            val totalLiters = sorted.sumOf { it.fuelAmount }
            val totalKm = (sorted.last().mileage - sorted.first().mileage)
                .coerceAtLeast(0.0)
            if (totalKm > 0) {
                val avg = (totalLiters / totalKm * 100).toFloat()
                result.addAll(List(3) { avg })
            }
        }

        return result
    }

    private fun computeWeeklyConsumption(
        records: List<FuelRecordEntity>,
        selectedVehicleId: Long?
    ): List<Pair<String, Float>> {
        if (records.isEmpty()) return emptyList()

        val filteredRecords = if (selectedVehicleId != null && selectedVehicleId > 0) {
            records.filter { it.vehicleId == selectedVehicleId }
        } else records

        val dayNames = arrayOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
        val grouped = mutableMapOf<Int, MutableList<Float>>()

        val sorted = filteredRecords.sortedBy { it.date }
        for (i in 1 until sorted.size) {
            val curr = sorted[i]
            val prev = sorted[i - 1]
            val km = curr.mileage - prev.mileage
            val liters = curr.fuelAmount
            if (km > 0 && liters > 0) {
                val consumption = (liters / km * 100).toFloat()
                if (consumption in 2f..50f) {
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = curr.date
                    val calDay = cal.get(Calendar.DAY_OF_WEEK)
                    val dayOfWeek = if (calDay == Calendar.SUNDAY) 7 else calDay - 1
                    grouped.getOrPut(dayOfWeek) { mutableListOf() }.add(consumption)
                }
            }
        }

        val result = mutableListOf<Pair<String, Float>>()
        for (day in 1..7) {
            val consumptions = grouped[day]
            if (consumptions != null && consumptions.isNotEmpty()) {
                val avg = consumptions.average().toFloat()
                val idx = (day - 1).coerceIn(0, 6)
                result.add(dayNames[idx] to avg)
            }
        }

        return result
    }
}
