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
