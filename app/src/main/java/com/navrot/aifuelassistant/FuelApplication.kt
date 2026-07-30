package com.navrot.aifuelassistant

import android.app.Application
import androidx.room.Room
import com.navrot.aifuelassistant.data.database.AppDatabase

class FuelApplication : Application() {

    // Единый экземпляр базы данных для всего приложения.
    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)
    }

    companion object {
        lateinit var instance: FuelApplication
            private set
    }
}
