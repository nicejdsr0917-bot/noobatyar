package xyz.sattar.javid.proqueue.ui.theme

import androidx.compose.ui.graphics.Color

// Consumer-app palette.
//
// Neutral surfaces, one accent. Brand violet (docs/BRAND_GUIDE_FOR_AI.md) is
// reserved for the primary action and the selected state — everything else is
// greyscale, so the one thing that is violet on a screen is the thing to tap.
// #7C3AED rather than the lighter brand #8B5CF6 for text/buttons on white: it
// clears WCAG AA (4.5:1) for normal-size text, the lighter one does not.

// Accent
val Accent = Color(0xFF7C3AED)
val AccentDarkTheme = Color(0xFFA78BFA)
val AccentSoft = Color(0xFFF3EEFE)
val AccentSoftDark = Color(0xFF2A2140)

// Light neutrals
val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceAlt = Color(0xFFF5F5F7)
val LightBorder = Color(0xFFE4E4E9)
val LightDivider = Color(0xFFEFEFF2)
val LightText = Color(0xFF16161D)
val LightTextSecondary = Color(0xFF6B6B76)

// Dark neutrals
val DarkBackground = Color(0xFF0F0F12)
val DarkSurface = Color(0xFF16161B)
val DarkSurfaceAlt = Color(0xFF1F1F26)
val DarkBorder = Color(0xFF30303A)
val DarkDivider = Color(0xFF24242C)
val DarkText = Color(0xFFF3F3F6)
val DarkTextSecondary = Color(0xFFA1A1AC)

// Status — used only for small text/dots, never as a fill.
val SuccessColor = Color(0xFF0E9F6E)
val SuccessColorDark = Color(0xFF34D399)
val WarningColor = Color(0xFFD97706)
val ErrorColor = Color(0xFFDC2626)
val ErrorColorDark = Color(0xFFF87171)
