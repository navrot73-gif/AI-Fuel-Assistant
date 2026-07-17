package com.navrot.aifuelassistant.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.navrot.aifuelassistant.data.database.dao.VehicleDao
import com.navrot.aifuelassistant.data.database.entity.VehicleEntity

@Database(
    entities = [VehicleEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
}