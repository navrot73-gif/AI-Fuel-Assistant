package com.navrot.aifuelassistant.data

import com.navrot.aifuelassistant.data.database.dao.FuelRecordDao
import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import com.navrot.aifuelassistant.data.model.ReportPeriod
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FuelRecordRepositoryImpl @Inject constructor(
    private val dao: FuelRecordDao
) : FuelRecordRepository {

    override fun getRecordsForPeriod(period: ReportPeriod): Flow<List<FuelRecordEntity>> {
        val (startMillis, endMillis) = period.getPeriodRange()
        return dao.getByDateRange(startMillis, endMillis)
    }

    override fun getAll(): Flow<List<FuelRecordEntity>> =
        dao.getAll()

    override fun getByVehicleId(vehicleId: Long): Flow<List<FuelRecordEntity>> =
        dao.getByVehicleId(vehicleId)

    override suspend fun insert(record: FuelRecordEntity) =
        dao.insert(record)

    override suspend fun update(record: FuelRecordEntity) =
        dao.update(record)

    override suspend fun delete(record: FuelRecordEntity) =
        dao.delete(record)

    override fun getByDateRange(startDateMillis: Long, endDateMillis: Long): Flow<List<FuelRecordEntity>> =
        dao.getByDateRange(startDateMillis, endDateMillis)

    override fun getByVehicleIdAndDateRange(
        vehicleId: Long,
        startDateMillis: Long,
        endDateMillis: Long
    ): Flow<List<FuelRecordEntity>> =
        dao.getByVehicleIdAndDateRange(vehicleId, startDateMillis, endDateMillis)
}