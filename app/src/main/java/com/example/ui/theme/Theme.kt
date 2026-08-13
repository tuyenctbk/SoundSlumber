package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.example.ui.ThemeStyle

import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = MoonlightBlue,
    onPrimary = OledBlack,
    primaryContainer = SoftBlueTint,
    onPrimaryContainer = MoonlightBlue,
    secondary = WarmAmber,
    onSecondary = OledBlack,
    secondaryContainer = WarmAmberTint,
    onSecondaryContainer = WarmAmber,
    tertiary = EmeraldGreen,
    background = OledBlack,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = DarkBorder
)

private val NightAmberColorScheme = darkColorScheme(
    primary = WarmAmber,
    onPrimary = OledBlack,
    primaryContainer = WarmAmberTint,
    onPrimaryContainer = WarmAmber,
    secondary = MoonlightBlue,
    onSecondary = OledBlack,
    secondaryContainer = SoftBlueTint,
    onSecondaryContainer = MoonlightBlue,
    tertiary = EmeraldGreen,
    background = OledBlack,
    onBackground = Color(0xFFFDE68A),
    surface = Color(0xFF14120E),
    onSurface = Color(0xFFFDE68A),
    surfaceVariant = Color(0xFF1F1A12),
    onSurfaceVariant = Color(0xFFD97706),
    outline = Color(0xFF332612)
)

private val LightColorScheme = lightColorScheme(
    primary = MoonlightBlue,
    onPrimary = White,
    primaryContainer = LightSoftBlueTint,
    onPrimaryContainer = MoonlightBlueVariant,
    secondary = WarmAmber,
    onSecondary = White,
    secondaryContainer = LightWarmAmberTint,
    onSecondaryContainer = WarmAmberVariant,
    tertiary = EmeraldGreen,
    background = White,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder
)

@Composable
fun SoundSlumberTheme(
    themeStyle: ThemeStyle = ThemeStyle.DARK,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeStyle) {
        ThemeStyle.DARK -> DarkColorScheme
        ThemeStyle.NIGHT_AMBER -> NightAmberColorScheme
        ThemeStyle.LIGHT -> LightColorScheme
        ThemeStyle.AUTO -> if (isSystemInDarkTheme()) DarkColorScheme else LightColorScheme
        ThemeStyle.SUNSET_AUTO -> {
            val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            if (currentHour >= 19 || currentHour < 6) {
                NightAmberColorScheme // Automatic Night Amber from sunset (7 PM) to sunrise (6 AM)
            } else {
                DarkColorScheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    SoundSlumberTheme(content = content)
}
