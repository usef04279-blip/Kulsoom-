package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ProfessionalBlue,
    onPrimary = Color.White,
    primaryContainer = ProfessionalPurple,
    onPrimaryContainer = Color.White,
    secondary = ProfessionalPurple,
    onSecondary = Color.White,
    secondaryContainer = SurfaceCard,
    onSecondaryContainer = Color.White,
    tertiary = ProfessionalPink,
    onTertiary = Color.White,
    background = SpaceBlack,
    onBackground = Color.White,
    surface = SurfaceDark,
    onSurface = Color.White,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextMuted,
    outline = SurfaceCardBorder
)

private val LightColorScheme = DarkColorScheme // Preserves consistent Professional Polish dark canvas

@Composable
fun KulsoomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

