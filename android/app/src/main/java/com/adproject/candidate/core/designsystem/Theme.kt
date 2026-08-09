package com.adproject.candidate.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AdTeal = Color(0xFF00A6A8)
val AdTealDark = Color(0xFF008B8D)
val AdTealSoft = Color(0xFFD9F7F5)
val AdBackground = Color(0xFFF4F7F8)
val AdSurface = Color.White
val AdText = Color(0xFF11161B)
val AdMuted = Color(0xFF75808C)
val AdBorder = Color(0xFFDDE3E7)
val AdChip = Color(0xFFF3F5F6)

private val AdColors = lightColorScheme(
    primary = AdTeal,
    onPrimary = Color.White,
    primaryContainer = AdTealSoft,
    onPrimaryContainer = AdTealDark,
    background = AdBackground,
    onBackground = AdText,
    surface = AdSurface,
    onSurface = AdText,
    outline = AdBorder,
)

@Composable
fun AdTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AdColors,
        typography = Typography(),
        content = content,
    )
}
