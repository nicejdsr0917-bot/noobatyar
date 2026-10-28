package xyz.sattar.javid.proqueue.core.utils

import kotlin.experimental.ExperimentalNativeApi
import platform.Foundation.NSBundle

// Platform.isDebugBinary is the only way to ask a Kotlin/Native binary whether
// it was built in debug, and it has been experimental since it was added; the
// opt-in is required to compile at all, not a choice about API stability.
@OptIn(ExperimentalNativeApi::class)
actual object AppInfo {
    actual val versionCode: Int = (NSBundle.mainBundle.infoDictionary?.get("CFBundleVersion") as? String)?.toIntOrNull() ?: 100
    actual val versionName: String = (NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String) ?: "1.0.0"
    actual val isIOS: Boolean = true
    actual val isWeb: Boolean = false
    actual val isDebug: Boolean = kotlin.native.Platform.isDebugBinary
}
