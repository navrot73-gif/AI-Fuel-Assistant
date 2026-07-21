package com.navrot.aifuelassistant.data

import com.navrot.aifuelassistant.data.database.dao.VehicleDao
import com.navrot.aifuelassistant.data.database.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

class VehicleRepositoryImpl(
    private val dao: VehicleDao
) : VehicleRepository {

    override fun getAllVehicles(): Flow<List<VehicleEntity>> =
        dao.getAllVehicles()

    override suspend fun getVehicleById(id: Long): VehicleEntity? =
        dao.getVehicleById(id)

    override suspend fun insertVehicle(vehicle: VehicleEntity) =
        dao.insertVehicle(vehicle)

    override suspend fun updateVehicle(vehicle: VehicleEntity) =
        dao.updateVehicle(vehicle)

    override suspend fun deleteVehicle(vehicle: VehicleEntity) =
        dao.deleteVehicle(vehicle)
}