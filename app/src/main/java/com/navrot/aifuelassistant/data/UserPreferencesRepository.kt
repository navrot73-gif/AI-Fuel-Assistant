package com.navrot.aifuelassistant.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFERENCES_NAME = "app_user_preferences"

val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = PREFERENCES_NAME,
    produceMigrations = { context ->
        listOf(
            SharedPreferencesMigration(context, "map_prefs"),
            SharedPreferencesMigration(context, "user_prices")
        )
    }
)

/**
 * Асинхронное типобезопасное хранилище несекретных настроек приложения на базе Jetpack DataStore.
 *
 * Хранит:
 * - Тёмую/светлую тему (is_dark_mode)
 * - Кэш города (cached_city)
 * - Пользовательские цены на топливо (price:{stationId}:{fuelType})
 */
@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    // Вторичный конструктор для удобного создания в тестах/контексте
    constructor(@ApplicationContext context: Context) : this(context.userPreferencesDataStore)

    companion object {
        private const val TAG = "UserPreferencesRepo"
        val KEY_IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val KEY_CACHED_CITY = stringPreferencesKey("cached_city")
        val KEY_MAP_ENGINE = stringPreferencesKey("map_engine")
        private const val USER_PRICE_PREFIX = "price:"
        const val ENGINE_OSMDROID = "osmdroid"
        const val ENGINE_MAPLIBRE = "maplibre"
    }

    val mapEngine: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.tag(TAG).e(exception, "Error reading map engine preference.")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_MAP_ENGINE] ?: ENGINE_OSMDROID
        }

    val isDarkMode: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.tag(TAG).e(exception, "Error reading dark mode preference.")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_IS_DARK_MODE] ?: false
        }

    val cachedCity: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.tag(TAG).e(exception, "Error reading cached city preference.")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_CACHED_CITY]
        }

    val userPrices: Flow<Map<Pair<Int, String>, Double>> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.tag(TAG).e(exception, "Error reading user prices preference.")
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            parseUserPricesFromPreferences(preferences)
        }

    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_IS_DARK_MODE] = enabled
        }
    }

    suspend fun setMapEngine(engine: String) {
        dataStore.edit { preferences ->
            preferences[KEY_MAP_ENGINE] = engine
        }
    }

    suspend fun setCachedCity(city: String) {
        dataStore.edit { preferences ->
            preferences[KEY_CACHED_CITY] = city
        }
    }

    suspend fun reportUserPrice(stationId: Int, fuelType: String, price: Double) {
        val key = stringPreferencesKey("$USER_PRICE_PREFIX$stationId:$fuelType")
        dataStore.edit { preferences ->
            preferences[key] = price.toString()
        }
    }

    suspend fun clearUserPrice(stationId: Int, fuelType: String) {
        val key = stringPreferencesKey("$USER_PRICE_PREFIX$stationId:$fuelType")
        dataStore.edit { preferences ->
            preferences.remove(key)
        }
    }

    suspend fun clearAllUserPrices() {
        dataStore.edit { preferences ->
            val keysToRemove = preferences.asMap().keys.filter { it.name.startsWith(USER_PRICE_PREFIX) }
            for (key in keysToRemove) {
                preferences.remove(key)
            }
        }
    }

    suspend fun getAllUserPrices(): Map<Pair<Int, String>, Double> {
        return try {
            val prefs = dataStore.data.first()
            parseUserPricesFromPreferences(prefs)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to read user prices")
            emptyMap()
        }
    }

    fun getAllUserPricesBlocking(): Map<Pair<Int, String>, Double> {
        return runBlocking {
            getAllUserPrices()
        }
    }

    private fun parseUserPricesFromPreferences(preferences: Preferences): Map<Pair<Int, String>, Double> {
        val result = mutableMapOf<Pair<Int, String>, Double>()
        for ((key, value) in preferences.asMap()) {
            val keyName = key.name
            if (!keyName.startsWith(USER_PRICE_PREFIX)) continue
            val raw = keyName.removePrefix(USER_PRICE_PREFIX)
            val sep = raw.indexOf(':')
            if (sep <= 0) continue
            val stationId = raw.substring(0, sep).toIntOrNull() ?: continue
            val fuelType = raw.substring(sep + 1)
            val price = (value as? String)?.toDoubleOrNull()
                ?: (value as? Double)
                ?: continue
            result[Pair(stationId, fuelType)] = price
        }
        return result
    }
}
