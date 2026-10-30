package xyz.sattar.javid.proqueue.core.utils

import androidx.compose.runtime.Composable

/** Intercepts the system back gesture/button. A no-op where there is none (iOS). */
@Composable
expect fun SystemBackHandler(enabled: Boolean = true, onBack: () -> Unit)
