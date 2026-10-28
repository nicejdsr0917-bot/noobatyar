package xyz.sattar.javid.proqueue.domain.usecase.user

import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.domain.UserRepository

class CreatePaymentUseCase(private val repository: UserRepository) {
    suspend operator fun invoke(planId: Int): ApiResponse<xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.PaymentResponseDto> {
        if (planId == 1) {
            return ApiResponse.Error(
                message = Str.trial_plan_cant_be_purchased,
                code = 400
            )
        }
        return repository.createPayment(planId.toString())
    }
}
