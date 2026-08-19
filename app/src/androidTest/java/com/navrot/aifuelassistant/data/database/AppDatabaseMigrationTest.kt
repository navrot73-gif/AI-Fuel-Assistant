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
    fun migrate1To2_removesForeignKeyAndPreservesData() {
        // В v1 у fuel_records был FK vehicleId → vehicles(id) ON DELETE CASCADE.
        // В v2 FK должен быть удалён, но данные сохранены.
        val vehicleId: Long
        val fuelRecordId: Long

        helper.createDatabase(testDbName, 1).apply {
            val vehicle = ContentValues().apply {
                put("name", "v1 car")
                put("brand", "Brand")
                put("model", "Model")
                put("year", 2020)
                put("fuelType", "АИ-95")
                put("tankCapacity", 60.0)
                put("currentMileage", 10000.0)
            }
            vehicleId = insert("vehicles", 0, vehicle)
            assertTrue(vehicleId > 0)

            val fuelRecord = ContentValues().apply {
                put("vehicleId", vehicleId)
                put("date", 1_700_000_000_000L)
                put("mileage", 10000.0)
                put("fuelAmount", 40.0)
                put("pricePerLiter", 60.0)
                put("totalCost", 2400.0)
                put("fuelType", "АИ-95")
                put("stationName", "v1 station")
                put("notes", "v1 test")
                putNull("latitude")
                putNull("longitude")
            }
            fuelRecordId = insert("fuel_records", 0, fuelRecord)
            assertTrue(fuelRecordId > 0)
            close()
        }

        helper.runMigrationsAndValidate(
            testDbName,
            2,
            true,
            DatabaseMigrations.MIGRATION_1_2
        ).use { db ->
            // Данные должны сохраниться
            db.query(
                "SELECT vehicleId, stationName, fuelAmount FROM fuel_records WHERE id = ?",
                arrayOf(fuelRecordId.toString())
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(vehicleId, cursor.getLong(0))
                assertEquals("v1 station", cursor.getString(1))
                assertEquals(40.0, cursor.getDouble(2), 0.001)
            }

            // FK должен отсутствовать в v2
            db.query("PRAGMA foreign_key_list(fuel_records)").use { cursor ->
                assertEquals(
                    "v2 should have NO foreign keys on fuel_records",
                    0, cursor.count
                )
            }
        }
    }

    @Test
    fun migrate2To3_preservesFuelRecordsAndAddsForeignKey() {
        val vehicleId: Long
        val fuelRecordId: Long
        val secondRecordId: Long

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

            // Add a second record for cascade delete verification
            val secondRecord = ContentValues().apply {
                put("vehicleId", vehicleId)
                put("date", 1_722_800_000_000L)
                put("mileage", 10500.0)
                put("fuelAmount", 35.0)
                put("pricePerLiter", 61.0)
                put("totalCost", 2135.0)
                put("fuelType", "АИ-92")
                put("stationName", "Second station")
                put("notes", "second record")
                putNull("latitude")
                putNull("longitude")
            }
            secondRecordId = insert("fuel_records", 0, secondRecord)
            assertTrue(secondRecordId > 0)
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

            // Note: CASCADE delete is a runtime behaviour enforced by Room
            // (which enables PRAGMA foreign_keys = ON automatically).
            // It cannot be tested here because MigrationTestHelper runs inside
            // a transaction, and SQLite forbids changing foreign_keys pragma
            // mid-transaction. The FK definition itself is verified above.
        }
    }

    @Test
    fun migrate2To3_preservesOrphanRecords() {
        // In v2 there was no FK, so orphan records could exist.
        // Migration should NOT fail — data integrity was not enforced before.
        helper.createDatabase(testDbName, 2).apply {
            // Insert only a fuel record — NO parent vehicle
            val orphanRecord = ContentValues().apply {
                put("vehicleId", 999)
                put("date", 1_722_700_000_000L)
                put("mileage", 10000.0)
                put("fuelAmount", 40.0)
                put("pricePerLiter", 60.0)
                put("totalCost", 2400.0)
                put("fuelType", "АИ-95")
                put("stationName", "Orphan station")
                put("notes", "orphan record")
                putNull("latitude")
                putNull("longitude")
            }
            val orphanId = insert("fuel_records", 0, orphanRecord)
            assertTrue(orphanId > 0)
            close()
        }

        // Migration should succeed
        helper.runMigrationsAndValidate(
            testDbName, 3, true, DatabaseMigrations.MIGRATION_2_3
        ).use { db ->
            // Orphan record should survive (FK added but not validated on existing data)
            db.query("SELECT COUNT(*) FROM fuel_records WHERE vehicleId = 999")
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(
                        "Orphan record should survive migration (FK not retroactively validated)",
                        1, cursor.getInt(0)
                    )
                }
        }
    }

    @Test
    fun migrate2To3_preservesIndexOnVehicleId() {
        helper.createDatabase(testDbName, 2).apply {
            val vehicle = ContentValues().apply {
                put("name", "Idx car")
                put("brand", "B")
                put("model", "M")
                put("year", 2022)
                put("fuelType", "АИ-95")
                put("tankCapacity", 50.0)
                put("currentMileage", 5000.0)
            }
            val vid = insert("vehicles", 0, vehicle)

            val record = ContentValues().apply {
                put("vehicleId", vid)
                put("date", 1000L)
                put("mileage", 5000.0)
                put("fuelAmount", 30.0)
                put("pricePerLiter", 60.0)
                put("totalCost", 1800.0)
                put("fuelType", "АИ-95")
                put("stationName", "Idx station")
                put("notes", "")
                putNull("latitude")
                putNull("longitude")
            }
            insert("fuel_records", 0, record)
            close()
        }

        helper.runMigrationsAndValidate(
            testDbName, 3, true, DatabaseMigrations.MIGRATION_2_3
        ).use { db ->
            db.query("PRAGMA index_list(fuel_records)").use { cursor ->
                var foundIndex = false
                while (cursor.moveToNext()) {
                    val indexName = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    if (indexName == "index_fuel_records_vehicleId") {
                        foundIndex = true
                        break
                    }
                }
                assertTrue("Index on vehicleId should exist after migration", foundIndex)
            }
        }
    }
}
