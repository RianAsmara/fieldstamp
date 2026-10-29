package id.asmaraputra.fieldstamp.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.asmaraputra.fieldstamp.R

/*
 * FieldStamp design tokens.
 *
 * Principles: utilitarian and legible outdoors. One working color (survey-ink teal) for primary actions,
 * a high-visibility amber reserved for the capture action only, warm paper neutrals, small radii,
 * lists over cards, monospace for machine-like values (time, coordinates). Dynamic color is off so
 * contrast is predictable in sunlight and screenshots match the printed report.
 */

private object Palette {
    val ink = Color(0xFF0E5A6B)
    val inkLight = Color(0xFF7FC8D8)
    /** Signal yellow: capture action only (shutter, "Ambil foto"). */
    val hiVis = Color(0xFFFFC21A)
    val onHiVis = Color(0xFF1A1F24)
}

private val LightColors = lightColorScheme(
    primary = Palette.ink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E7EC),
    onPrimaryContainer = Color(0xFF04313B),
    secondary = Color(0xFF4B5C63),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE6E9),
    onSecondaryContainer = Color(0xFF1B2B31),
    tertiary = Color(0xFF7A5200),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDEA6),
    onTertiaryContainer = Color(0xFF2A1A00),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF7F7F5),
    onBackground = Color(0xFF1A1F24),
    surface = Color(0xFFF7F7F5),
    onSurface = Color(0xFF1A1F24),
    surfaceVariant = Color(0xFFE2E5E7),
    onSurfaceVariant = Color(0xFF475159),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F2F0),
    surfaceContainer = Color(0xFFEBECEA),
    surfaceContainerHigh = Color(0xFFE5E6E4),
    surfaceContainerHighest = Color(0xFFDFE0DE),
    outline = Color(0xFF76808A),
    outlineVariant = Color(0xFFC9CED4),
    inverseSurface = Color(0xFF2B3035),
    inverseOnSurface = Color(0xFFEEF0F1),
    inversePrimary = Palette.inkLight,
)

private val DarkColors = darkColorScheme(
    primary = Palette.inkLight,
    onPrimary = Color(0xFF00363F),
    primaryContainer = Color(0xFF0B4552),
    onPrimaryContainer = Color(0xFFC9EAF1),
    secondary = Color(0xFFB4C9D0),
    onSecondary = Color(0xFF1F343A),
    secondaryContainer = Color(0xFF354A51),
    onSecondaryContainer = Color(0xFFD0E5EC),
    tertiary = Color(0xFFFFC24D),
    onTertiary = Color(0xFF422C00),
    tertiaryContainer = Color(0xFF5E4100),
    onTertiaryContainer = Color(0xFFFFDEA6),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF111416),
    onBackground = Color(0xFFE3E6E8),
    surface = Color(0xFF111416),
    onSurface = Color(0xFFE3E6E8),
    surfaceVariant = Color(0xFF3E454C),
    onSurfaceVariant = Color(0xFFB8BFC6),
    surfaceContainerLowest = Color(0xFF0C0F11),
    surfaceContainerLow = Color(0xFF171A1D),
    surfaceContainer = Color(0xFF1C2023),
    surfaceContainerHigh = Color(0xFF23272B),
    surfaceContainerHighest = Color(0xFF2B3034),
    outline = Color(0xFF8A939B),
    outlineVariant = Color(0xFF3E454C),
    inverseSurface = Color(0xFFE3E6E8),
    inverseOnSurface = Color(0xFF2B3035),
    inversePrimary = Palette.ink,
)

val PlexSans = FontFamily(
    Font(R.font.plex_sans_regular, FontWeight.Normal),
    Font(R.font.plex_sans_medium, FontWeight.Medium),
    Font(R.font.plex_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plex_sans_bold, FontWeight.Bold),
)

val PlexMono = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    /** Also the section-header style (set in uppercase). */
    labelSmall = TextStyle(fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.8.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

/** 4-dp spacing scale. */
@Immutable
object Spacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    /** Horizontal screen gutter. */
    val gutter: Dp = 16.dp
    /** Minimum touch target. */
    val touch: Dp = 48.dp
    /** Minimum list-row height. */
    val row: Dp = 56.dp
    /** Height of the capture action ("Ambil foto"). */
    val capture: Dp = 64.dp
}

@Immutable
data class ExtendedColors(
    val capture: Color,
    val onCapture: Color,
    /** 2-dp edge that keeps the yellow capture button visible on light backgrounds in sunlight. */
    val captureEdge: Color,
    val success: Color,
    val warning: Color,
    val onWarningContainer: Color,
    val warningContainer: Color,
)

private val LightExtended = ExtendedColors(
    capture = Palette.hiVis,
    onCapture = Palette.onHiVis,
    captureEdge = Palette.onHiVis,
    success = Color(0xFF1E6B3A),
    warning = Color(0xFF8A5A00),
    warningContainer = Color(0xFFFFF0D1),
    onWarningContainer = Color(0xFF3D2A00),
)

private val DarkExtended = ExtendedColors(
    capture = Palette.hiVis,
    onCapture = Palette.onHiVis,
    captureEdge = Color.Transparent,
    success = Color(0xFF8AD5A0),
    warning = Color(0xFFFFC24D),
    warningContainer = Color(0xFF3D2C06),
    onWarningContainer = Color(0xFFFFDEA6),
)

@Immutable
data class MonoStyles(val small: TextStyle, val medium: TextStyle, val label: TextStyle)

private val Mono = MonoStyles(
    small = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    medium = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp),
    /** Small uppercase machine label, e.g. "KAMIS · WIB" above the date masthead. */
    label = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.2.sp),
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtended }
val LocalMono = staticCompositionLocalOf { Mono }

object FieldStampTheme {
    val colors: ColorScheme @Composable get() = MaterialTheme.colorScheme
    val extended: ExtendedColors @Composable get() = LocalExtendedColors.current
    val mono: MonoStyles @Composable get() = LocalMono.current
}

@Composable
fun FieldStampTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalExtendedColors provides if (darkTheme) DarkExtended else LightExtended,
        LocalMono provides Mono,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
