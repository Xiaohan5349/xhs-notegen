package com.xiaohan.xhsnotegen.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Bento radii: tiles 18, things inside a tile 12, small marks 8.
 * (Sheets and dialogs 24.) Nested corners get visibly tighter inward.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/**
 * Surfaces and marks the M3 scheme has no role for. Derived from the scheme,
 * so each of the themes gets them in light and dark.
 *
 * Layers: [inset] (sunk into a tile) → page background → [tile] → [tile2] (raised in a tile).
 */
@Immutable
data class AppColors(
    val tile: Color,
    val tile2: Color,
    val inset: Color,
    /** Hairline around tiles and between rows. */
    val line: Color,
    /** Stronger hairline: chips, dashed "add" cells, grid lines. */
    val line2: Color,
    /** "Posted" is gold in every theme. */
    val goldContainer: Color,
    val onGoldContainer: Color,
    val goldMark: Color,
    val star: Color,
)

fun appColors(scheme: ColorScheme, dark: Boolean) = AppColors(
    tile = if (dark) scheme.surfaceContainerLow else scheme.surfaceContainerLowest,
    tile2 = if (dark) scheme.surfaceContainer else scheme.surfaceContainerLow,
    inset = if (dark) scheme.surfaceContainerLowest else scheme.surfaceContainer,
    line = scheme.outlineVariant,
    line2 = scheme.outline,
    goldContainer = if (dark) GoldNightContainer else GoldContainer,
    onGoldContainer = if (dark) OnGoldNightContainer else OnGoldContainer,
    goldMark = if (dark) GoldNightMark else GoldMark,
    star = if (dark) StarNight else GoldMark,
)

private val LocalAppColors = staticCompositionLocalOf { appColors(lightColorScheme(), false) }

/** The extra colors of the current theme: `MaterialTheme.app.tile`. */
val MaterialTheme.app: AppColors
    @Composable @ReadOnlyComposable get() = LocalAppColors.current

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
    val scheme = theme.scheme(darkTheme)
    CompositionLocalProvider(
        LocalAppTheme provides theme,
        LocalDarkTheme provides darkTheme,
        LocalAppColors provides appColors(scheme, darkTheme),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
