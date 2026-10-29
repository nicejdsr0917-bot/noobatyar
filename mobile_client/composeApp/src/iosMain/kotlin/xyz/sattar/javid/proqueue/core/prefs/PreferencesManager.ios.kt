package xyz.sattar.javid.proqueue.core.prefs

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import platform.Foundation.*

import xyz.sattar.javid.proqueue.core.state.AppThemeMode
import xyz.sattar.javid.proqueue.domain.model.business.DEFAULT_REMINDER_MINUTES

actual object PreferencesManager {
    private val defaults = NSUserDefaults.standardUserDefaults

    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DEFAULT_BUSINESS_ID = "default_business_id"
    private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
    private const val KEY_NOTIFICATION_REMINDER_MINUTES = "notification_reminder_minutes"
    private const val KEY_MESSAGE_TEMPLATE_PREFIX = "message_template_" // suffix: businessId
    private const val KEY_SAVED_BUSINESSES = "saved_business_ids"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_RECENT_SEARCHES = "recent_searches"
    private const val KEY_WELCOME_SEEN = "welcome_seen"
    private const val KEY_RECENT_CITIES = "recent_city_codes"

    private val _themeMode = MutableStateFlow(
        AppThemeMode.values()[defaults.integerForKey(KEY_THEME_MODE).toInt().coerceIn(0, 2)]
    )
    private fun readDefaultBusinessId(): Long? = if (defaults.objectForKey(KEY_DEFAULT_BUSINESS_ID) != null) {
        defaults.integerForKey(KEY_DEFAULT_BUSINESS_ID)
    } else null
    private val _defaultBusinessId = MutableStateFlow(readDefaultBusinessId())
    
    private val _notificationsEnabled = MutableStateFlow(
        defaults.boolForKey(KEY_NOTIFICATIONS_ENABLED)
    )
    
    private val _notificationReminderMinutes = MutableStateFlow(
        if (defaults.objectForKey(KEY_NOTIFICATION_REMINDER_MINUTES) != null) 
            defaults.integerForKey(KEY_NOTIFICATION_REMINDER_MINUTES).toInt() 
        else DEFAULT_REMINDER_MINUTES
    )

    actual val themeMode: Flow<AppThemeMode> = _themeMode
    actual val defaultBusinessId: Flow<Long?> = _defaultBusinessId
    actual val notificationsEnabled: Flow<Boolean> = _notificationsEnabled
    actual val notificationReminderMinutes: Flow<Int> = _notificationReminderMinutes
    private val templateFlows = mutableMapOf<Long, MutableStateFlow<String?>>()

    actual suspend fun setThemeMode(mode: AppThemeMode) {
        defaults.setInteger(mode.ordinal.toLong(), KEY_THEME_MODE)
        _themeMode.value = mode
    }

    actual suspend fun setDefaultBusinessId(id: Long?) {
        if (id == null) {
            defaults.removeObjectForKey(KEY_DEFAULT_BUSINESS_ID)
            _defaultBusinessId.value = null
        } else {
            defaults.setInteger(id, KEY_DEFAULT_BUSINESS_ID)
            _defaultBusinessId.value = id
        }
    }

    actual suspend fun setNotificationsEnabled(enabled: Boolean) {
        defaults.setBool(enabled, KEY_NOTIFICATIONS_ENABLED)
        _notificationsEnabled.value = enabled
    }

    actual suspend fun setNotificationReminderMinutes(minutes: Int) {
        defaults.setInteger(minutes.toLong(), KEY_NOTIFICATION_REMINDER_MINUTES)
        _notificationReminderMinutes.value = minutes
    }

    actual fun messageTemplate(businessId: Long): Flow<String?> {
        return templateFlows.getOrPut(businessId) {
            val key = KEY_MESSAGE_TEMPLATE_PREFIX + businessId
            val value = defaults.stringForKey(key)
            MutableStateFlow(if (value != null) value else null)
        }
    }

    actual suspend fun setMessageTemplate(businessId: Long, template: String) {
        val key = KEY_MESSAGE_TEMPLATE_PREFIX + businessId
        defaults.setObject(template, key)
        val flow = templateFlows.getOrPut(businessId) { MutableStateFlow(null) }
        flow.value = template
    }

    actual fun getMessageTemplate(businessId: Long): String? {
        val key = KEY_MESSAGE_TEMPLATE_PREFIX + businessId
        return defaults.stringForKey(key)
    }

    actual fun getNotificationReminderMinutes(): Int {
        val value = defaults.integerForKey(KEY_NOTIFICATION_REMINDER_MINUTES)
        return if (value == 0L) DEFAULT_REMINDER_MINUTES else value.toInt()
    }

    private val _savedBusinessIds = MutableStateFlow(
        (defaults.stringForKey(KEY_SAVED_BUSINESSES) ?: "")
            .split(',').mapNotNull { it.toLongOrNull() }.toSet()
    )
    actual val savedBusinessIds: Flow<Set<Long>> = _savedBusinessIds

    actual suspend fun toggleSavedBusiness(id: Long) {
        val current = _savedBusinessIds.value
        val updated = if (id in current) current - id else current + id
        defaults.setObject(updated.joinToString(","), KEY_SAVED_BUSINESSES)
        _savedBusinessIds.value = updated
    }

    actual fun deviceId(): String {
        defaults.stringForKey(KEY_DEVICE_ID)?.let { return it }
        val id = generateDeviceId()
        defaults.setObject(id, KEY_DEVICE_ID)
        return id
    }

    private val _recentSearches = MutableStateFlow(
        (defaults.stringForKey(KEY_RECENT_SEARCHES) ?: "").split('\n').filter { it.isNotBlank() }
    )
    actual val recentSearches: Flow<List<String>> = _recentSearches

    actual suspend fun addRecentSearch(query: String) {
        if (query.isBlank()) return
        val updated = _recentSearches.value.withRecent(query)
        defaults.setObject(updated.joinToString("\n"), KEY_RECENT_SEARCHES)
        _recentSearches.value = updated
    }

    actual suspend fun clearRecentSearches() {
        defaults.removeObjectForKey(KEY_RECENT_SEARCHES)
        _recentSearches.value = emptyList()
    }

    private val _recentCityCodes = MutableStateFlow(defaults.stringForKey(KEY_RECENT_CITIES).toCityCodes())
    actual val recentCityCodes: Flow<List<Int>> = _recentCityCodes

    actual suspend fun selectCity(code: Int) {
        val updated = _recentCityCodes.value.withRecentCity(code)
        defaults.setObject(updated.joinToString(","), KEY_RECENT_CITIES)
        _recentCityCodes.value = updated
    }

    actual fun hasSeenWelcome(): Boolean = defaults.boolForKey(KEY_WELCOME_SEEN)

    actual suspend fun setWelcomeSeen() {
        defaults.setBool(true, KEY_WELCOME_SEEN)
    }
}
