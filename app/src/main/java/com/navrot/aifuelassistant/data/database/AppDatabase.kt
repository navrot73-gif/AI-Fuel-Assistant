package com.navrot.aifuelassistant.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.navrot.aifuelassistant.data.database.dao.FuelRecordDao
import com.navrot.aifuelassistant.data.database.dao.VehicleDao
import com.navrot.aifuelassistant.data.database.entity.FuelRecordEntity
import com.navrot.aifuelassistant.data.database.entity.VehicleEntity

@Database(entities = [VehicleEntity::class, FuelRecordEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {

    abstract fun vehicleDao(): VehicleDao       // БЕЗ suspend!
    abstract fun fuelRecordDao(): FuelRecordDao // БЕЗ suspend!
}