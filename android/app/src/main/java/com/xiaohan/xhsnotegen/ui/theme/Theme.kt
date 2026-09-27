package com.xiaohan.xhsnotegen.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Tomato, onPrimary = Color.White,
    primaryContainer = TomatoContainer, onPrimaryContainer = TomatoDeep,
    secondary = Herb, onSecondary = Color.White,
    secondaryContainer = HerbContainer, onSecondaryContainer = HerbDeep,
    tertiary = Honey, onTertiary = Color.White,
    tertiaryContainer = HoneyContainer, onTertiaryContainer = HoneyDeep,
    background = Paper, onBackground = Ink,
    surface = Paper, onSurface = Ink,
    surfaceVariant = PaperHigh, onSurfaceVariant = InkSoft,
    surfaceContainerLowest = PaperLowest,
    surfaceContainerLow = PaperLow,
    surfaceContainer = PaperMid,
    surfaceContainerHigh = PaperHigh,
    surfaceContainerHighest = PaperHighest,
    inverseSurface = Ink, inverseOnSurface = Paper, inversePrimary = TomatoLight,
    outline = Rule, outlineVariant = RuleSoft,
    error = Chili, onError = Color.White,
    errorContainer = ChiliContainer, onErrorContainer = TomatoDeep,
    scrim = Color.Black,
)

private val DarkColors = darkColorScheme(
    primary = TomatoLight, onPrimary = TomatoDeep,
    primaryContainer = TomatoNightContainer, onPrimaryContainer = TomatoContainer,
    secondary = HerbLight, onSecondary = HerbDeep,
    secondaryContainer = HerbNightContainer, onSecondaryContainer = HerbContainer,
    tertiary = HoneyLight, onTertiary = HoneyDeep,
    tertiaryContainer = HoneyNightContainer, onTertiaryContainer = HoneyContainer,
    background = Night, onBackground = Cream,
    surface = Night, onSurface = Cream,
    surfaceVariant = NightHigh, onSurfaceVariant = CreamSoft,
    surfaceContainerLowest = NightLowest,
    surfaceContainerLow = NightLow,
    surfaceContainer = NightMid,
    surfaceContainerHigh = NightHigh,
    surfaceContainerHighest = NightHighest,
    inverseSurface = Cream, inverseOnSurface = Night, inversePrimary = Tomato,
    outline = NightRule, outlineVariant = NightRuleSoft,
    error = ChiliLight, onError = TomatoDeep,
    errorContainer = ChiliNightContainer, onErrorContainer = ChiliContainer,
    scrim = Color.Black,
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Follows the system light/dark setting. Dynamic (wallpaper) color is off on
 * purpose — the warm palette is the app's identity. System bar icons are
 * handled by enableEdgeToEdge() in MainActivity.
 */
@Composable
fun XhsNoteGenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
