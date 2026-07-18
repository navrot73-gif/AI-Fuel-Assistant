package com.navrot.aifuelassistant.data.database.dao

import androidx.room.*
import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelRecordDao {

    @Query("SELECT * FROM fuel_records WHERE vehicleId = :vehicleId ORDER BY date DESC")
    fun getByVehicleId(vehicleId: Long): Flow<List<FuelRecordEntity>>

    @Query("SELECT * FROM fuel_records ORDER BY date DESC")
    fun getAll(): Flow<List<FuelRecordEntity>>

    @Query("SELECT * FROM fuel_records WHERE id = :id")
    suspend fun getById(id: Long): FuelRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: FuelRecordEntity): Long

    @Update
    suspend fun update(record: FuelRecordEntity)

    @Delete
    suspend fun delete(record: FuelRecordEntity)

    @Query("DELETE FROM fuel_records WHERE vehicleId = :vehicleId")
    suspend fun deleteByVehicleId(vehicleId: Long)
}