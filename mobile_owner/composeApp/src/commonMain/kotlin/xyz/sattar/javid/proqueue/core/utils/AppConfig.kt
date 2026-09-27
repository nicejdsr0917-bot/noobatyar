package xyz.sattar.javid.proqueue.core.utils

/**
 * Which server this build talks to.
 *
 * Deliberately NOT backed by buildkonfig's generated `BuildKonfig` object on
 * Android: buildkonfig has no idea Android product flavors ("prod"/"local",
 * declared in composeApp/build.gradle.kts) exist — its own "flavor" is an
 * unrelated Gradle property (`-Pbuildkonfig.flavor=...`) nobody sets here, so
 * every Android build, "local" included, was silently getting buildkonfig's
 * un-flavored (production) values. AGP's own `buildConfigField`, set per
 * product flavor in the android{} block, genuinely varies by flavor, so the
 * Android `actual` reads that instead. iOS has no flavor concept — see
 * AppInfo.isIOS callers elsewhere — so its `actual` keeps using BuildKonfig,
 * which is fine since that object only ever holds the production values now.
 */
expect object AppConfig {
    val baseUrl: String
    val bookingBaseUrl: String
}
