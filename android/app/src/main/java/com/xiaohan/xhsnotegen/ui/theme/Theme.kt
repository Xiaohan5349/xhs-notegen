package com.xiaohan.xhsnotegen.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** The active theme and mode, for decorations that go beyond the color scheme. */
val LocalAppTheme = staticCompositionLocalOf { AppTheme.TOMATO }
val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * App theme: one of [AppTheme]'s palettes in light or dark. Dynamic
 * (wallpaper) color is off on purpose — the palettes are the app's identity.
 * System bar icons are set by MainActivity to match [darkTheme].
 */
@Composable
fun XhsNoteGenTheme(
    theme: AppTheme = AppTheme.TOMATO,
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalAppTheme provides theme, LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = theme.scheme(darkTheme),
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
