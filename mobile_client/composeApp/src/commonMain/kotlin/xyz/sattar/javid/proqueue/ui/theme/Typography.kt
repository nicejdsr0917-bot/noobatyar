package xyz.sattar.javid.proqueue.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Yekan Bakh: Regular, SemiBold (as Medium and SemiBold) and Bold. */
@Composable
expect fun yakanFontFamily(): FontFamily

/**
 * The app's one type scale. Every style is Yekan Bakh and none is tracked:
 * letter spacing pulls the joined letters of Persian script apart. The theme
 * also makes [bodyMedium] the default style (see AppTheme), so a Text or a text
 * field that names no style still looks like the rest.
 */
@Composable
fun appTypography(): Typography {
    val font = yakanFontFamily()
    fun style(weight: FontWeight, size: Int, line: Int) =
        TextStyle(fontFamily = font, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = 0.sp)

    return Typography(
        displayLarge = style(FontWeight.Bold, 57, 64),
        displayMedium = style(FontWeight.Bold, 45, 52),
        displaySmall = style(FontWeight.Bold, 36, 44),
        headlineLarge = style(FontWeight.Bold, 32, 40),
        headlineMedium = style(FontWeight.Bold, 28, 36),
        headlineSmall = style(FontWeight.Bold, 24, 32),
        titleLarge = style(FontWeight.Bold, 22, 28),
        titleMedium = style(FontWeight.SemiBold, 16, 24),
        titleSmall = style(FontWeight.Medium, 14, 20),
        bodyLarge = style(FontWeight.Normal, 16, 24),
        bodyMedium = style(FontWeight.Normal, 14, 20),
        bodySmall = style(FontWeight.Normal, 12, 16),
        labelLarge = style(FontWeight.Medium, 14, 20),
        labelMedium = style(FontWeight.Medium, 12, 16),
        labelSmall = style(FontWeight.Medium, 11, 16),
    )
}
