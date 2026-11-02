package xyz.sattar.javid.proqueue.domain.usecase.user

import xyz.sattar.javid.proqueue.domain.UserRepository

class BuyAddonUseCase(private val repository: UserRepository) {
    suspend operator fun invoke(packId: Int, useWallet: Boolean = false, walletOnly: Boolean = false) =
        repository.buyAddon(packId, useWallet, walletOnly)
}
