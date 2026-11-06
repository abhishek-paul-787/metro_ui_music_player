package com.metro.music.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.metro.music.R
import com.metro.music.data.AppSettings

val Selawik = FontFamily(
    Font(R.font.selawik_light, FontWeight.Light),
    Font(R.font.selawik_semilight, FontWeight.Normal),
    Font(R.font.selawik_regular, FontWeight.Medium),
    Font(R.font.selawik_semibold, FontWeight.SemiBold),
)

object Metro {
    val accents = listOf(
        0xFFA4C400, 0xFF60A917, 0xFF008A00, 0xFF00ABA9, 0xFF1BA1E2, 0xFF0050EF, 0xFF6A00FF, 0xFFAA00FF, 0xFFF472D0, 0xFFD80073,
        0xFFA20025, 0xFFE51400, 0xFFFA6800, 0xFFF0A30A, 0xFFE3C800, 0xFF825A2C, 0xFF6D8764, 0xFF647687, 0xFF76608A, 0xFF87794E,
    ).map { it.toInt() }
    val accent get() = Color(AppSettings.accent)
    val bg get() = if (AppSettings.dark) Color.Black else Color.White
    val fg get() = if (AppSettings.dark) Color.White else Color.Black
    val sub get() = if (AppSettings.dark) Color(0xFF999999) else Color(0xFF666666)
    val tile get() = if (AppSettings.dark) Color(0xFF1F1F1F) else Color(0xFFE5E5E5)
}

fun metroText(size: TextUnit, weight: FontWeight = FontWeight.Normal, color: Color = Color.Unspecified) =
    TextStyle(fontFamily = Selawik, fontSize = size, fontWeight = weight, color = color)

val Light = FontWeight.Light
