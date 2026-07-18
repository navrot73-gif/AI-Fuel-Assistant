package com.navrot.aifuelassistant

import android.app.Application
import androidx.room.Room
import com.navrot.aifuelassistant.data.database.AppDatabase

class FuelApplication : Application() {

    // Делаем базу данных публичной (val вместо private val)
    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "ai_fuel_assistant_db"
        ).build()
    }

    companion object {
        lateinit var instance: FuelApplication
            private set
    }
}