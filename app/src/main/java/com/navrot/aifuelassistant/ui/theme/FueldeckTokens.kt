package com.navrot.aifuelassistant.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Дизайн-токены Fueldeck, перенесённые из index.html (:root).
 * Компоненты ниже берут цвета отсюда НАПРЯМУЮ, поэтому не зависят от
 * того, какая тема сейчас стоит в приложении — ничего не сломают.
 *
 * КАСТОМНЫЕ ШРИФТЫ (опционально, когда захочешь точное совпадение):
 *   1) положи ttf в app/src/main/res/font/:
 *        space_grotesk.ttf, manrope.ttf, jetbrains_mono.ttf
 *   2) замени FontFamily.* ниже на:
 *        val SpaceGrotesk = FontFamily(Font(R.font.space_grotesk))  // и т.д.
 *   Пока стоят системные семейства — собирается без ресурсов.
 */
object FueldeckColors {
    val Bg0      = Color(0xFF17222B)
    val Bg1      = Color(0xFF17222B)
    val Surface  = Color(0xFF1E2732)
    val Surface2 = Color(0xFF242E38)

    val Line  = Color(0x12FFFFFF) // rgba(255,255,255,.07)
    val Line2 = Color(0x1FFFFFFF) // rgba(255,255,255,.12)

    val Ink      = Color(0xFFEAF3F1)
    val InkDim   = Color(0xFF93A6A4)
    val InkFaint = Color(0xFF5D6F6E)

    val Amber     = Color(0xFFE8A750)
    val AmberSoft = Color(0x29E8A750) // rgba(...,.16)
    val Mint      = Color(0xFF4DB6AC)
    val MintSoft  = Color(0x244DB6AC) // rgba(...,.14)
    val Coral     = Color(0xFFFF6F61)
    val CoralSoft = Color(0x24FF6F61) // rgba(...,.14)

    val Danger    = Color(0xFFE57373) // очередь на АЗС > 0
}

object FueldeckShapes {
    val Sm   = RoundedCornerShape(11.dp)
    val Md   = RoundedCornerShape(17.dp)
    val Lg   = RoundedCornerShape(24.dp)
    val Pill = RoundedCornerShape(50)
}

private val Disp = FontFamily.SansSerif  // → Space Grotesk
private val Body = FontFamily.SansSerif  // → Manrope
private val Mono = FontFamily.Monospace  // → JetBrains Mono

val FueldeckTypography = Typography(
    displayLarge  = androidx.compose.ui.text.TextStyle(fontFamily = Disp, fontWeight = FontWeight.SemiBold, fontSize = 25.sp, letterSpacing = (-0.5).sp),
    titleMedium   = androidx.compose.ui.text.TextStyle(fontFamily = Disp, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelSmall    = androidx.compose.ui.text.TextStyle(fontFamily = Disp, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.6.sp),
    bodyMedium    = androidx.compose.ui.text.TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 13.5.sp),
    bodySmall     = androidx.compose.ui.text.TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.3.sp),
)