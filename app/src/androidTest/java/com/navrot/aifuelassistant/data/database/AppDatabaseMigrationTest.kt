package com.navrot.aifuelassistant.data.database

import android.content.ContentValues
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val testDbName = "migration-test"
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var helper: MigrationTestHelper

    @Before
    fun setUp() {
        helper = MigrationTestHelper(
            instrumentation,
            AppDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory()
        )
    }

    @After
    fun tearDown() {
        instrumentation.targetContext.deleteDatabase(testDbName)
    }

    @Test
    fun migrate2To3_preservesFuelRecordsAndAddsForeignKey() {
        helper.createDatabase(testDbName, 2).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS vehicles (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    brand TEXT NOT NULL,
                    model TEXT NOT NULL,
                    year INTEGER NOT NULL,
                    fuelType TEXT NOT NULL,
                    tankCapacity REAL NOT NULL,
                    currentFuelLevel REAL NOT NULL,
                    currentMileage REAL NOT NULL,
                    averageConsumption REAL NOT NULL
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS fuel_records (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    vehicleId INTEGER NOT NULL,
                    date INTEGER NOT NULL,
                    mileage REAL NOT NULL,
                    fuelAmount REAL NOT NULL,
                    pricePerLiter REAL NOT NULL,
                    totalCost REAL NOT NULL,
                    fuelType TEXT NOT NULL,
                    stationName TEXT NOT NULL,
                    notes TEXT NOT NULL,
                    latitude REAL,
                    longitude REAL
                )
                """.trimIndent()
            )

            val vehicle = ContentValues().apply {
                put("name", "Test car")
                put("brand", "Test")
                put("model", "Model")
                put("year", 2020)
                put("fuelType", "АИ-95")
                put("tankCapacity", 60.0)
                put("currentFuelLevel", 30.0)
                put("currentMileage", 10000.0)
                put("averageConsumption", 8.0)
            }
            val vehicleId = insert("vehicles", 0, vehicle)

            val fuelRecord = ContentValues().apply {
                put("vehicleId", vehicleId)
                put("date", 1_722_700_000_000L)
                put("mileage", 10000.0)
                put("fuelAmount", 40.0)
                put("pricePerLiter", 60.0)
                put("totalCost", 2400.0)
                put("fuelType", "АИ-95")
                put("stationName", "Test station")
                put("notes", "migration test")
                putNull("latitude")
                putNull("longitude")
            }
            val fuelRecordId = insert("fuel_records", 0, fuelRecord)
            assertTrue(fuelRecordId > 0)

            close()
        }

        helper.runMigrationsAndValidate(
            testDbName,
            3,
            true,
            MIGRATION_2_3
        ).use { db ->
            db.query(
                "SELECT vehicleId, stationName, fuelAmount, totalCost FROM fuel_records WHERE id = ?",
                arrayOf(fuelRecordId.toString())
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(vehicleId, cursor.getLong(0))
                assertEquals("Test station", cursor.getString(1))
                assertEquals(40.0, cursor.getDouble(2), 0.001)
                assertEquals(2400.0, cursor.getDouble(3), 0.001)
            }

            db.query("PRAGMA foreign_key_list(fuel_records)").use { cursor ->
                var foundVehicleForeignKey = false
                while (cursor.moveToNext()) {
                    val referencedTable = cursor.getString(cursor.getColumnIndexOrThrow("table"))
                    val fromColumn = cursor.getString(cursor.getColumnIndexOrThrow("from"))
                    val toColumn = cursor.getString(cursor.getColumnIndexOrThrow("to"))
                    if (referencedTable == "vehicles" && fromColumn == "vehicleId" && toColumn == "id") {
                        foundVehicleForeignKey = true
                        break
                    }
                }
                assertTrue("vehicleId foreign key should reference vehicles.id", foundVehicleForeignKey)
            }
        }
    }

    companion object {
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS fuel_records_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        vehicleId INTEGER NOT NULL,
                        date INTEGER NOT NULL,
                        mileage REAL NOT NULL,
                        fuelAmount REAL NOT NULL,
                        pricePerLiter REAL NOT NULL,
                        totalCost REAL NOT NULL,
                        fuelType TEXT NOT NULL,
                        stationName TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        latitude REAL,
                        longitude REAL,
                        FOREIGN KEY(vehicleId) REFERENCES vehicles(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    INSERT INTO fuel_records_new (
                        id, vehicleId, date, mileage, fuelAmount, pricePerLiter,
                        totalCost, fuelType, stationName, notes, latitude, longitude
                    )
                    SELECT id, vehicleId, date, mileage, fuelAmount, pricePerLiter,
                        totalCost, fuelType, stationName, notes, latitude, longitude
                    FROM fuel_records
                    """.trimIndent()
                )
                database.execSQL("DROP TABLE fuel_records")
                database.execSQL("ALTER TABLE fuel_records_new RENAME TO fuel_records")
            }
        }
    }
}
