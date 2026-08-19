package com.navrot.aifuelassistant.util

import java.util.Locale

/**
 * Форматирование чисел в независимой от системной локали форме.
 *
 * По умолчанию [String.format] использует default locale устройства.
 * В локали `de_DE` или `ru_RU` разделителем дробной части будет запятая,
 * а не точка — это ломает парсинг GPS-координат и цен, если они потом
 * сериализуются в строку и парсятся обратно.
 *
 * Все числовые строки, которые попадают в UI или конкатенируются в строки,
 * должны идти через эти хелперы (используем [Locale.ROOT] / [Locale.US]).
 */
object Format {

    /**
     * Аналог `String.format(fmt, args)` с фиксированной [Locale.ROOT].
     * Используйте для любых форматных строк.
     */
    fun format(fmt: String, vararg args: Any?): String =
        String.format(Locale.ROOT, fmt, *args)

    /** Десятичное число с [digits] знаками после точки, разделитель — точка. */
    fun number(value: Double, digits: Int = 1): String =
        String.format(Locale.ROOT, "%.${digits}f", value)

    /** Цена в ₽, 0 знаков после точки. */
    fun price(value: Double): String =
        String.format(Locale.ROOT, "%.0f", value)

    /** Цена в ₽, 2 знака после точки. */
    fun price2(value: Double): String =
        String.format(Locale.ROOT, "%.2f", value)

    /** Координата с 5 знаками после точки (точность ~1 м). */
    fun coord(value: Double): String =
        String.format(Locale.ROOT, "%.5f", value)

    /** Пробег в км с 1 знаком после точки. */
    fun km(value: Double): String =
        String.format(Locale.ROOT, "%.1f", value)
}
