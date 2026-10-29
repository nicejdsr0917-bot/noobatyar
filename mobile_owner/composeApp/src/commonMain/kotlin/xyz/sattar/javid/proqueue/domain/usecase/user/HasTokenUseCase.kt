package xyz.sattar.javid.proqueue.domain.usecase.user

import xyz.sattar.javid.proqueue.core.network.TokenStore

class HasTokenUseCase(private val tokens: TokenStore) {
    operator fun invoke(): Boolean = tokens.getAccessToken() != null
}
