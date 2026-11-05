package xyz.sattar.javid.proqueue.core.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import xyz.sattar.javid.proqueue.ProQueueApp
import androidx.core.content.edit

import xyz.sattar.javid.proqueue.core.state.AppThemeMode
import xyz.sattar.javid.proqueue.domain.model.business.DEFAULT_REMINDER_MINUTES

actual object PreferencesManager {
    private val context: Context get() = ProQueueApp.appContext
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DEFAULT_BUSINESS_ID = "default_business_id"
    private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
    private const val KEY_NOTIFICATION_REMINDER_MINUTES = "notification_reminder_minutes"
    private const val KEY_MESSAGE_TEMPLATE_PREFIX = "message_template_" // suffix: businessId
    private const val KEY_SAVED_BUSINESSES = "saved_business_ids"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_RECENT_SEARCHES = "recent_searches"
    private const val KEY_WELCOME_SEEN = "welcome_seen"
    private const val KEY_REVIEW_PROMPTS_DISMISSED = "review_prompts_dismissed"
    private const val KEY_VISITOR_TOKEN = "visitor_token"
    private const val KEY_LAST_LOCATION = "last_location"
    private const val KEY_MY_INSURANCE = "my_insurance"
    private const val KEY_RECENT_CITIES = "recent_city_codes"

    private val _themeMode = MutableStateFlow(
        // Light until the user picks a theme (product decision); a stored
        // choice, «خودکار» included, still wins.
        if (prefs.contains(KEY_THEME_MODE)) AppThemeMode.values()[prefs.getInt(KEY_THEME_MODE, 0).coerceIn(0, 2)]
        else AppThemeMode.LIGHT
    )
    private val _defaultBusinessId = MutableStateFlow<Long?>(
        if (prefs.contains(KEY_DEFAULT_BUSINESS_ID)) prefs.getLong(KEY_DEFAULT_BUSINESS_ID, 0L) else null
    )
    private val _notificationsEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, false)
    )
    private val _notificationReminderMinutes = MutableStateFlow(
        prefs.getInt(KEY_NOTIFICATION_REMINDER_MINUTES, DEFAULT_REMINDER_MINUTES)
    )

    actual val themeMode: Flow<AppThemeMode> = _themeMode
    actual val defaultBusinessId: Flow<Long?> = _defaultBusinessId
    actual val notificationsEnabled: Flow<Boolean> = _notificationsEnabled
    actual val notificationReminderMinutes: Flow<Int> = _notificationReminderMinutes
    private val templateFlows = mutableMapOf<Long, MutableStateFlow<String?>>()

    actual suspend fun setThemeMode(mode: AppThemeMode) {
        prefs.edit(commit = true) { putInt(KEY_THEME_MODE, mode.ordinal) }
        _themeMode.value = mode
    }

    actual suspend fun setDefaultBusinessId(id: Long?) {
        prefs.edit(commit = true) {
            if (id == null) remove(KEY_DEFAULT_BUSINESS_ID) else putLong(
                KEY_DEFAULT_BUSINESS_ID,
                id
            )
        }
        _defaultBusinessId.value = id
    }

    actual suspend fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit(commit = true) { putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled) }
        _notificationsEnabled.value = enabled
    }

    actual suspend fun setNotificationReminderMinutes(minutes: Int) {
        prefs.edit(commit = true) { putInt(KEY_NOTIFICATION_REMINDER_MINUTES, minutes) }
        _notificationReminderMinutes.value = minutes
    }

    actual fun messageTemplate(businessId: Long): Flow<String?> {
        return templateFlows.getOrPut(businessId) {
            val key = KEY_MESSAGE_TEMPLATE_PREFIX + businessId
            MutableStateFlow(
                if (prefs.contains(key)) prefs.getString(key, null) else null
            )
        }
    }

    actual suspend fun setMessageTemplate(businessId: Long, template: String) {
        val key = KEY_MESSAGE_TEMPLATE_PREFIX + businessId
        prefs.edit(commit = true) { putString(key, template) }
        val flow = templateFlows.getOrPut(businessId) { MutableStateFlow(null) }
        flow.value = template
    }

    actual fun getMessageTemplate(businessId: Long): String? {
        val key = KEY_MESSAGE_TEMPLATE_PREFIX + businessId
        return if (prefs.contains(key)) prefs.getString(key, null) else null
    }

    actual fun getNotificationReminderMinutes(): Int {
        return prefs.getInt(KEY_NOTIFICATION_REMINDER_MINUTES, DEFAULT_REMINDER_MINUTES)
    }

    private val _savedBusinessIds = MutableStateFlow(
        prefs.getStringSet(KEY_SAVED_BUSINESSES, emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull() }.toSet()
    )
    actual val savedBusinessIds: Flow<Set<Long>> = _savedBusinessIds

    actual suspend fun toggleSavedBusiness(id: Long) {
        val current = _savedBusinessIds.value
        val updated = if (id in current) current - id else current + id
        prefs.edit(commit = true) { putStringSet(KEY_SAVED_BUSINESSES, updated.map { it.toString() }.toSet()) }
        _savedBusinessIds.value = updated
    }

    actual fun deviceId(): String {
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
        val id = generateDeviceId()
        prefs.edit(commit = true) { putString(KEY_DEVICE_ID, id) }
        return id
    }

    // Newline-joined: a StringSet would lose the most-recent-first order.
    private val _recentSearches = MutableStateFlow(
        prefs.getString(KEY_RECENT_SEARCHES, null).orEmpty().split('\n').filter { it.isNotBlank() }
    )
    actual val recentSearches: Flow<List<String>> = _recentSearches

    actual suspend fun addRecentSearch(query: String) {
        if (query.isBlank()) return
        val updated = _recentSearches.value.withRecent(query)
        prefs.edit(commit = true) { putString(KEY_RECENT_SEARCHES, updated.joinToString("\n")) }
        _recentSearches.value = updated
    }

    actual suspend fun clearRecentSearches() {
        prefs.edit(commit = true) { remove(KEY_RECENT_SEARCHES) }
        _recentSearches.value = emptyList()
    }

    private val _recentCityCodes = MutableStateFlow(prefs.getString(KEY_RECENT_CITIES, null).toCityCodes())
    actual val recentCityCodes: Flow<List<Int>> = _recentCityCodes

    actual suspend fun selectCity(code: Int) {
        val updated = _recentCityCodes.value.withRecentCity(code)
        prefs.edit(commit = true) { putString(KEY_RECENT_CITIES, updated.joinToString(",")) }
        _recentCityCodes.value = updated
    }

    actual fun hasSeenWelcome(): Boolean = prefs.getBoolean(KEY_WELCOME_SEEN, false)

    actual suspend fun setWelcomeSeen() {
        prefs.edit(commit = true) { putBoolean(KEY_WELCOME_SEEN, true) }
    }

    actual fun dismissedReviewPrompts(): Set<Long> =
        prefs.getString(KEY_REVIEW_PROMPTS_DISMISSED, null).toIdSet()

    actual suspend fun dismissReviewPrompt(appointmentId: Long) {
        val ids = (dismissedReviewPrompts() + appointmentId).toList().takeLast(50)
        prefs.edit(commit = true) { putString(KEY_REVIEW_PROMPTS_DISMISSED, ids.joinToString(",")) }
    }

    private val _visitorToken by lazy { MutableStateFlow(prefs.getString(KEY_VISITOR_TOKEN, null)) }
    actual val visitorToken: Flow<String?> get() = _visitorToken
    actual fun getVisitorToken(): String? = _visitorToken.value

    actual suspend fun setVisitorToken(token: String?) {
        prefs.edit(commit = true) { if (token == null) remove(KEY_VISITOR_TOKEN) else putString(KEY_VISITOR_TOKEN, token) }
        _visitorToken.value = token
    }

    private val _myInsurance by lazy { MutableStateFlow(prefs.getString(KEY_MY_INSURANCE, null)) }
    actual val myInsuranceSlug: Flow<String?> get() = _myInsurance

    actual suspend fun setMyInsuranceSlug(slug: String?) {
        prefs.edit(commit = true) { if (slug == null) remove(KEY_MY_INSURANCE) else putString(KEY_MY_INSURANCE, slug) }
        _myInsurance.value = slug
    }

    private val _lastLocation by lazy { MutableStateFlow(decodeSavedLocation(prefs.getString(KEY_LAST_LOCATION, null))) }
    actual val lastLocation: Flow<SavedLocation?> get() = _lastLocation

    actual suspend fun saveLocation(location: SavedLocation) {
        prefs.edit(commit = true) { putString(KEY_LAST_LOCATION, location.encode()) }
        _lastLocation.value = location
    }
}
