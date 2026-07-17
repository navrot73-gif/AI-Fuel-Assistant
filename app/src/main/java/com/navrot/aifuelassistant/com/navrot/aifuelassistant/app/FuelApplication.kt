package com.navrot.aifuelassistant.app

import android.app.Application
import androidx.room.Room
import com.navrot.aifuelassistant.data.database.AppDatabase

class FuelApplication : Application() {

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "fuel_database"
        ).build()
    }

    companion object {
        lateinit var instance: FuelApplication
            private set
    }
}