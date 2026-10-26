package xyz.sattar.javid.proqueue.core.prefs

import kotlinx.coroutines.flow.Flow

import xyz.sattar.javid.proqueue.core.state.AppThemeMode

expect object PreferencesManager {
    val themeMode: Flow<AppThemeMode>
    val defaultBusinessId: Flow<Long?>
    val notificationsEnabled: Flow<Boolean>
    val notificationReminderMinutes: Flow<Int>
    suspend fun setThemeMode(mode: AppThemeMode)
    suspend fun setDefaultBusinessId(id: Long?)
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setNotificationReminderMinutes(minutes: Int)
    fun messageTemplate(businessId: Long): Flow<String?>
    suspend fun setMessageTemplate(businessId: Long, template: String)
    fun getMessageTemplate(businessId: Long): String?
    fun getNotificationReminderMinutes(): Int

    /** Businesses the client bookmarked from the explore/detail screens. */
    val savedBusinessIds: Flow<Set<Long>>
    suspend fun toggleSavedBusiness(id: Long)

    /**
     * Random per-install id, sent as `X-Device-Id` so the server can count one
     * "enable online booking" vote per device (it only ever stores a hash).
     * Not tied to any hardware identifier.
     */
    fun deviceId(): String

    /** Most recent first, at most [MAX_RECENT_SEARCHES]. */
    val recentSearches: Flow<List<String>>
    suspend fun addRecentSearch(query: String)
    suspend fun clearRecentSearches()

    /** First-launch welcome screen shown once per install. */
    fun hasSeenWelcome(): Boolean
    suspend fun setWelcomeSeen()
}

const val MAX_RECENT_SEARCHES = 8

/** Shared by both platforms: newest first, de-duplicated, capped. */
internal fun List<String>.withRecent(query: String): List<String> =
    (listOf(query.trim()) + filterNot { it.equals(query.trim(), ignoreCase = true) })
        .filter { it.isNotBlank() }
        .take(MAX_RECENT_SEARCHES)
