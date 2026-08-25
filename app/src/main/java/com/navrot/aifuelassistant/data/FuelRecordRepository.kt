package com.navrot.aifuelassistant.data

import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import com.navrot.aifuelassistant.data.model.ReportPeriod
import kotlinx.coroutines.flow.Flow

interface FuelRecordRepository {
    fun getRecordsForPeriod(period: ReportPeriod): Flow<List<FuelRecordEntity>>
    fun getAll(): Flow<List<FuelRecordEntity>>
    fun getByVehicleId(vehicleId: Long): Flow<List<FuelRecordEntity>>
    suspend fun insert(record: FuelRecordEntity)
    suspend fun update(record: FuelRecordEntity)
    suspend fun delete(record: FuelRecordEntity)
    fun getByDateRange(startDateMillis: Long, endDateMillis: Long): Flow<List<FuelRecordEntity>>
    fun getByVehicleIdAndDateRange(vehicleId: Long, startDateMillis: Long, endDateMillis: Long): Flow<List<FuelRecordEntity>>
}
