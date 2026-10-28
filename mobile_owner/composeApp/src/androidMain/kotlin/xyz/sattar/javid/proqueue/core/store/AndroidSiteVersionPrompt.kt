package xyz.sattar.javid.proqueue.core.store

import android.content.Context

/**
 * Timing for [SiteVersionPrompt] on Android: first due [FIRST_AFTER_MS] after
 * the first signed-in start, then again [REPEAT_AFTER_MS] after each
 * dismissal until the user is on the site build (which never installs this).
 */
class AndroidSiteVersionPrompt(context: Context) : SiteVersionPrompt {
    private val prefs = context.applicationContext.getSharedPreferences("site_version_prompt", Context.MODE_PRIVATE)

    override fun isDue(): Boolean {
        val now = System.currentTimeMillis()
        val firstSeen = prefs.getLong(KEY_FIRST_SEEN, 0L).takeIf { it > 0 }
            ?: now.also { prefs.edit().putLong(KEY_FIRST_SEEN, it).apply() }
        return now - firstSeen >= FIRST_AFTER_MS && now >= prefs.getLong(KEY_NEXT_AT, 0L)
    }

    override fun postpone() {
        prefs.edit().putLong(KEY_NEXT_AT, System.currentTimeMillis() + REPEAT_AFTER_MS).apply()
    }

    private companion object {
        const val KEY_FIRST_SEEN = "first_seen"
        const val KEY_NEXT_AT = "next_at"
        const val FIRST_AFTER_MS = 3L * 24 * 60 * 60 * 1000
        const val REPEAT_AFTER_MS = 7L * 24 * 60 * 60 * 1000
    }
}
