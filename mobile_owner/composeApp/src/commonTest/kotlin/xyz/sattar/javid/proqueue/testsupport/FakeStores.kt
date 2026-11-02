package xyz.sattar.javid.proqueue.testsupport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.sattar.javid.proqueue.core.i18n.AppLanguage
import xyz.sattar.javid.proqueue.core.network.TokenStore
import xyz.sattar.javid.proqueue.core.prefs.AppPreferences
import xyz.sattar.javid.proqueue.core.state.AppThemeMode
import xyz.sattar.javid.proqueue.core.state.SelectedBusinessStore
import xyz.sattar.javid.proqueue.domain.model.business.Business

class FakeBusinessStore(initial: Business? = null) : SelectedBusinessStore {
    private val state = MutableStateFlow(initial)
    override val selectedBusiness: StateFlow<Business?> = state.asStateFlow()
    override fun selectBusiness(business: Business) { state.value = business }
    override fun clearBusiness() { state.value = null }
}

class FakeTokenStore(var access: String? = null, var refresh: String? = null) : TokenStore {
    var clearCalls = 0
    override fun getAccessToken() = access
    override fun getRefreshToken() = refresh
    override fun saveTokens(accessToken: String, refreshToken: String) { access = accessToken; refresh = refreshToken }
    override fun clearTokens() { clearCalls++; access = null; refresh = null }
}

class FakeAppPreferences : AppPreferences {
    val defaultBusiness = MutableStateFlow<Long?>(null)
    val reminderMinutes = MutableStateFlow(15)
    val templates = mutableMapOf<Long, MutableStateFlow<String?>>()
    private fun tpl(id: Long) = templates.getOrPut(id) { MutableStateFlow(null) }
    override val themeMode: Flow<AppThemeMode> = MutableStateFlow(AppThemeMode.entries.first())
    override val defaultBusinessId: Flow<Long?> = defaultBusiness
    override val notificationsEnabled: Flow<Boolean> = MutableStateFlow(true)
    override val notificationReminderMinutes: Flow<Int> = reminderMinutes
    override suspend fun setThemeMode(mode: AppThemeMode) {}
    override suspend fun setDefaultBusinessId(id: Long?) { defaultBusiness.value = id }
    override suspend fun setNotificationsEnabled(enabled: Boolean) {}
    override suspend fun setNotificationReminderMinutes(minutes: Int) { reminderMinutes.value = minutes }
    override fun messageTemplate(businessId: Long): Flow<String?> = tpl(businessId)
    override suspend fun setMessageTemplate(businessId: Long, template: String) { tpl(businessId).value = template }
    override fun getMessageTemplate(businessId: Long): String? = tpl(businessId).value
    override fun getNotificationReminderMinutes(): Int = reminderMinutes.value
    override fun hasSeenOnboarding() = true
    override suspend fun setOnboardingSeen() {}
    override fun getLanguage() = AppLanguage.entries.first()
    override suspend fun setLanguage(language: AppLanguage) {}
}
