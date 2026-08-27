package uk.co.cabcomply.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = BrandBlueLight,
    onPrimaryContainer = BrandBlueDark,
    secondary = BrandGreen,
    onSecondary = Color.White,
    error = BrandRed,
    onError = Color.White,
    background = BrandBackground,
    onBackground = BrandInk,
    surface = BrandSurface,
    onSurface = BrandInk,
    surfaceVariant = BrandBlueLight,
    onSurfaceVariant = BrandInkMuted,
    outline = BrandOutline
)

private val DarkColors = darkColorScheme(
    primary = BrandBlueLight,
    onPrimary = BrandBlueDark,
    primaryContainer = BrandBlueDark,
    onPrimaryContainer = BrandBlueLight,
    secondary = BrandGreen,
    onSecondary = Color.White,
    error = Color(0xFFE6857D),
    onError = BrandBlueDark,
    background = BrandBackgroundDark,
    onBackground = BrandInkOnDark,
    surface = BrandSurfaceDark,
    onSurface = BrandInkOnDark,
    surfaceVariant = BrandOutlineDark,
    onSurfaceVariant = BrandInkMutedOnDark,
    outline = BrandOutlineDark
)

@Composable
fun CabComplyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = CabComplyTypography,
        content = content
    )
}
