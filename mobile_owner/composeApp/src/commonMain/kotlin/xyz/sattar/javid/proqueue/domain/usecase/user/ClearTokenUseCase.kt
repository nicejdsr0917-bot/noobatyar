package xyz.sattar.javid.proqueue.domain.usecase.user

import xyz.sattar.javid.proqueue.core.network.TokenStore

class ClearTokenUseCase(private val tokens: TokenStore) {
    operator fun invoke() {
        tokens.clearTokens()
    }
}
