package xyz.sattar.javid.proqueue.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import xyz.sattar.javid.proqueue.core.i18n.AppLocale
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.nunito_bold
import proqueue.composeapp.generated.resources.nunito_medium
import proqueue.composeapp.generated.resources.nunito_regular
import proqueue.composeapp.generated.resources.yakan_bold
import proqueue.composeapp.generated.resources.yakan_medium
import proqueue.composeapp.generated.resources.yekan_regular

// Was an expect/actual with Android and iOS actuals that were
// character-for-character identical (both just load Res.font.* via
// compose-resources, which is already multiplatform). Folded into commonMain
// as part of web-readiness prep — one fewer platform seam to reimplement for
// a wasmJs target later.
@Composable
fun yakanFontFamily() = FontFamily(
    Font(Res.font.yekan_regular, FontWeight.Normal),
    Font(Res.font.yakan_medium, FontWeight.Medium),
    Font(Res.font.yakan_bold, FontWeight.Bold)
)

/**
 * English UI font: Nunito (Google Fonts, OFL), in the same three weights as
 * Yekan Bakh. Its fully rounded stroke ends carry Yekan's soft, friendly
 * shapes into Latin text without hard corners, and its strokes are lighter
 * than the first pick (Rubik), which read heavy and blunt next to Yekan.
 * Latin-only subset: Persian text in an English UI (customer and business
 * names) falls back to the system font, or on the web to Yekan, which main.kt
 * registers as a glyph fallback before the first frame.
 */
@Composable
fun nunitoFontFamily() = FontFamily(
    Font(Res.font.nunito_regular, FontWeight.Normal),
    Font(Res.font.nunito_medium, FontWeight.Medium),
    Font(Res.font.nunito_bold, FontWeight.Bold)
)

/** Yekan Bakh for Persian, Nunito for English. */
@Composable
fun appFontFamily(): FontFamily = if (AppLocale.isEnglish) nunitoFontFamily() else yakanFontFamily()

@Composable
fun appTypography(fontFamily: FontFamily = appFontFamily()) = Typography(
    displayLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp
    ),
    displaySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)