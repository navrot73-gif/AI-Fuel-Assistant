package com.navrot.aifuelassistant.data

import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import kotlinx.coroutines.flow.Flow

interface FuelRecordRepository {
    fun getByVehicleId(vehicleId: Long): Flow<List<FuelRecordEntity>>
    suspend fun insert(record: FuelRecordEntity)
    suspend fun update(record: FuelRecordEntity)
    suspend fun delete(record: FuelRecordEntity)
}
