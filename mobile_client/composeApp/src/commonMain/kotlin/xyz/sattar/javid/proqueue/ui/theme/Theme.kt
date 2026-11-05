package xyz.sattar.javid.proqueue.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import xyz.sattar.javid.proqueue.core.state.AppThemeMode


// Every role is set explicitly: anything left to Material3's baseline comes out
// as its stock lilac/brown tones, which is exactly the off-brand tint this
// palette exists to avoid. surfaceVariant = filled controls (search, chips),
// outline = control borders, outlineVariant = hairline dividers.
private val DarkColorScheme = darkColorScheme(
    primary = AccentDarkTheme,
    onPrimary = Color(0xFF14101F),
    primaryContainer = AccentSoftDark,
    onPrimaryContainer = AccentDarkTheme,
    secondary = AccentDarkTheme,
    tertiary = AccentDarkTheme,
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceAlt,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceAlt,
    surfaceContainerHighest = DarkBorder,
    outline = DarkBorder,
    outlineVariant = DarkDivider,
    error = ErrorColorDark,
)

private val LightColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentSoft,
    onPrimaryContainer = Accent,
    secondary = Accent,
    tertiary = Accent,
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceAlt,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceAlt,
    surfaceContainerHighest = LightBorder,
    outline = LightBorder,
    outlineVariant = LightDivider,
    error = ErrorColor,
)


/**
 * Whether the dark scheme is active.
 *
 * AppTheme already knows this exactly, but never published it, so components
 * needing a light/dark branch resorted to guessing from the luminance of
 * `colorScheme.surface`. That works until a scheme changes and is impossible
 * to grep for; this is the same answer, stated once at the source.
 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

@Composable
fun AppTheme(
    themeMode: AppThemeMode = AppThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    val isDarkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val colorScheme = if (isDarkTheme) DarkColorScheme else LightColorScheme

    SystemAppearance(!isDarkTheme)

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalIsDarkTheme provides isDarkTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = appTypography(),
        ) {
            // LocalContentColor defaults to black and only a Surface/Scaffold
            // resets it, so text on a bare `background()` (welcome, sheets,
            // overlays) stayed black on the dark scheme. Seed it from the theme.
            CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground) {
                // The default for any text that names no style, typed text included.
                ProvideTextStyle(MaterialTheme.typography.bodyMedium, content)
            }
        }
    }
}

@Composable
expect fun SystemAppearance(isDark: Boolean)
