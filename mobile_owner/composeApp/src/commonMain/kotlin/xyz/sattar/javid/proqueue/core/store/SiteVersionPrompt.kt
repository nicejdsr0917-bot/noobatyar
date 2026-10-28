package xyz.sattar.javid.proqueue.core.store

/**
 * Invites users of a store build (Myket, Bazaar) over to the site build, where
 * releases land first. Store builds lag behind by the stores' review time, so
 * after a few days of use we suggest the site APK — same package and signing
 * key, so it installs over the store build and keeps everything.
 *
 * Only store builds set [SiteVersionPromptProvider.prompt] (see MainActivity),
 * so the site build and iOS never see it.
 */
interface SiteVersionPrompt {
    /** True once the user has used the app long enough and hasn't just dismissed it. */
    fun isDue(): Boolean

    /** Hide it for a while — after either button, so it comes back if they didn't switch. */
    fun postpone()
}

object SiteVersionPromptProvider {
    var prompt: SiteVersionPrompt? = null

    const val SITE_URL = "https://noobatyar.ir"
}
