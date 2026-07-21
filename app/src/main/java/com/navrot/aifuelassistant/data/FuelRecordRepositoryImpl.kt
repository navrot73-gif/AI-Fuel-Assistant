package com.navrot.aifuelassistant.data

import com.navrot.aifuelassistant.data.database.dao.FuelRecordDao
import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import kotlinx.coroutines.flow.Flow

class FuelRecordRepositoryImpl(
    private val dao: FuelRecordDao
) : FuelRecordRepository {

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
}