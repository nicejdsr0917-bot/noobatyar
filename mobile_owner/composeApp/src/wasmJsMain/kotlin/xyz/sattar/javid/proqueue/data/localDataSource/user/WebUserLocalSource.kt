package xyz.sattar.javid.proqueue.data.localDataSource.user

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xyz.sattar.javid.proqueue.domain.model.user.Subscription
import xyz.sattar.javid.proqueue.domain.model.user.User
import xyz.sattar.javid.proqueue.domain.model.user.UserRole

/**
 * The signed-in user, kept in localStorage beside the tokens
 * (TokenManager.wasmJs.kt) — the web counterpart of RoomUserLocalSource's
 * single row. It used to live in memory only: the tokens survived a page
 * reload and the user did not, so the panel stayed signed in as nobody and
 * the profile card read "کاربر نوبت‌یار".
 *
 * Only name/phone/role, no secrets. The subscription stays in memory: it is
 * fetched fresh whenever a screen needs it.
 */
class WebUserLocalSource : UserLocalSource {
    private val userState = MutableStateFlow(readStoredUser())
    private val subscriptionState = MutableStateFlow<Subscription?>(null)

    override suspend fun insertUser(user: User) {
        userState.value = user
        safeSet(KEY_USER, Json.encodeToString(StoredUser.serializer(), StoredUser.from(user)))
    }

    override fun getUserById(id: Int): Flow<User?> = userState

    override fun getCurrentUser(): Flow<User?> = userState

    override suspend fun insertSubscription(subscription: Subscription) {
        subscriptionState.value = subscription
    }

    override fun getActiveSubscription(): Flow<Subscription?> = subscriptionState

    override suspend fun clearUser() {
        userState.value = null
        safeRemove(KEY_USER)
    }

    override suspend fun clearSubscription() {
        subscriptionState.value = null
    }

    @Serializable
    private data class StoredUser(
        val id: Int,
        val phone: String,
        val name: String,
        val role: String,
        val isEmployee: Boolean,
        val joinedAt: String,
    ) {
        fun toUser() = User(id, phone, name, UserRole.fromString(role), isEmployee, joinedAt)

        companion object {
            fun from(user: User) = StoredUser(
                user.id, user.phone, user.name, user.userType.value, user.isEmployee, user.joinedAt,
            )
        }
    }

    private companion object {
        const val KEY_USER = "current_user"

        // Same guards as TokenManager.wasmJs.kt: storage can throw (private
        // mode, disabled storage), and a bad or old entry must not crash boot.
        fun readStoredUser(): User? = try {
            localStorage.getItem(KEY_USER)?.let {
                Json.decodeFromString(StoredUser.serializer(), it).toUser()
            }
        } catch (e: Throwable) {
            null
        }

        fun safeSet(key: String, value: String) {
            try { localStorage.setItem(key, value) } catch (e: Throwable) {}
        }

        fun safeRemove(key: String) {
            try { localStorage.removeItem(key) } catch (e: Throwable) {}
        }
    }
}
