package xyz.sattar.javid.proqueue.core.utils

import androidx.compose.runtime.Composable

// iOS has no system back button; the top bar's back drives the flow.
@Composable
actual fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
