package bot.paradox.music.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import bot.paradox.music.R
import bot.paradox.music.data.ThemeMode

/** The Paradox monochrome palette (same values as College WiFi). No accent hue, no purple anywhere. */
@Immutable
data class Pal(
    val dark: Boolean,
    val bg: Color, val surface: Color, val surface2: Color, val hairline: Color,
    val text: Color, val muted: Color, val faint: Color,
    val primary: Color, val onPrimary: Color, val scrim: Color,
)

val DarkPal = Pal(true, Color(0xFF0E0E10), Color(0xFF1A1A1D), Color(0xFF232327), Color(0xFF2A2A2F),
    Color(0xFFEDEDED), Color(0xFFA3A3AA), Color(0xFF6F6F77), Color(0xFFE6E6E6), Color(0xFF111113), Color(0x99000000))
val LightPal = Pal(false, Color(0xFFF6F6F4), Color(0xFFFFFFFF), Color(0xFFEEEEEC), Color(0xFFE8E8E5),
    Color(0xFF141416), Color(0xFF5E5E66), Color(0xFF9E9EA5), Color(0xFF1C1C1F), Color(0xFFF6F6F4), Color(0x47000000))

val LocalPal = staticCompositionLocalOf { DarkPal }

@OptIn(ExperimentalTextApi::class)
private fun nunito(w: Int) = Font(R.font.nunito, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

@OptIn(ExperimentalTextApi::class)
val Nunito = FontFamily(nunito(400), nunito(500), nunito(600), nunito(650), nunito(700), nunito(750), nunito(800), nunito(900))

fun ts(size: Float, weight: Int, color: Color = Color.Unspecified, tracking: Float = 0f) =
    TextStyle(fontFamily = Nunito, fontWeight = FontWeight(weight), fontSize = size.sp, letterSpacing = (size * tracking).sp, color = color)

@Composable
fun MusicTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    val p = if (dark) DarkPal else LightPal
    // Every Material role is pinned to the monochrome palette, so no dynamic (wallpaper) colour can leak in.
    val scheme = if (dark) darkColorScheme(
        primary = p.primary, onPrimary = p.onPrimary, primaryContainer = p.surface2, onPrimaryContainer = p.text,
        secondary = p.muted, onSecondary = p.onPrimary, secondaryContainer = p.surface2, onSecondaryContainer = p.text,
        tertiary = p.muted, onTertiary = p.onPrimary, tertiaryContainer = p.surface2, onTertiaryContainer = p.text,
        background = p.bg, onBackground = p.text, surface = p.bg, onSurface = p.text, surfaceVariant = p.surface2, onSurfaceVariant = p.muted,
        surfaceTint = Color.Transparent, inverseSurface = p.text, inverseOnSurface = p.bg, inversePrimary = p.onPrimary,
        outline = p.hairline, outlineVariant = p.hairline, scrim = p.scrim, error = Color(0xFFE5484D), onError = Color.White,
        surfaceContainer = p.surface, surfaceContainerHigh = p.surface2, surfaceContainerHighest = p.surface2, surfaceContainerLow = p.surface,
        surfaceContainerLowest = p.bg, surfaceBright = p.surface2, surfaceDim = p.bg,
    ) else lightColorScheme(
        primary = p.primary, onPrimary = p.onPrimary, primaryContainer = p.surface2, onPrimaryContainer = p.text,
        secondary = p.muted, onSecondary = p.onPrimary, secondaryContainer = p.surface2, onSecondaryContainer = p.text,
        tertiary = p.muted, onTertiary = p.onPrimary, tertiaryContainer = p.surface2, onTertiaryContainer = p.text,
        background = p.bg, onBackground = p.text, surface = p.bg, onSurface = p.text, surfaceVariant = p.surface2, onSurfaceVariant = p.muted,
        surfaceTint = Color.Transparent, inverseSurface = p.text, inverseOnSurface = p.bg, inversePrimary = p.onPrimary,
        outline = p.hairline, outlineVariant = p.hairline, scrim = p.scrim, error = Color(0xFFD13438), onError = Color.White,
        surfaceContainer = p.surface, surfaceContainerHigh = p.surface2, surfaceContainerHighest = p.surface2, surfaceContainerLow = p.surface,
        surfaceContainerLowest = p.bg, surfaceBright = p.surface, surfaceDim = p.surface2,
    )
    val type = Typography(
        bodyLarge = ts(16f, 500), bodyMedium = ts(14.5f, 500), bodySmall = ts(13f, 500),
        titleLarge = ts(22f, 800), titleMedium = ts(17f, 750), titleSmall = ts(15f, 750),
        labelLarge = ts(15f, 750), labelMedium = ts(13f, 700), labelSmall = ts(11.5f, 700),
        headlineSmall = ts(24f, 800), headlineMedium = ts(28f, 800), headlineLarge = ts(32f, 850),
        displaySmall = ts(36f, 850),
    )
    CompositionLocalProvider(LocalPal provides p) { MaterialTheme(colorScheme = scheme, typography = type, content = content) }
}
