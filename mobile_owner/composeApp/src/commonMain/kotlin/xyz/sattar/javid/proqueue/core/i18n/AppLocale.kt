package xyz.sattar.javid.proqueue.core.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.LayoutDirection
import xyz.sattar.javid.proqueue.core.prefs.PreferencesManager

enum class AppLanguage(val code: String) {
    FA("fa"),
    EN("en");

    companion object {
        /** Persian unless English was explicitly chosen. */
        fun fromCode(code: String?): AppLanguage = entries.firstOrNull { it.code == code } ?: FA
    }
}

/**
 * The UI language, chosen in Settings and persisted by PreferencesManager.
 *
 * Every string goes through the generated [Str] accessors, which read
 * [language] at call time. It is snapshot state, so a composable that showed a
 * string recomposes when the language changes — no Activity restart and no
 * platform-locale override (Compose resources only follow the *system* locale,
 * which the owner may not want to change). Messages already resolved into a
 * ViewModel state keep the language they were built in until they're rebuilt.
 */
object AppLocale {
    // Read synchronously on first use, so the very first frame already has
    // the saved language and direction.
    var language by mutableStateOf(PreferencesManager.getLanguage())
        private set

    suspend fun change(newLanguage: AppLanguage) {
        language = newLanguage
        PreferencesManager.setLanguage(newLanguage)
    }

    val isEnglish: Boolean get() = language == AppLanguage.EN
    val isRtl: Boolean get() = language == AppLanguage.FA
    val layoutDirection: LayoutDirection
        get() = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
}

/**
 * Fills `%1$s` / `%1$d` (positional) and `%s` / `%d` (sequential) placeholders
 * the way the strings.xml files write them. Used by the generated [Str]
 * functions; arguments are already display-ready (digits converted by the
 * caller where needed).
 */
internal fun formatString(template: String, vararg args: Any?): String {
    var next = 0
    return PLACEHOLDER.replace(template) { m ->
        val index = m.groupValues[1].toIntOrNull()?.minus(1) ?: next++
        args.getOrNull(index)?.toString().orEmpty()
    }
}

private val PLACEHOLDER = Regex("""%(?:(\d+)\$)?[sd]""")
