package xyz.sattar.javid.proqueue.core.utils

expect object AppInfo {
    val versionCode: Int
    val versionName: String
    val isIOS: Boolean
    /**
     * True for a debug build (an Android Studio "Run", not a signed release
     * APK). A Studio run has no `ANDROID_VERSION_CODE` set at build time, so
     * it falls back to the placeholder in build.gradle.kts (100) — far below
     * whatever the production force-update gate requires, which made every
     * local run hit the mandatory-update screen before anything else could be
     * tested. See VersionViewModel, the one place this is read.
     */
    val isDebug: Boolean
}
