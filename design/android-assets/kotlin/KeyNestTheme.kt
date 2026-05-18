// KeyNestTheme.kt — Compose entry point. Drop into:
//   app/src/main/java/com/example/keynest/ui/theme/KeyNestTheme.kt
//
// Wraps Material3 with the KeyNest palette + typography + shapes. Mirrors
// the XML theme in values/themes.xml so View-based screens and any future
// Compose screens stay aligned to the same tokens.

package com.example.keynest.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.keynest.R

// ── Palette ─────────────────────────────────────────────────────
private val KnBlue50  = Color(0xFFEAF2FE)
private val KnBlue100 = Color(0xFFD0E0FC)
private val KnBlue500 = Color(0xFF1F6FEB)
private val KnBlue600 = Color(0xFF1457C9)
private val KnBlue700 = Color(0xFF0F4AA8)
private val KnBlue900 = Color(0xFF0A2E66)
private val KnAccent  = Color(0xFF6366F1)
private val KnSuccess = Color(0xFF10B981)
private val KnWarning = Color(0xFFF59E0B)
private val KnDanger  = Color(0xFFEF4444)

private val KnInk950  = Color(0xFF0A1020)
private val KnInk900  = Color(0xFF0F1729)
private val KnInk850  = Color(0xFF131C33)
private val KnInk800  = Color(0xFF1A2540)
private val KnInk500  = Color(0xFF5C6B8E)
private val KnInk400  = Color(0xFF7A89AD)
private val KnInk300  = Color(0xFF9CA9C7)
private val KnInk50   = Color(0xFFF1F4FA)
private val KnPaper   = Color(0xFFF6F8FC)

// ── Extra status colors exposed via CompositionLocal ────────────
data class KnStatusColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val borderSoft: Color,
)
val LocalKnStatus = staticCompositionLocalOf {
    KnStatusColors(KnSuccess, KnWarning, KnDanger, Color(0x14000000))
}

// ── ColorSchemes ────────────────────────────────────────────────
private val LightScheme = lightColorScheme(
    primary             = KnBlue500,
    onPrimary           = Color.White,
    primaryContainer    = KnBlue50,
    onPrimaryContainer  = KnBlue900,
    secondary           = KnAccent,
    onSecondary         = Color.White,
    background          = KnPaper,
    onBackground        = Color(0xFF0B1220),
    surface             = Color.White,
    onSurface           = Color(0xFF0B1220),
    surfaceVariant      = KnInk50,
    onSurfaceVariant    = KnInk500,
    outline             = Color(0x24000000),
    outlineVariant      = Color(0x14000000),
    error               = KnDanger,
    onError             = Color.White,
)

private val DarkScheme = darkColorScheme(
    primary             = Color(0xFF4D8DF4),
    onPrimary           = Color.White,
    primaryContainer    = Color(0xFF1A2D5C),
    onPrimaryContainer  = KnBlue100,
    secondary           = KnAccent,
    onSecondary         = Color.White,
    background          = KnInk950,
    onBackground        = Color(0xFFECF1FA),
    surface             = KnInk850,
    onSurface           = Color(0xFFECF1FA),
    surfaceVariant      = KnInk800,
    onSurfaceVariant    = KnInk300,
    outline             = Color(0x1FFFFFFF),
    outlineVariant      = Color(0x0FFFFFFF),
    error               = KnDanger,
    onError             = Color.White,
)

// ── Type ────────────────────────────────────────────────────────
private val Manrope = FontFamily(
    Font(R.font.manrope_regular,    FontWeight.W500),
    Font(R.font.manrope_semibold,   FontWeight.W600),
    Font(R.font.manrope_bold,       FontWeight.W700),
    Font(R.font.manrope_extrabold,  FontWeight.W800),
)
private val Mono = FontFamily(Font(R.font.jetbrains_mono, FontWeight.W500))

private val KnTypography = Typography(
    displaySmall    = TextStyle(Manrope, fontWeight = FontWeight.W800, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.56).sp), // -.02em
    headlineSmall   = TextStyle(Manrope, fontWeight = FontWeight.W800, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.52).sp),
    titleLarge      = TextStyle(Manrope, fontWeight = FontWeight.W800, fontSize = 19.sp, lineHeight = 24.sp, letterSpacing = (-0.38).sp),
    titleMedium     = TextStyle(Manrope, fontWeight = FontWeight.W700, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.16).sp),
    titleSmall      = TextStyle(Manrope, fontWeight = FontWeight.W700, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge       = TextStyle(Manrope, fontWeight = FontWeight.W600, fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium      = TextStyle(Manrope, fontWeight = FontWeight.W500, fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall       = TextStyle(Manrope, fontWeight = FontWeight.W500, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge      = TextStyle(Manrope, fontWeight = FontWeight.W700, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium     = TextStyle(Manrope, fontWeight = FontWeight.W700, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.88.sp), // .08em
)

val KnMonoText = TextStyle(Mono, fontWeight = FontWeight.W500, fontSize = 12.sp, lineHeight = 18.sp)
val KnPasswordText = TextStyle(Mono, fontWeight = FontWeight.W700, fontSize = 17.sp, letterSpacing = 1.7.sp) // .1em

// ── Shapes ──────────────────────────────────────────────────────
private val KnShapes = Shapes(
    extraSmall  = RoundedCornerShape(8.dp),
    small       = RoundedCornerShape(12.dp),
    medium      = RoundedCornerShape(16.dp),
    large       = RoundedCornerShape(20.dp),
    extraLarge  = RoundedCornerShape(28.dp),
)

// ── Theme entry point ───────────────────────────────────────────
@Composable
fun KeyNestTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val statusColors = if (darkTheme)
        KnStatusColors(KnSuccess, KnWarning, KnDanger, Color(0x1FFFFFFF))
    else
        KnStatusColors(KnSuccess, KnWarning, KnDanger, Color(0x14000000))

    androidx.compose.runtime.CompositionLocalProvider(LocalKnStatus provides statusColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography  = KnTypography,
            shapes      = KnShapes,
            content     = content,
        )
    }
}
