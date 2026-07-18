package com.navrot.aifuelassistant.data

import com.navrot.aifuelassistant.data.database.dao.VehicleDao
import com.navrot.aifuelassistant.data.database.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

class VehicleRepositoryImpl(
    private val vehicleDao: VehicleDao
) : VehicleRepository {

    override fun getAllVehicles(): Flow<List<VehicleEntity>> {
        return vehicleDao.getAllVehicles()
    }

    override suspend fun getVehicleById(id: Long): VehicleEntity? {
        return vehicleDao.getVehicleById(id)
    }

    override suspend fun insertVehicle(vehicle: VehicleEntity) {
        vehicleDao.insertVehicle(vehicle)
    }

    override suspend fun updateVehicle(vehicle: VehicleEntity) {
        vehicleDao.updateVehicle(vehicle)
    }

    override suspend fun deleteVehicle(vehicle: VehicleEntity) {
        vehicleDao.deleteVehicle(vehicle)
    }
}