package xyz.sattar.javid.proqueue.core.prefs

import kotlinx.coroutines.flow.Flow

/**
 * The slice of preferences the consumer screens use: bookmarks, recent
 * searches and the browsed city. An interface so view-models can run against
 * an in-memory fake instead of the platform key-value store.
 */
interface ClientPreferences {
    /** Businesses the client bookmarked from the explore/detail screens. */
    val savedBusinessIds: Flow<Set<Long>>
    suspend fun toggleSavedBusiness(id: Long)

    /** Most recent first, at most [MAX_RECENT_SEARCHES]. */
    val recentSearches: Flow<List<String>>
    suspend fun addRecentSearch(query: String)
    suspend fun clearRecentSearches()

    /** Divar city ids ([ALL_IRAN] for "کل ایران"), newest first; empty until the first pick. */
    val recentCityCodes: Flow<List<Int>>
    suspend fun selectCity(code: Int)

    /** Visitor session from OTP sign-in; null when signed out. */
    val visitorToken: Flow<String?>
    suspend fun setVisitorToken(token: String?)

    /** The user's own insurance (an insurance slug), null until they set one. */
    val myInsuranceSlug: Flow<String?>
    suspend fun setMyInsuranceSlug(slug: String?)

    /** The user's last fix, kept so distances show before a new one arrives. */
    val lastLocation: Flow<SavedLocation?>
    suspend fun saveLocation(location: SavedLocation)
}

/** Production [ClientPreferences]: straight delegation to the platform [PreferencesManager]. */
class PlatformClientPreferences : ClientPreferences {
    override val savedBusinessIds: Flow<Set<Long>> get() = PreferencesManager.savedBusinessIds
    override suspend fun toggleSavedBusiness(id: Long) = PreferencesManager.toggleSavedBusiness(id)

    override val recentSearches: Flow<List<String>> get() = PreferencesManager.recentSearches
    override suspend fun addRecentSearch(query: String) = PreferencesManager.addRecentSearch(query)
    override suspend fun clearRecentSearches() = PreferencesManager.clearRecentSearches()

    override val recentCityCodes: Flow<List<Int>> get() = PreferencesManager.recentCityCodes
    override suspend fun selectCity(code: Int) = PreferencesManager.selectCity(code)

    override val visitorToken: Flow<String?> get() = PreferencesManager.visitorToken
    override suspend fun setVisitorToken(token: String?) = PreferencesManager.setVisitorToken(token)

    override val myInsuranceSlug: Flow<String?> get() = PreferencesManager.myInsuranceSlug
    override suspend fun setMyInsuranceSlug(slug: String?) = PreferencesManager.setMyInsuranceSlug(slug)

    override val lastLocation: Flow<SavedLocation?> get() = PreferencesManager.lastLocation
    override suspend fun saveLocation(location: SavedLocation) = PreferencesManager.saveLocation(location)
}
