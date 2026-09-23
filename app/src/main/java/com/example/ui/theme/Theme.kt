package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SleekColorScheme = darkColorScheme(
    primary = SleekTealPrimary,
    onPrimary = SleekOnPrimary,
    primaryContainer = SleekTealContainer,
    onPrimaryContainer = SleekTealPrimary,
    secondary = SleekBlueCheck,
    onSecondary = Color.White,
    background = SleekBackground,
    onBackground = SleekTextPrimary,
    surface = SleekSurface,
    onSurface = SleekTextPrimary,
    surfaceVariant = SleekSurfaceVariant,
    onSurfaceVariant = SleekTextSecondary,
    outline = SleekBorder,
    outlineVariant = SleekBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to false to preserve Sleek Interface styling
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SleekColorScheme,
        typography = Typography,
        content = content
    )
}
