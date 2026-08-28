package uk.co.tripassistant.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import uk.co.tripassistant.app.data.prefs.ThemeMode

private val LightColors = lightColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,
    primaryContainer = BrandGreenLight,
    onPrimaryContainer = BrandGreenDark,
    secondary = BrandGreenDark,
    onSecondary = Color.White,
    error = StatusPoor,
    onError = Color.White,
    background = BrandBackground,
    onBackground = BrandInk,
    surface = BrandSurface,
    onSurface = BrandInk,
    surfaceVariant = BrandGreenLight,
    onSurfaceVariant = BrandInkMuted,
    outline = BrandOutline
)

private val DarkColors = darkColorScheme(
    primary = BrandGreenOnDark,
    onPrimary = BrandGreenDark,
    primaryContainer = BrandGreenDark,
    onPrimaryContainer = BrandGreenLight,
    secondary = BrandGreenOnDark,
    onSecondary = BrandGreenDark,
    error = StatusPoorOnDark,
    onError = BrandGreenDark,
    background = BrandBackgroundDark,
    onBackground = BrandInkOnDark,
    surface = BrandSurfaceDark,
    onSurface = BrandInkOnDark,
    surfaceVariant = BrandOutlineDark,
    onSurfaceVariant = BrandInkMutedOnDark,
    outline = BrandOutlineDark
)

/** System, light or dark, as the driver chose (spec section 47). */
@Composable
fun TripAssistantTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = TripAssistantTypography,
        content = content
    )
}
