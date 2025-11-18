package com.example.karooglucometer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF2D2D2D),
    secondary = Color(0xFF4CAF50),
    tertiary = Color(0xFF2196F3)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2D2D2D),
    secondary = Color(0xFF4CAF50),
    tertiary = Color(0xFF2196F3),
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    primaryContainer = Color(0xFFF5F5F5),
    onPrimaryContainer = Color(0xFF2D2D2D)
)

@Composable
fun KarooGlucometerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}