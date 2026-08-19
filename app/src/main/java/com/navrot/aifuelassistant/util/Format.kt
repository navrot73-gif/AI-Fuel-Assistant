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
 *
 * Все числовые методы принимают [Number] — это позволяет передавать
 * `Int`, `Long`, `Float`, `Double` без явного приведения. Внутри
 * конвертация идёт через [Number.toDouble], что безопасно для всех
 * примитивных числовых типов Kotlin.
 */
object Format {

    /**
     * Аналог `String.format(fmt, args)` с фиксированной [Locale.ROOT].
     * Используйте для любых форматных строк.
     */
    fun format(fmt: String, vararg args: Any?): String =
        String.format(Locale.ROOT, fmt, *args)

    /** Десятичное число с [digits] знаками после точки, разделитель — точка. */
    fun number(value: Number, digits: Int = 1): String =
        String.format(Locale.ROOT, "%.${digits}f", value.toDouble())

    /** Цена в ₽, 0 знаков после точки. */
    fun price(value: Number): String =
        String.format(Locale.ROOT, "%.0f", value.toDouble())

    /** Цена в ₽, 2 знака после точки. */
    fun price2(value: Number): String =
        String.format(Locale.ROOT, "%.2f", value.toDouble())

    /** Координата с 5 знаками после точки (точность ~1 м). */
    fun coord(value: Number): String =
        String.format(Locale.ROOT, "%.5f", value.toDouble())

    /** Пробег в км с 1 знаком после точки. */
    fun km(value: Number): String =
        String.format(Locale.ROOT, "%.1f", value.toDouble())
}
