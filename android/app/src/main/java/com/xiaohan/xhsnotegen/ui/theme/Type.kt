package com.xiaohan.xhsnotegen.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Display sizes use the system serif for an editorial, journal-like voice;
// everything you read or type stays in the system sans (which also carries
// the CJK glyphs). System fonts only — no Play Services font provider, which
// many phones sold in China don't have.
private val Display = FontFamily.Serif

val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.25).sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp, letterSpacing = 0.15.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.2.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.3.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

/** Monospace is for numbers only: counts, photo numbers, dates, the title length. */
val NumberStyle = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 15.sp)

/** The big count on the home screen. */
val BigNumberStyle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 48.sp, lineHeight = 52.sp, letterSpacing = (-1).sp)
