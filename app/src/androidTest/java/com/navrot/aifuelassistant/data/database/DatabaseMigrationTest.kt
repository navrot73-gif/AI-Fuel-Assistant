package com.navrot.aifuelassistant.data.database

import android.content.ContentValues
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Интеграционные тесты миграций БД Room (версии 1 → 2 → 3).
 *
 * Зачем нужны эти тесты:
 * После удаления `fallbackToDestructiveMigration()` из AppModule миграции стали критически
 * важной частью приложения. Если миграция написана неверно или отсутствует, обновление
 * приложения приведёт к падению базы данных или безвозвратной потере пользовательских данных.
 *
 * Данный класс проверяет:
 * 1. [testMigrationFrom1To2]: корректность миграции с версии 1 на 2 (удаление FK, сохранение данных).
 * 2. [testMigrationFrom2To3]: корректность миграции с версии 2 на 3 (добавление FK, создание индекса, сохранение данных).
 * 3. [testAllMigrations]: сквозную миграцию 1 → 2 → 3 (*DatabaseMigrations.ALL), гарантируя соответствие итоговой схемы v3.
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

    /**
     * Тест 1: Миграция 1 → 2.
     * В v1 у fuel_records был FK vehicleId → vehicles(id) ON DELETE CASCADE.
     * В v2 FK удалён, но все данные в таблицах vehicles и fuel_records сохраняются.
     */
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
            // Проверка сохранения данных vehicles
            db.query(
                "SELECT name, brand, model, year, fuelType, tankCapacity, currentMileage FROM vehicles WHERE id = ?",
                arrayOf(vehicleId.toString())
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("v1 car", cursor.getString(0))
                assertEquals("Toyota", cursor.getString(1))
                assertEquals("Camry", cursor.getString(2))
                assertEquals(2020, cursor.getInt(3))
                assertEquals("АИ-95", cursor.getString(4))
                assertEquals(60.0, cursor.getDouble(5), 0.001)
                assertEquals(10000.0, cursor.getDouble(6), 0.001)
            }

            // Проверка сохранения данных fuel_records
            db.query(
                "SELECT vehicleId, date, mileage, fuelAmount, pricePerLiter, totalCost, fuelType, stationName, notes FROM fuel_records WHERE id = ?",
                arrayOf(fuelRecordId.toString())
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(vehicleId, cursor.getLong(0))
                assertEquals(1_700_000_000_000L, cursor.getLong(1))
                assertEquals(10000.0, cursor.getDouble(2), 0.001)
                assertEquals(40.0, cursor.getDouble(3), 0.001)
                assertEquals(60.0, cursor.getDouble(4), 0.001)
                assertEquals(2400.0, cursor.getDouble(5), 0.001)
                assertEquals("АИ-95", cursor.getString(6))
                assertEquals("v1 station", cursor.getString(7))
                assertEquals("v1 test note", cursor.getString(8))
            }

            // Проверка: в v2 у fuel_records отсутствует FK
            db.query("PRAGMA foreign_key_list(fuel_records)").use { cursor ->
                assertEquals("v2 should have no foreign keys on fuel_records", 0, cursor.count)
            }
        }
    }

    /**
     * Тест 2: Миграция 2 → 3.
     * В v3 возвращается FK vehicleId → vehicles(id) ON DELETE CASCADE и создаётся индекс index_fuel_records_vehicleId.
     * Проверяем сохранение данных во всех таблицах, наличие FK и наличие индекса.
     */
    @Test
    fun testMigrationFrom2To3() {
        val vehicleId: Long
        val fuelRecordId: Long

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
            assertTrue(vehicleId > 0)

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
            fuelRecordId = insert("fuel_records", 0, fuelRecord)
            assertTrue(fuelRecordId > 0)
            close()
        }

        helper.runMigrationsAndValidate(
            testDbName,
            3,
            true,
            DatabaseMigrations.MIGRATION_2_3
        ).use { db ->
            // Проверка сохранения данных vehicles
            db.query(
                "SELECT name, brand, model, year, fuelType, tankCapacity, currentMileage FROM vehicles WHERE id = ?",
                arrayOf(vehicleId.toString())
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("v2 car", cursor.getString(0))
                assertEquals("Honda", cursor.getString(1))
                assertEquals("Civic", cursor.getString(2))
                assertEquals(2022, cursor.getInt(3))
                assertEquals("АИ-92", cursor.getString(4))
                assertEquals(50.0, cursor.getDouble(5), 0.001)
                assertEquals(15000.0, cursor.getDouble(6), 0.001)
            }

            // Проверка сохранения данных fuel_records (включая latitude/longitude)
            db.query(
                "SELECT vehicleId, date, mileage, fuelAmount, pricePerLiter, totalCost, fuelType, stationName, notes, latitude, longitude FROM fuel_records WHERE id = ?",
                arrayOf(fuelRecordId.toString())
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(vehicleId, cursor.getLong(0))
                assertEquals(1_722_700_000_000L, cursor.getLong(1))
                assertEquals(15000.0, cursor.getDouble(2), 0.001)
                assertEquals(35.0, cursor.getDouble(3), 0.001)
                assertEquals(55.0, cursor.getDouble(4), 0.001)
                assertEquals(1925.0, cursor.getDouble(5), 0.001)
                assertEquals("АИ-92", cursor.getString(6))
                assertEquals("v2 station", cursor.getString(7))
                assertEquals("v2 test note", cursor.getString(8))
                assertEquals(55.7558, cursor.getDouble(9), 0.0001)
                assertEquals(37.6173, cursor.getDouble(10), 0.0001)
            }

            // Проверка наличия Foreign Key на vehicles(id)
            db.query("PRAGMA foreign_key_list(fuel_records)").use { cursor ->
                var foundFk = false
                while (cursor.moveToNext()) {
                    val table = cursor.getString(cursor.getColumnIndexOrThrow("table"))
                    val fromCol = cursor.getString(cursor.getColumnIndexOrThrow("from"))
                    val toCol = cursor.getString(cursor.getColumnIndexOrThrow("to"))
                    if (table == "vehicles" && fromCol == "vehicleId" && toCol == "id") {
                        foundFk = true
                        break
                    }
                }
                assertTrue("Foreign Key vehicleId -> vehicles(id) should exist in v3", foundFk)
            }

            // Проверка наличия индекса index_fuel_records_vehicleId
            db.query("PRAGMA index_list(fuel_records)").use { cursor ->
                var foundIndex = false
                while (cursor.moveToNext()) {
                    val indexName = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    if (indexName == "index_fuel_records_vehicleId") {
                        foundIndex = true
                        break
                    }
                }
                assertTrue("Index index_fuel_records_vehicleId should exist in v3", foundIndex)
            }
        }
    }

    /**
     * Тест 3: Сквозная миграция 1 → 2 → 3 через DatabaseMigrations.ALL.
     * Создаёт БД версии 1, заполняет её данными, выполняет все миграции до v3 и валидирует схему v3.
     */
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
            assertTrue(vehicleId > 0)

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
            assertTrue(fuelRecordId > 0)
            close()
        }

        // Запуск всех миграций 1->2->3 и автоматическая валидация соответствия v3.json
        helper.runMigrationsAndValidate(
            testDbName,
            3,
            true,
            *DatabaseMigrations.ALL
        ).use { db ->
            // Проверка сохранности данных vehicles
            db.query(
                "SELECT name, brand, model, year, fuelType, tankCapacity, currentMileage FROM vehicles WHERE id = ?",
                arrayOf(vehicleId.toString())
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Full migration car", cursor.getString(0))
                assertEquals("Kia", cursor.getString(1))
                assertEquals("Rio", cursor.getString(2))
                assertEquals(2019, cursor.getInt(3))
                assertEquals("АИ-95", cursor.getString(4))
                assertEquals(43.0, cursor.getDouble(5), 0.001)
                assertEquals(50000.0, cursor.getDouble(6), 0.001)
            }

            // Проверка сохранности данных fuel_records
            db.query(
                "SELECT vehicleId, date, mileage, fuelAmount, pricePerLiter, totalCost, fuelType, stationName, notes FROM fuel_records WHERE id = ?",
                arrayOf(fuelRecordId.toString())
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(vehicleId, cursor.getLong(0))
                assertEquals(1_710_000_000_000L, cursor.getLong(1))
                assertEquals(50000.0, cursor.getDouble(2), 0.001)
                assertEquals(40.0, cursor.getDouble(3), 0.001)
                assertEquals(58.0, cursor.getDouble(4), 0.001)
                assertEquals(2320.0, cursor.getDouble(5), 0.001)
                assertEquals("АИ-95", cursor.getString(6))
                assertEquals("All migration station", cursor.getString(7))
                assertEquals("All migration note", cursor.getString(8))
            }
        }
    }
}
