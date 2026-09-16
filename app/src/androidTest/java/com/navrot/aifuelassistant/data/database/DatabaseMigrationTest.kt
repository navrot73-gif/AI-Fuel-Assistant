package com.navrot.aifuelassistant.data.database

import android.content.ContentValues
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Интеграционные тесты миграций БД Room (версии 1 → 2 → 3 → 4 → 5 → 6 → 7).
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    private val testDbName = "migration-test-db"
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        instrumentation,
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @After
    fun tearDown() {
        instrumentation.targetContext.deleteDatabase(testDbName)
    }

    @Test
    fun testMigrationFrom1To2() {
        val vehicleId: Long
        val fuelRecordId: Long

        helper.createDatabase(testDbName, 1).apply {
            val vehicle = ContentValues().apply {
                put("name", "v1 car")
                put("brand", "Toyota")
                put("model", "Camry")
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
                put("notes", "v1 test note")
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
            db.query(
                "SELECT name, brand, model, year, fuelType, tankCapacity, currentMileage FROM vehicles WHERE id = ?",
                arrayOf(vehicleId.toString())
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("v1 car", cursor.getString(0))
            }
        }
    }

    @Test
    fun testMigrationFrom2To3() {
        val vehicleId: Long

        helper.createDatabase(testDbName, 2).apply {
            val vehicle = ContentValues().apply {
                put("name", "v2 car")
                put("brand", "Honda")
                put("model", "Civic")
                put("year", 2022)
                put("fuelType", "АИ-92")
                put("tankCapacity", 50.0)
                put("currentMileage", 15000.0)
            }
            vehicleId = insert("vehicles", 0, vehicle)

            val fuelRecord = ContentValues().apply {
                put("vehicleId", vehicleId)
                put("date", 1_722_700_000_000L)
                put("mileage", 15000.0)
                put("fuelAmount", 35.0)
                put("pricePerLiter", 55.0)
                put("totalCost", 1925.0)
                put("fuelType", "АИ-92")
                put("stationName", "v2 station")
                put("notes", "v2 test note")
                put("latitude", 55.7558)
                put("longitude", 37.6173)
            }
            insert("fuel_records", 0, fuelRecord)
            close()
        }

        helper.runMigrationsAndValidate(
            testDbName,
            3,
            true,
            DatabaseMigrations.MIGRATION_2_3
        ).use { db ->
            db.query(
                "SELECT name FROM vehicles WHERE id = ?",
                arrayOf(vehicleId.toString())
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("v2 car", cursor.getString(0))
            }
        }
    }

    @Test
    fun testMigrationFrom3To4() {
        val vehicleId: Long

        helper.createDatabase(testDbName, 3).apply {
            val vehicle = ContentValues().apply {
                put("name", "v3 car")
                put("brand", "Mazda")
                put("model", "CX-5")
                put("year", 2021)
                put("fuelType", "АИ-95")
                put("tankCapacity", 56.0)
                put("currentMileage", 25000.0)
            }
            vehicleId = insert("vehicles", 0, vehicle)
            close()
        }

        helper.runMigrationsAndValidate(
            testDbName,
            4,
            true,
            DatabaseMigrations.MIGRATION_3_4
        ).use { db ->
            db.query(
                "SELECT photo_url FROM vehicles WHERE id = ?",
                arrayOf(vehicleId.toString())
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertNull(cursor.getString(0))
            }
        }
    }

    @Test
    fun testMigrationFrom4To5() {
        val vehicleId: Long

        helper.createDatabase(testDbName, 4).apply {
            val vehicle = ContentValues().apply {
                put("name", "v4 car")
                put("brand", "Mazda")
                put("model", "CX-5")
                put("year", 2021)
                put("fuelType", "АИ-95")
                put("tankCapacity", 56.0)
                put("currentMileage", 25000.0)
                putNull("photo_url")
            }
            vehicleId = insert("vehicles", 0, vehicle)

            val fuelRecord = ContentValues().apply {
                put("vehicleId", vehicleId)
                put("date", 1_700_000_000_000L)
                put("mileage", 25000.0)
                put("fuelAmount", 45.0)
                put("pricePerLiter", 62.0)
                put("totalCost", 2790.0)
                put("fuelType", "АИ-95")
                put("stationName", "Gazpromneft")
                put("notes", "Test 4to5")
                putNull("latitude")
                putNull("longitude")
            }
            insert("fuel_records", 0, fuelRecord)
            close()
        }

        helper.runMigrationsAndValidate(
            testDbName,
            5,
            true,
            DatabaseMigrations.MIGRATION_4_5
        ).use { db ->
            db.query(
                "SELECT stationId, fullTank FROM fuel_records WHERE vehicleId = ?",
                arrayOf(vehicleId.toString())
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
                assertEquals(0, cursor.getInt(1))
            }
        }
    }

    @Test
    fun testMigrationFrom6To7() {
        val feedbackId = "f-123"

        helper.createDatabase(testDbName, 6).apply {
            val feedback = ContentValues().apply {
                put("id", feedbackId)
                put("recommendationId", "rec-1")
                put("recommendedStationId", 101L)
                put("chosenStationId", 101L)
                put("timestamp", 1_700_000_000_000L)
                put("routeStarted", 1)
                put("routeCompleted", 0)
                put("refuelCompleted", 1)
                put("signal", "ACCEPTED")
            }
            insert("recommendation_feedbacks", 0, feedback)
            close()
        }

        helper.runMigrationsAndValidate(
            testDbName,
            7,
            true,
            DatabaseMigrations.MIGRATION_6_7
        ).use { db ->
            db.query(
                "SELECT id, action, outcome, userConfirmed, source FROM recommendation_feedbacks WHERE id = ?",
                arrayOf(feedbackId)
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(feedbackId, cursor.getString(0))
                assertEquals("VIEWED", cursor.getString(1))
                assertEquals("UNKNOWN", cursor.getString(2))
                assertEquals(0, cursor.getInt(3))
                assertEquals("USER_CONFIRMED", cursor.getString(4))
            }
        }
    }

    @Test
    fun testAllMigrations() {
        val vehicleId: Long
        val fuelRecordId: Long

        helper.createDatabase(testDbName, 1).apply {
            val vehicle = ContentValues().apply {
                put("name", "Full migration car")
                put("brand", "Kia")
                put("model", "Rio")
                put("year", 2019)
                put("fuelType", "АИ-95")
                put("tankCapacity", 43.0)
                put("currentMileage", 50000.0)
            }
            vehicleId = insert("vehicles", 0, vehicle)

            val fuelRecord = ContentValues().apply {
                put("vehicleId", vehicleId)
                put("date", 1_710_000_000_000L)
                put("mileage", 50000.0)
                put("fuelAmount", 40.0)
                put("pricePerLiter", 58.0)
                put("totalCost", 2320.0)
                put("fuelType", "АИ-95")
                put("stationName", "All migration station")
                put("notes", "All migration note")
                putNull("latitude")
                putNull("longitude")
            }
            fuelRecordId = insert("fuel_records", 0, fuelRecord)
            close()
        }

        helper.runMigrationsAndValidate(
            testDbName,
            7,
            true,
            *DatabaseMigrations.ALL
        ).use { db ->
            db.query(
                "SELECT name, brand, model, year, fuelType, tankCapacity, currentMileage, photo_url FROM vehicles WHERE id = ?",
                arrayOf(vehicleId.toString())
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Full migration car", cursor.getString(0))
            }

            db.query(
                "SELECT vehicleId, date, mileage, fuelAmount, pricePerLiter, totalCost, fuelType, stationName, notes, stationId, fullTank FROM fuel_records WHERE id = ?",
                arrayOf(fuelRecordId.toString())
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(vehicleId, cursor.getLong(0))
                assertTrue(cursor.isNull(9))
                assertEquals(0, cursor.getInt(10))
            }
        }
    }
}
