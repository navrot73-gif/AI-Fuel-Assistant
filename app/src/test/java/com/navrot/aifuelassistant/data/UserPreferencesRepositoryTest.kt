package com.navrot.aifuelassistant.data

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class UserPreferencesRepositoryTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var repository: UserPreferencesRepository

    @Before
    fun setUp() {
        val testFile = tmpFolder.newFile("test_user_preferences.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile }
        )
        repository = UserPreferencesRepository(dataStore)
    }

    @Test
    fun `default isDarkMode is false`() = runTest {
        val mode = repository.isDarkMode.first()
        assertFalse(mode)
    }

    @Test
    fun `setDarkMode updates isDarkMode flow`() = runTest {
        repository.setDarkMode(true)
        val mode = repository.isDarkMode.first()
        assertTrue(mode)
    }

    @Test
    fun `setCachedCity updates cachedCity flow`() = runTest {
        repository.setCachedCity("Челябинск")
        val city = repository.cachedCity.first()
        assertEquals("Челябинск", city)
    }

    @Test
    fun `reportUserPrice and clearUserPrice work correctly`() = runTest {
        repository.reportUserPrice(1, "АИ-95", 54.5)

        val prices = repository.userPrices.first()
        assertEquals(1, prices.size)
        assertEquals(54.5, prices[Pair(1, "АИ-95")]!!, 0.001)

        repository.clearUserPrice(1, "АИ-95")
        val pricesAfterClear = repository.userPrices.first()
        assertTrue(pricesAfterClear.isEmpty())
    }

    @Test
    fun `clearAllUserPrices clears all price keys`() = runTest {
        repository.reportUserPrice(1, "АИ-95", 54.5)
        repository.reportUserPrice(2, "АИ-92", 50.0)

        repository.clearAllUserPrices()
        val prices = repository.userPrices.first()
        assertTrue(prices.isEmpty())
    }

    @Test
    fun `migration from SharedPreferences preserves data`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sharedPrefsMap = context.getSharedPreferences("map_prefs", Context.MODE_PRIVATE)
        sharedPrefsMap.edit().putBoolean("is_dark_mode", true).commit()

        val sharedPrefsPrices = context.getSharedPreferences("user_prices", Context.MODE_PRIVATE)
        sharedPrefsPrices.edit().putString("price:1:АИ-95", "55.0").commit()

        val dataStore = PreferenceDataStoreFactory.create(
            migrations = listOf(
                SharedPreferencesMigration(context, "map_prefs"),
                SharedPreferencesMigration(context, "user_prices")
            ),
            scope = testScope,
            produceFile = { tmpFolder.newFile("migrated.preferences_pb") }
        )
        val migratedRepo = UserPreferencesRepository(dataStore)

        assertTrue(migratedRepo.isDarkMode.first())
        val prices = migratedRepo.userPrices.first()
        assertEquals(55.0, prices[Pair(1, "АИ-95")]!!, 0.001)
    }
}
