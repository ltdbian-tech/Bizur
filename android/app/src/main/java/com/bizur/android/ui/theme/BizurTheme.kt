package com.bizur.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BizurDarkColors = darkColorScheme(
    primary = Color(0xFF55D6BE),
    onPrimary = Color(0xFF081C1A),
    background = Color(0xFF0B0E11),
    onBackground = Color(0xFFF2F4F8),
    surface = Color(0xFF111418),
    onSurface = Color(0xFFF2F4F8),
    secondary = Color(0xFF1B8EF2),
    onSecondary = Color(0xFF021322),
    tertiary = Color(0xFF8E8CF9)
)

private val BizurLightColors = lightColorScheme(
    primary = Color(0xFF1B8EF2),
    onPrimary = Color.White,
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF0B0E11),
    surface = Color.White,
    onSurface = Color(0xFF0B0E11)
)

@Composable
fun BizurTheme(useDarkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (useDarkTheme) BizurDarkColors else BizurLightColors
    MaterialTheme(colorScheme = colors, typography = MaterialTheme.typography, content = content)
}
