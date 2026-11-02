package xyz.sattar.javid.proqueue.core.network

/**
 * Where the session tokens live. [TokenManager] is the platform `expect
 * object`, so [DefaultTokenStore] adapts it to this interface.
 */
interface TokenStore {
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun saveTokens(accessToken: String, refreshToken: String)
    fun clearTokens()
}

object DefaultTokenStore : TokenStore {
    override fun getAccessToken() = TokenManager.getAccessToken()
    override fun getRefreshToken() = TokenManager.getRefreshToken()
    override fun saveTokens(accessToken: String, refreshToken: String) = TokenManager.saveTokens(accessToken, refreshToken)
    override fun clearTokens() = TokenManager.clearTokens()
}
