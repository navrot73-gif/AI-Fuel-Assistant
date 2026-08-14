package com.navrot.aifuelassistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Приборная панель Fueldeck: фиксированная тёмная палитра.
 * Material You отключён намеренно — дизайн не должен перекрашиваться под обои.
 * ВАЖНО: контейнерные цвета заданы явно, иначе material3 подставит свои
 * дефолтные фиолетовые (и FAB / индикатор вкладок станут фиолетовыми).
 */
private val FueldeckDarkScheme = darkColorScheme(
    primary = FueldeckColors.Amber,
    onPrimary = Color(0xFF1A1205),
    primaryContainer = Color(0xFF3A2A08),
    onPrimaryContainer = FueldeckColors.Amber,

    secondary = FueldeckColors.Mint,
    onSecondary = Color(0xFF06201C),
    secondaryContainer = Color(0xFF0C2A26),
    onSecondaryContainer = FueldeckColors.Mint,

    tertiary = FueldeckColors.Coral,
    onTertiary = Color(0xFF2A0B08),
    tertiaryContainer = Color(0xFF341310),
    onTertiaryContainer = FueldeckColors.Coral,

    background = FueldeckColors.Bg0,
    onBackground = FueldeckColors.Ink,
    surface = FueldeckColors.Surface,
    onSurface = FueldeckColors.Ink,
    surfaceVariant = FueldeckColors.Surface2,
    onSurfaceVariant = FueldeckColors.InkDim,
    outline = FueldeckColors.Line2,
    outlineVariant = FueldeckColors.Line,
    error = FueldeckColors.Coral,
    onError = Color(0xFF2A0B08),
)

@Composable
fun AIFuelAssistantTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FueldeckDarkScheme,
        typography = FueldeckTypography,
        content = content,
    )
}