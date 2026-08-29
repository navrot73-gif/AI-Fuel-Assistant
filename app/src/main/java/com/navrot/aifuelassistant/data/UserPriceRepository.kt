package com.navrot.aifuelassistant.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Хранилище пользовательских цен на топливо.
 *
 * Формат ключа: "price:{stationId}:{fuelType}" → String (цена Double.toString())
 * Переживает перезапуск приложения (Jetpack DataStore Preferences).
 *
 * Приоритет: эти цены перекрывают данные из GitHub json / кеша / assets.
 */
@Singleton
class UserPriceRepository @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) {

    constructor(@ApplicationContext context: Context) : this(UserPreferencesRepository(context))

    /**
     * Flow пользовательских цен.
     */
    val userPricesFlow: Flow<Map<Pair<Int, String>, Double>> = userPreferencesRepository.userPrices

    /**
     * Получить все пользовательские цены в виде Map: Pair(stationId, fuelType) → price
     */
    suspend fun getAll(): Map<Pair<Int, String>, Double> {
        return userPreferencesRepository.getAllUserPrices()
    }

    /**
     * Сохранить пользовательскую цену.
     */
    suspend fun report(stationId: Int, fuelType: String, price: Double) {
        userPreferencesRepository.reportUserPrice(stationId, fuelType, price)
    }

    /**
     * Удалить пользовательскую цену (когда, например, пришёл новый json с актуальной ценой).
     */
    suspend fun clear(stationId: Int, fuelType: String) {
        userPreferencesRepository.clearUserPrice(stationId, fuelType)
    }

    /**
     * Очистить все пользовательские цены (для отладки).
     */
    suspend fun clearAll() {
        userPreferencesRepository.clearAllUserPrices()
    }
}
