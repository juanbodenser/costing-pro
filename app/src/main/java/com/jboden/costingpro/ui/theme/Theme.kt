package com.jboden.costingpro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Teal,
    onPrimary = White,
    secondary = Amber,
    onSecondary = Ink,
    tertiary = Plum,
    onTertiary = White,
    background = Ivory,
    onBackground = Ink,
    surface = White,
    onSurface = Ink,
    surfaceVariant = IvoryMedium,
    onSurfaceVariant = InkSecondary,
    outline = OutlineLight,
    error = ErrorLight,
    onError = White,
)

private val DarkColorScheme = darkColorScheme(
    primary = TealLight,
    onPrimary = NightBackground,
    secondary = AmberLight,
    onSecondary = NightBackground,
    tertiary = PlumLight,
    onTertiary = NightBackground,
    background = NightBackground,
    onBackground = NightText,
    surface = NightSurface,
    onSurface = NightText,
    surfaceVariant = NightSurfaceAlt,
    onSurfaceVariant = NightTextMuted,
    outline = OutlineDark,
    error = ErrorDark,
    onError = NightBackground,
)

/** App theme. Dynamic colour is deliberately not used: the palette carries meaning. */
@Composable
fun CostingProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = CostingTypography,
        content = content,
    )
}
