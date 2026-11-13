package com.jboden.costingpro.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.jboden.costingpro.domain.MarginStatus

// Margin state colours, from the "Diseño visual" section of PLAN.md.
private val MarginGood = Color(0xFF2E7D5B)
private val MarginFair = Color(0xFFB7791F)
private val MarginLow = Color(0xFFB5483A)

// White text on the plan's ochre only reaches 3.6:1, so the filled pill uses a darker
// ochre (5.3:1). Same role, adjusted tone, as the plan allows.
private val MarginFairStrong = Color(0xFF96600F)

// Lightened tones for the dark theme.
private val MarginGoodDark = Color(0xFF6CC9A0)
private val MarginFairDark = Color(0xFFE0B04E)
private val MarginLowDark = Color(0xFFE39386)

/**
 * Colour of a margin state. Always show it next to a word ("Bueno", "Justo", "Bajo"):
 * colour alone is not enough, and the ochre does not reach AA contrast as small text.
 */
@Composable
@ReadOnlyComposable
fun MarginStatus.color(): Color {
    val dark = isSystemInDarkTheme()
    return when (this) {
        MarginStatus.GOOD -> if (dark) MarginGoodDark else MarginGood
        MarginStatus.FAIR -> if (dark) MarginFairDark else MarginFair
        MarginStatus.LOW -> if (dark) MarginLowDark else MarginLow
    }
}

/** Fill of the margin pill, and colour of the large margin figure. Pair it with [onPillColor]. */
@Composable
@ReadOnlyComposable
fun MarginStatus.pillColor(): Color =
    if (this == MarginStatus.FAIR && !isSystemInDarkTheme()) MarginFairStrong else color()

/** Text and icons on [pillColor]: white on the light theme, the night background on the dark one. */
@Composable
@ReadOnlyComposable
fun onPillColor(): Color = if (isSystemInDarkTheme()) NightBackground else White
