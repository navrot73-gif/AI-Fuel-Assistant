package com.navrot.aifuelassistant.domain.usecase

import com.navrot.aifuelassistant.data.FuelRecordRepository
import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import com.navrot.aifuelassistant.data.model.FuelReport
import com.navrot.aifuelassistant.data.model.ReportPeriod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetFuelReportUseCase @Inject constructor(
    private val repository: FuelRecordRepository
) {
    fun execute(
        period: ReportPeriod,
        vehicleId: Long? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): Flow<FuelReport> {
        val (startMillis, endMillis) = period.getPeriodRange(nowMillis)
        return execute(startMillis, endMillis, vehicleId)
    }

    fun execute(
        startDateMillis: Long,
        endDateMillis: Long,
        vehicleId: Long? = null
    ): Flow<FuelReport> {
        val flow = if (vehicleId != null && vehicleId > 0) {
            repository.getByVehicleIdAndDateRange(vehicleId, startDateMillis, endDateMillis)
        } else {
            repository.getByDateRange(startDateMillis, endDateMillis)
        }
        return flow.map { records ->
            calculateReport(records, startDateMillis, endDateMillis)
        }
    }

    fun calculateReport(
        records: List<FuelRecordEntity>,
        periodStartEpochMillis: Long,
        periodEndEpochMillis: Long
    ): FuelReport {
        if (records.isEmpty()) {
            return FuelReport(
                totalRefuels = 0,
                totalLiters = 0.0,
                totalCost = 0.0,
                totalDistanceKm = 0.0,
                averageConsumptionPer100Km = 0.0,
                averagePricePerLiter = 0.0,
                costPerKm = 0.0,
                periodStartEpochMillis = periodStartEpochMillis,
                periodEndEpochMillis = periodEndEpochMillis
            )
        }

        val totalRefuels = records.size
        val totalLiters = records.sumOf { it.fuelAmount }
        val totalCost = records.sumOf { it.totalCost }

        val groupedByVehicle = records.groupBy { it.vehicleId }
        var totalDistanceKm = 0.0
        var totalLitersForConsumption = 0.0
        var totalKmForConsumption = 0.0

        for ((_, vehicleRecords) in groupedByVehicle) {
            if (vehicleRecords.size >= 2) {
                val sorted = vehicleRecords.sortedBy { it.date }
                val vehicleDist = (sorted.last().mileage - sorted.first().mileage).coerceAtLeast(0.0)
                totalDistanceKm += vehicleDist

                for (i in 1 until sorted.size) {
                    val prev = sorted[i - 1]
                    val curr = sorted[i]
                    val km = curr.mileage - prev.mileage
                    if (km > 0 && curr.fuelAmount > 0) {
                        totalKmForConsumption += km
                        totalLitersForConsumption += curr.fuelAmount
                    }
                }
            }
        }

        val averageConsumptionPer100Km = if (totalKmForConsumption > 0) {
            (totalLitersForConsumption / totalKmForConsumption) * 100.0
        } else 0.0

        val averagePricePerLiter = if (totalLiters > 0) {
            totalCost / totalLiters
        } else 0.0

        val costPerKm = if (totalDistanceKm > 0) {
            totalCost / totalDistanceKm
        } else 0.0

        return FuelReport(
            totalRefuels = totalRefuels,
            totalLiters = totalLiters,
            totalCost = totalCost,
            totalDistanceKm = totalDistanceKm,
            averageConsumptionPer100Km = averageConsumptionPer100Km,
            averagePricePerLiter = averagePricePerLiter,
            costPerKm = costPerKm,
            periodStartEpochMillis = periodStartEpochMillis,
            periodEndEpochMillis = periodEndEpochMillis
        )
    }
}
