package com.navrot.aifuelassistant.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Хранилище пользовательских цен на топливо.
 *
 * Формат ключа: "price:{stationId}:{fuelType}" → String (цена Double.toString())
 * Переживает перезапуск приложения (SharedPreferences).
 *
 * Приоритет: эти цены перекрывают данные из GitHub json / кеша / assets.
 */
@Singleton
class UserPriceRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("user_prices", Context.MODE_PRIVATE)

    /**
     * Получить все пользовательские цены в виде Map: Pair(stationId, fuelType) → price
     */
    fun getAll(): Map<Pair<Int, String>, Double> {
        val result = mutableMapOf<Pair<Int, String>, Double>()
        for ((key, value) in prefs.all) {
            if (!key.startsWith(PREFIX)) continue
            val raw = key.removePrefix(PREFIX)
            val sep = raw.indexOf(':')
            if (sep <= 0) continue
            val stationId = raw.substring(0, sep).toIntOrNull() ?: continue
            val fuelType = raw.substring(sep + 1)
            val price = (value as? String)?.toDoubleOrNull() ?: continue
            result[Pair(stationId, fuelType)] = price
        }
        return result
    }

    /**
     * Сохранить пользовательскую цену.
     */
    fun report(stationId: Int, fuelType: String, price: Double) {
        prefs.edit()
            .putString("$PREFIX$stationId:$fuelType", price.toString())
            .apply()
    }

    /**
     * Удалить пользовательскую цену (когда, например, пришёл новый json с актуальной ценой).
     */
    fun clear(stationId: Int, fuelType: String) {
        prefs.edit().remove("$PREFIX$stationId:$fuelType").apply()
    }

    /**
     * Очистить все пользовательские цены (для отладки).
     */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFIX = "price:"
    }
}
