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
 * Radii: paper cards 22, tiles 18, things inside 12, small marks 8; sheets and
 * dialogs 24. Buttons and chips are pills; photo covers are arches ([ArchShape]).
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
    /** One small warm accent beside the primary: terracotta in Field, the primary elsewhere. */
    val accent2: Color,
    val onAccent2: Color,
    /** The deep full-width band on the home screen and under the publish button. */
    val band: Color,
    val onBand: Color,
    /** "Posted" gold where it sits on the band (the band is deep in both modes). */
    val bandGold: Color,
    /** Serif headings: the deep end of the theme color by day, plain ink by night. */
    val head: Color,
)

fun appColors(scheme: ColorScheme, dark: Boolean, theme: AppTheme = AppTheme.DEFAULT) = AppColors(
    tile = if (dark) scheme.surfaceContainerLow else scheme.surfaceContainerLowest,
    tile2 = if (dark) scheme.surfaceContainer else scheme.surfaceContainerLow,
    inset = if (dark) scheme.surfaceContainerLowest else scheme.surfaceContainer,
    line = scheme.outlineVariant,
    line2 = scheme.outline,
    goldContainer = if (dark) GoldNightContainer else GoldContainer,
    onGoldContainer = if (dark) OnGoldNightContainer else OnGoldContainer,
    goldMark = if (dark) GoldNightMark else GoldMark,
    star = if (dark) StarNight else GoldMark,
    accent2 = when {
        theme != AppTheme.FIELD -> scheme.primary
        dark -> FieldTerracottaLight
        else -> FieldTerracotta
    },
    onAccent2 = when {
        theme != AppTheme.FIELD -> scheme.onPrimary
        dark -> FieldTerracottaInk
        else -> Color.White
    },
    band = when {
        !dark -> scheme.primary
        theme == AppTheme.FIELD -> FieldNightBand
        else -> scheme.primaryContainer
    },
    onBand = if (dark) scheme.onPrimaryContainer else scheme.onPrimary,
    bandGold = GoldNightMark,
    head = if (dark) scheme.onSurface else scheme.onPrimaryContainer,
)

private val LocalAppColors = staticCompositionLocalOf { appColors(lightColorScheme(), false) }

/** The extra colors of the current theme: `MaterialTheme.app.tile`. */
val MaterialTheme.app: AppColors
    @Composable @ReadOnlyComposable get() = LocalAppColors.current

/** The active theme and mode, for decorations that go beyond the color scheme. */
val LocalAppTheme = staticCompositionLocalOf { AppTheme.DEFAULT }
val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * App theme: one of [AppTheme]'s palettes in light or dark. Dynamic
 * (wallpaper) color is off on purpose — the palettes are the app's identity.
 * System bar icons are set by MainActivity to match [darkTheme].
 */
@Composable
fun XhsNoteGenTheme(
    theme: AppTheme = AppTheme.DEFAULT,
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = theme.scheme(darkTheme)
    CompositionLocalProvider(
        LocalAppTheme provides theme,
        LocalDarkTheme provides darkTheme,
        LocalAppColors provides appColors(scheme, darkTheme, theme),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
