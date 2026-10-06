package com.xiaohan.xhsnotegen.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.xiaohan.xhsnotegen.i18n.tr

/**
 * Selectable color themes. Each defines an accent family on top of a neutral
 * "paper" base; status colors map to secondary (ready) and tertiary (to review),
 * so every theme keeps those two distinct from its primary (posted).
 */
enum class AppTheme(
    val id: String,
    val group: ThemeGroup = ThemeGroup.CLASSIC,
    /** Decorative art drawn behind the home header (original, drawn in code). */
    val backdrop: Backdrop = Backdrop.NONE,
) {
    GLINT("glint", ThemeGroup.SIGNATURE, Backdrop.RIPPLE),
    TOMATO("tomato"),
    MATCHA("matcha"),
    BLUEBERRY("blueberry"),
    SAKURA("sakura"),
    SESAME("sesame"),
    LATTE("latte"),
    SUMMER_SKY("summer_sky", ThemeGroup.ANIME, Backdrop.SKY),
    CITY_POP("city_pop", ThemeGroup.ANIME, Backdrop.SUNSET),
    MATSURI("matsuri", ThemeGroup.ANIME, Backdrop.SEIGAIHA);

    /** Name shown in the theme picker, in the app language. */
    val displayName: String
        get() = when (this) {
            GLINT -> tr("Glint", "浮生")
            TOMATO -> tr("Tomato", "番茄")
            MATCHA -> tr("Matcha", "抹茶")
            BLUEBERRY -> tr("Blueberry", "蓝莓")
            SAKURA -> tr("Sakura", "樱花")
            SESAME -> tr("Sesame", "芝麻")
            LATTE -> tr("Latte", "拿铁")
            SUMMER_SKY -> tr("Summer Sky", "夏日晴空")
            CITY_POP -> tr("City Pop", "城市流行")
            MATSURI -> tr("Matsuri", "祭典")
        }

    fun scheme(dark: Boolean): ColorScheme = if (dark) darkSchemes.getValue(this) else lightSchemes.getValue(this)

    /** Swatch shown in the theme picker: (accent, container). */
    val swatch: Pair<Color, Color> get() = lightSchemes.getValue(this).let { it.primary to it.primaryContainer }

    companion object {
        fun fromId(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: TOMATO
    }
}

enum class ThemeGroup {
    SIGNATURE, CLASSIC, ANIME;

    val label: String
        get() = when (this) {
            SIGNATURE -> tr("Signature", "专属")
            CLASSIC -> tr("Classic", "经典")
            ANIME -> tr("Anime-inspired", "动漫风")
        }
}

enum class Backdrop { NONE, SKY, SUNSET, SEIGAIHA, RIPPLE }

/** Surface ramp + text colors shared by several themes. */
private data class Neutrals(
    val bg: Color, val lowest: Color, val low: Color, val mid: Color, val high: Color, val highest: Color,
    val ink: Color, val inkSoft: Color, val rule: Color, val ruleSoft: Color,
)

private data class Accent(
    val color: Color, val on: Color, val container: Color, val onContainer: Color,
)

private val WarmLight = Neutrals(Paper, PaperLowest, PaperLow, PaperMid, PaperHigh, PaperHighest, Ink, InkSoft, Rule, RuleSoft)
private val WarmDark = Neutrals(Night, NightLowest, NightLow, NightMid, NightHigh, NightHighest, Cream, CreamSoft, NightRule, NightRuleSoft)

private val GreenLight = Neutrals(
    Color(0xFFF6F7F0), Color(0xFFFFFFFF), Color(0xFFF0F2EA), Color(0xFFEAEDE2), Color(0xFFE3E7DA), Color(0xFFDCE1D2),
    Color(0xFF22261F), Color(0xFF5D6556), Color(0xFFC9D0C0), Color(0xFFE1E6D9),
)
private val GreenDark = Neutrals(
    Color(0xFF121510), Color(0xFF0D0F0B), Color(0xFF1A1D17), Color(0xFF1F231C), Color(0xFF292E25), Color(0xFF343A30),
    Color(0xFFE4E9DE), Color(0xFFB2BAA9), Color(0xFF4A5243), Color(0xFF333A2E),
)
private val CoolLight = Neutrals(
    Color(0xFFF6F7FB), Color(0xFFFFFFFF), Color(0xFFF0F2F8), Color(0xFFEAECF4), Color(0xFFE3E6EF), Color(0xFFDCE0EA),
    Color(0xFF1E2230), Color(0xFF5A6072), Color(0xFFC9CEDB), Color(0xFFE1E4EC),
)
private val CoolDark = Neutrals(
    Color(0xFF111318), Color(0xFF0C0E12), Color(0xFF181B21), Color(0xFF1D2027), Color(0xFF272B33), Color(0xFF32363F),
    Color(0xFFE3E6EF), Color(0xFFAEB4C2), Color(0xFF474C58), Color(0xFF30343D),
)

private val BeigeLight = Neutrals(
    Color(0xFFF3EADB), Color(0xFFFFFBF4), Color(0xFFEFE5D4), Color(0xFFE9DECB), Color(0xFFE2D5C0), Color(0xFFDACDB6),
    Color(0xFF2E261E), Color(0xFF6B5D4E), Color(0xFFCDBFA9), Color(0xFFE3D8C6),
)
private val BeigeDark = Neutrals(
    Color(0xFF1C1814), Color(0xFF16130F), Color(0xFF231E19), Color(0xFF29231D), Color(0xFF332C25), Color(0xFF3E362E),
    Color(0xFFEDE3D4), Color(0xFFBFB2A2), Color(0xFF5A4F44), Color(0xFF3B332B),
)
private val SkyLight = Neutrals(
    Color(0xFFF4F8FC), Color(0xFFFFFFFF), Color(0xFFEDF3FA), Color(0xFFE6EEF7), Color(0xFFDDE7F2), Color(0xFFD4E0EE),
    Color(0xFF16233A), Color(0xFF52627A), Color(0xFFC3D1E2), Color(0xFFDCE5F0),
)
private val SkyDark = Neutrals(
    Color(0xFF0E1726), Color(0xFF0A111D), Color(0xFF141F31), Color(0xFF192538), Color(0xFF223046), Color(0xFF2C3B53),
    Color(0xFFE2EAF6), Color(0xFFA9B7CC), Color(0xFF435370), Color(0xFF2A3850),
)
private val PopLight = Neutrals(
    Color(0xFFFBF6FA), Color(0xFFFFFFFF), Color(0xFFF6EEF5), Color(0xFFF0E6EF), Color(0xFFE9DDE8), Color(0xFFE1D3E0),
    Color(0xFF2A1B33), Color(0xFF6A5872), Color(0xFFD6C4D6), Color(0xFFEADCE8),
)
private val PopDark = Neutrals(
    Color(0xFF170F24), Color(0xFF110A1C), Color(0xFF1F152E), Color(0xFF251A36), Color(0xFF2F2342), Color(0xFF3A2D4E),
    Color(0xFFF1E6F4), Color(0xFFBCA9C4), Color(0xFF56466A), Color(0xFF3A2E4B),
)
// Glint: pale mist over water by day, deep teal night by evening.
private val MistLight = Neutrals(
    Color(0xFFF2F6F6), Color(0xFFFFFFFF), Color(0xFFEBF1F1), Color(0xFFE4ECEC), Color(0xFFDCE6E6), Color(0xFFD3DFDF),
    Color(0xFF14262A), Color(0xFF52666A), Color(0xFFC2D2D3), Color(0xFFDCE6E6),
)
private val InkWaterDark = Neutrals(
    Color(0xFF0C1A1F), Color(0xFF081317), Color(0xFF112329), Color(0xFF162A30), Color(0xFF1F363D), Color(0xFF2A444C),
    Color(0xFFDDEBEC), Color(0xFFA5BCC0), Color(0xFF3F5A61), Color(0xFF2A4147),
)
private val IndigoNight = Neutrals(
    Color(0xFF11131F), Color(0xFF0C0E17), Color(0xFF181B29), Color(0xFF1D2030), Color(0xFF272A3B), Color(0xFF323547),
    Color(0xFFEAE6F0), Color(0xFFB3B0C2), Color(0xFF4A4C60), Color(0xFF31344A),
)

// Semantic accents reused across themes.
private val HerbL = Accent(Herb, Color.White, HerbContainer, HerbDeep)
private val HerbD = Accent(HerbLight, HerbDeep, HerbNightContainer, HerbContainer)
private val HoneyL = Accent(Honey, Color.White, HoneyContainer, HoneyDeep)
private val HoneyD = Accent(HoneyLight, HoneyDeep, HoneyNightContainer, HoneyContainer)
private val TealL = Accent(Color(0xFF2F7A78), Color.White, Color(0xFFCDEBE8), Color(0xFF002624))
private val TealD = Accent(Color(0xFF8FD3CF), Color(0xFF003735), Color(0xFF1E4F4D), Color(0xFFCDEBE8))

private fun light(p: Accent, s: Accent, t: Accent, n: Neutrals) = lightColorScheme(
    primary = p.color, onPrimary = p.on, primaryContainer = p.container, onPrimaryContainer = p.onContainer,
    secondary = s.color, onSecondary = s.on, secondaryContainer = s.container, onSecondaryContainer = s.onContainer,
    tertiary = t.color, onTertiary = t.on, tertiaryContainer = t.container, onTertiaryContainer = t.onContainer,
    background = n.bg, onBackground = n.ink, surface = n.bg, onSurface = n.ink,
    surfaceVariant = n.high, onSurfaceVariant = n.inkSoft,
    surfaceContainerLowest = n.lowest, surfaceContainerLow = n.low, surfaceContainer = n.mid,
    surfaceContainerHigh = n.high, surfaceContainerHighest = n.highest,
    inverseSurface = n.ink, inverseOnSurface = n.bg, inversePrimary = p.container,
    outline = n.rule, outlineVariant = n.ruleSoft,
    error = Chili, onError = Color.White, errorContainer = ChiliContainer, onErrorContainer = TomatoDeep,
    scrim = Color.Black,
)

private fun dark(p: Accent, s: Accent, t: Accent, n: Neutrals) = darkColorScheme(
    primary = p.color, onPrimary = p.on, primaryContainer = p.container, onPrimaryContainer = p.onContainer,
    secondary = s.color, onSecondary = s.on, secondaryContainer = s.container, onSecondaryContainer = s.onContainer,
    tertiary = t.color, onTertiary = t.on, tertiaryContainer = t.container, onTertiaryContainer = t.onContainer,
    background = n.bg, onBackground = n.ink, surface = n.bg, onSurface = n.ink,
    surfaceVariant = n.high, onSurfaceVariant = n.inkSoft,
    surfaceContainerLowest = n.lowest, surfaceContainerLow = n.low, surfaceContainer = n.mid,
    surfaceContainerHigh = n.high, surfaceContainerHighest = n.highest,
    inverseSurface = n.ink, inverseOnSurface = n.bg, inversePrimary = p.container,
    outline = n.rule, outlineVariant = n.ruleSoft,
    error = ChiliLight, onError = TomatoDeep, errorContainer = ChiliNightContainer, onErrorContainer = ChiliContainer,
    scrim = Color.Black,
)

private val lightSchemes: Map<AppTheme, ColorScheme> = mapOf(
    AppTheme.GLINT to light(
        Accent(Color(0xFF1B6B73), Color.White, Color(0xFFCDEBED), Color(0xFF00282C)), HerbL,
        Accent(Color(0xFFB8801A), Color.White, Color(0xFFFFE3A6), Color(0xFF3A2600)), MistLight,
    ),
    AppTheme.TOMATO to light(Accent(Tomato, Color.White, TomatoContainer, TomatoDeep), HerbL, HoneyL, WarmLight),
    AppTheme.MATCHA to light(Accent(Color(0xFF4E7A3A), Color.White, Color(0xFFDAEACB), Color(0xFF12300A)), TealL, HoneyL, GreenLight),
    AppTheme.BLUEBERRY to light(Accent(Color(0xFF4557A8), Color.White, Color(0xFFDDE1F8), Color(0xFF0E1A56)), HerbL, HoneyL, CoolLight),
    AppTheme.SAKURA to light(Accent(Color(0xFFC0457A), Color.White, Color(0xFFFBDCE8), Color(0xFF4A0B27)), HerbL, HoneyL, WarmLight),
    AppTheme.SESAME to light(Accent(Ink, Paper, PaperHighest, Ink), HerbL, HoneyL, WarmLight),
    AppTheme.LATTE to light(Accent(Color(0xFF8B5E3C), Color.White, Color(0xFFEBD9C6), Color(0xFF2F1A0A)), HerbL, HoneyL, BeigeLight),
    AppTheme.SUMMER_SKY to light(
        Accent(Color(0xFF1F7AE0), Color.White, Color(0xFFD3E6FC), Color(0xFF002B5C)), HerbL,
        Accent(Color(0xFFC98B00), Color.White, Color(0xFFFFE8A8), Color(0xFF3D2A00)), SkyLight,
    ),
    AppTheme.CITY_POP to light(
        Accent(Color(0xFFC2378A), Color.White, Color(0xFFFFD8EC), Color(0xFF3E0028)), TealL,
        Accent(Color(0xFFD9661F), Color.White, Color(0xFFFFDCC6), Color(0xFF3A1300)), PopLight,
    ),
    AppTheme.MATSURI to light(
        Accent(Color(0xFFC8372D), Color.White, Color(0xFFFFDAD3), Color(0xFF410000)),
        Accent(Color(0xFF2F4A8A), Color.White, Color(0xFFDBE1FA), Color(0xFF001748)),
        Accent(Color(0xFFB58500), Color.White, Color(0xFFFFE08A), Color(0xFF3A2A00)), WarmLight,
    ),
)

private val darkSchemes: Map<AppTheme, ColorScheme> = mapOf(
    AppTheme.GLINT to dark(
        Accent(Color(0xFF7FD0D6), Color(0xFF00363A), Color(0xFF1B535A), Color(0xFFCFF3F5)), HerbD,
        Accent(Color(0xFFF2C96B), Color(0xFF3F2A00), Color(0xFF5E4300), Color(0xFFFFE3A6)), InkWaterDark,
    ),
    AppTheme.TOMATO to dark(Accent(TomatoLight, TomatoDeep, TomatoNightContainer, TomatoContainer), HerbD, HoneyD, WarmDark),
    AppTheme.MATCHA to dark(Accent(Color(0xFFA8D08D), Color(0xFF173808), Color(0xFF2F4F22), Color(0xFFD2EDC0)), TealD, HoneyD, GreenDark),
    AppTheme.BLUEBERRY to dark(Accent(Color(0xFFB7C2FF), Color(0xFF1A2769), Color(0xFF333F82), Color(0xFFDEE1FF)), HerbD, HoneyD, CoolDark),
    AppTheme.SAKURA to dark(Accent(Color(0xFFFFB0CB), Color(0xFF5C1133), Color(0xFF7A2A4D), Color(0xFFFFD9E4)), HerbD, HoneyD, WarmDark),
    AppTheme.SESAME to dark(Accent(Cream, Night, NightHighest, Cream), HerbD, HoneyD, WarmDark),
    AppTheme.LATTE to dark(Accent(Color(0xFFE0B891), Color(0xFF3F2410), Color(0xFF5A3D26), Color(0xFFF6DEC6)), HerbD, HoneyD, BeigeDark),
    AppTheme.SUMMER_SKY to dark(
        Accent(Color(0xFF9CCBFF), Color(0xFF00315F), Color(0xFF0E4A8A), Color(0xFFD3E6FC)), HerbD,
        Accent(Color(0xFFF2C94C), Color(0xFF3D2A00), Color(0xFF5A4300), Color(0xFFFFE8A8)), SkyDark,
    ),
    AppTheme.CITY_POP to dark(
        Accent(Color(0xFFFF8FCB), Color(0xFF5A0039), Color(0xFF7E1E5A), Color(0xFFFFD8EC)), TealD,
        Accent(Color(0xFFFFB68A), Color(0xFF552000), Color(0xFF7A3510), Color(0xFFFFDCC6)), PopDark,
    ),
    AppTheme.MATSURI to dark(
        Accent(Color(0xFFFFB4A9), Color(0xFF690003), Color(0xFF8C1D18), Color(0xFFFFDAD3)),
        Accent(Color(0xFFB4C5FF), Color(0xFF0A2A6B), Color(0xFF2A437F), Color(0xFFDBE1FA)),
        Accent(Color(0xFFF1C048), Color(0xFF3E2E00), Color(0xFF594300), Color(0xFFFFE08A)), IndigoNight,
    ),
)
