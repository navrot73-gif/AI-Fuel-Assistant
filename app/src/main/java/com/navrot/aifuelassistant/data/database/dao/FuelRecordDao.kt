package com.navrot.aifuelassistant.data.database.dao

import androidx.room.*
import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelRecordDao {
    @Query("SELECT * FROM fuel_records ORDER BY date DESC")
    fun getAll(): Flow<List<FuelRecordEntity>>

    @Query("SELECT * FROM fuel_records WHERE vehicleId = :vehicleId ORDER BY date DESC")
    fun getByVehicleId(vehicleId: Long): Flow<List<FuelRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: FuelRecordEntity)

    @Update
    suspend fun update(record: FuelRecordEntity)

    @Delete
    suspend fun delete(record: FuelRecordEntity)

    @Query("SELECT * FROM fuel_records WHERE date >= :startDateMillis AND date <= :endDateMillis ORDER BY date DESC")
    fun getByDateRange(startDateMillis: Long, endDateMillis: Long): Flow<List<FuelRecordEntity>>

    @Query("SELECT * FROM fuel_records WHERE vehicleId = :vehicleId AND date >= :startDateMillis AND date <= :endDateMillis ORDER BY date DESC")
    fun getByVehicleIdAndDateRange(vehicleId: Long, startDateMillis: Long, endDateMillis: Long): Flow<List<FuelRecordEntity>>
}