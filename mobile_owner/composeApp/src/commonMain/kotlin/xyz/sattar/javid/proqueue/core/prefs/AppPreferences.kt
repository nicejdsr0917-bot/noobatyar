package xyz.sattar.javid.proqueue.core.prefs

import kotlinx.coroutines.flow.Flow
import xyz.sattar.javid.proqueue.core.i18n.AppLanguage
import xyz.sattar.javid.proqueue.core.state.AppThemeMode

/**
 * What the owner app persists as preferences. [PreferencesManager] is the
 * platform `expect object`, so [DefaultAppPreferences] adapts it to this interface.
 */
interface AppPreferences {
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
    fun hasSeenOnboarding(): Boolean
    suspend fun setOnboardingSeen()
    fun getLanguage(): AppLanguage
    suspend fun setLanguage(language: AppLanguage)
}

object DefaultAppPreferences : AppPreferences {
    override val themeMode get() = PreferencesManager.themeMode
    override val defaultBusinessId get() = PreferencesManager.defaultBusinessId
    override val notificationsEnabled get() = PreferencesManager.notificationsEnabled
    override val notificationReminderMinutes get() = PreferencesManager.notificationReminderMinutes
    override suspend fun setThemeMode(mode: AppThemeMode) = PreferencesManager.setThemeMode(mode)
    override suspend fun setDefaultBusinessId(id: Long?) = PreferencesManager.setDefaultBusinessId(id)
    override suspend fun setNotificationsEnabled(enabled: Boolean) = PreferencesManager.setNotificationsEnabled(enabled)
    override suspend fun setNotificationReminderMinutes(minutes: Int) = PreferencesManager.setNotificationReminderMinutes(minutes)
    override fun messageTemplate(businessId: Long) = PreferencesManager.messageTemplate(businessId)
    override suspend fun setMessageTemplate(businessId: Long, template: String) = PreferencesManager.setMessageTemplate(businessId, template)
    override fun getMessageTemplate(businessId: Long) = PreferencesManager.getMessageTemplate(businessId)
    override fun getNotificationReminderMinutes() = PreferencesManager.getNotificationReminderMinutes()
    override fun hasSeenOnboarding() = PreferencesManager.hasSeenOnboarding()
    override suspend fun setOnboardingSeen() = PreferencesManager.setOnboardingSeen()
    override fun getLanguage() = PreferencesManager.getLanguage()
    override suspend fun setLanguage(language: AppLanguage) = PreferencesManager.setLanguage(language)
}
