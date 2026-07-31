package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.example.ui.ThemeStyle

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
    val darkTheme = when (themeStyle) {
        ThemeStyle.DARK -> true
        ThemeStyle.LIGHT -> false
        ThemeStyle.AUTO -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

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
