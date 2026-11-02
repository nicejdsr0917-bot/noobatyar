package xyz.sattar.javid.proqueue.domain.usecase.user

import xyz.sattar.javid.proqueue.domain.UserRepository

class GetWalletHistoryUseCase(private val repository: UserRepository) {
    suspend operator fun invoke(page: Int, pageSize: Int) = repository.getWalletHistory(page, pageSize)
}
