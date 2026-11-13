package com.jboden.costingpro.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jboden.costingpro.R

/** Sans for everything except the recipe title and the margin figure. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Serif, only for the recipe title and the margin figure (PLAN.md, "Tipografía y jerarquía"). */
val Fraunces = FontFamily(
    Font(R.font.fraunces_semibold, FontWeight.SemiBold),
)

private val Base = Typography()

private fun TextStyle.inter() = copy(fontFamily = Inter)

/** Material 3 type scale, all in Inter. */
val CostingTypography = Typography(
    displayLarge = Base.displayLarge.inter(),
    displayMedium = Base.displayMedium.inter(),
    displaySmall = Base.displaySmall.inter(),
    headlineLarge = Base.headlineLarge.inter(),
    headlineMedium = Base.headlineMedium.inter(),
    headlineSmall = Base.headlineSmall.inter(),
    titleLarge = Base.titleLarge.inter(),
    titleMedium = Base.titleMedium.inter().copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.inter(),
    bodyLarge = Base.bodyLarge.inter(),
    bodyMedium = Base.bodyMedium.inter(),
    bodySmall = Base.bodySmall.inter(),
    labelLarge = Base.labelLarge.inter(),
    labelMedium = Base.labelMedium.inter(),
    labelSmall = Base.labelSmall.inter(),
)

/** Recipe title, in the serif. */
val RecipeTitleStyle = TextStyle(
    fontFamily = Fraunces,
    fontWeight = FontWeight.SemiBold,
    fontSize = 28.sp,
    lineHeight = 34.sp,
)

/** The margin percentage, the biggest figure of a screen, in the serif. */
val MarginFigureStyle = TextStyle(
    fontFamily = Fraunces,
    fontWeight = FontWeight.SemiBold,
    fontSize = 40.sp,
    lineHeight = 44.sp,
    fontFeatureSettings = "tnum",
)

/** Small caps-like labels over a figure ("COSTE POR UNIDAD"); write the text in upper case. */
val FigureLabelStyle = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.8.sp,
)
