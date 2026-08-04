package com.navrot.aifuelassistant.data.database

import android.content.ContentValues
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
        val vehicleId: Long
        val fuelRecordId: Long

        // Create database with version 2
        helper.createDatabase(testDbName, 2).apply {
            val vehicle = ContentValues().apply {
                put("name", "Test car")
                put("brand", "Test")
                put("model", "Model")
                put("year", 2020)
                put("fuelType", "АИ-95")
                put("tankCapacity", 60.0)
                put("currentMileage", 10000.0)
                // Note: currentFuelLevel and averageConsumption are NOT in version 2 schema
            }
            vehicleId = insert("vehicles", 0, vehicle)
            assertTrue(vehicleId > 0)

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
            fuelRecordId = insert("fuel_records", 0, fuelRecord)
            assertTrue(fuelRecordId > 0)
            close()
        }

        // Run migration to version 3
        helper.runMigrationsAndValidate(
            testDbName,
            3,
            true,
            DatabaseMigrations.MIGRATION_2_3
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
}
