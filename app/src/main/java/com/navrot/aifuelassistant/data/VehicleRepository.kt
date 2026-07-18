package com.navrot.aifuelassistant.data

import com.navrot.aifuelassistant.data.database.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

interface VehicleRepository {
    fun getAllVehicles(): Flow<List<VehicleEntity>>
    suspend fun getVehicleById(id: Long): VehicleEntity?
    suspend fun insertVehicle(vehicle: VehicleEntity)
    suspend fun updateVehicle(vehicle: VehicleEntity)
    suspend fun deleteVehicle(vehicle: VehicleEntity)
}