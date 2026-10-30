package xyz.sattar.javid.proqueue.core.utils

import androidx.compose.runtime.Composable

/**
 * True when the person asked the OS for less motion: Android's "remove
 * animations" (animator scale 0), iOS's Reduce Motion. Screens then swap
 * slides, springs and loops for plain fades or snaps.
 */
@Composable
expect fun rememberReducedMotion(): Boolean
