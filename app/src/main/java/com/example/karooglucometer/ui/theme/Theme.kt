package com.example.karooglucometer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ColorScheme = lightColorScheme(
    background = BabyPowder,
    primary = Onyx,
    secondary = PurpleGrey80,
    tertiary = BloodRed,
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