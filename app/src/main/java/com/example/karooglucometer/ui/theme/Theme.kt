package com.example.karooglucometer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ColorScheme = lightColorScheme(
    // Primary
    primary = BattleshipGray,
    onPrimary = White,

    // Surfaces
    surface = White,
    onSurface = Black,
    surfaceVariant = LightGray,
    onSurfaceVariant = BattleshipGray,

    // Backgrounds & Primary
    background = White,
    onBackground = Black,
    primaryContainer = LightGray,
    onPrimaryContainer = Black,

    // Borders
    outline = BattleshipGray,

    // Secondary
    secondary = ForestGreen,
    onSecondary = White,

    // Tertiary
    tertiary = Amber,
    onTertiary = Black,

    // Error
    error = BloodRed,
    onError = White
)

@Composable
fun KarooGlucometerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = Typography,
        content = content
    )
}