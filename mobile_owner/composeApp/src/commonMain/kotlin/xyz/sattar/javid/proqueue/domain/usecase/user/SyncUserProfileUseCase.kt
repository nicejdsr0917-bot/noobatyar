package xyz.sattar.javid.proqueue.domain.usecase.user

import kotlinx.coroutines.flow.first
import xyz.sattar.javid.proqueue.core.network.TokenStore
import xyz.sattar.javid.proqueue.core.network.userIdFromJwt
import xyz.sattar.javid.proqueue.domain.UserRepository

/**
 * Re-reads the signed-in user from the server into the local store, which
 * the profile screen observes. The id comes from the stored user, or — when
 * there is none, as on the web panel after a reload — from the access token.
 * Returns false when there is no session to sync.
 */
class SyncUserProfileUseCase(
    private val repository: UserRepository,
    private val tokens: TokenStore,
) {
    suspend operator fun invoke(): Boolean {
        val id = repository.getCurrentUser().first()?.id
            ?: tokens.getAccessToken()?.let(::userIdFromJwt)
            ?: return false
        repository.syncUserProfile(id)
        return true
    }
}
