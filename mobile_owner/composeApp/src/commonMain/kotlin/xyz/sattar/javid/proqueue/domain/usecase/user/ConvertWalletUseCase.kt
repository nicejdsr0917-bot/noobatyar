package xyz.sattar.javid.proqueue.domain.usecase.user

import xyz.sattar.javid.proqueue.domain.UserRepository

/** Turn toman-wallet balance into SMS or booking credit (accounting/wallet/convert/). */
class ConvertWalletUseCase(private val repository: UserRepository) {
    suspend operator fun invoke(kind: String, count: Int) = repository.convertWallet(kind, count)
}
