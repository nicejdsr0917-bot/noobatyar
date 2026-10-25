package xyz.sattar.javid.proqueue.feature.businessDetail

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.data.localDataSource.business.toDomain
import xyz.sattar.javid.proqueue.domain.BusinessRepository

class ClientBusinessDetailViewModel(
    private val businessRepository: BusinessRepository
) : BaseViewModel<ClientBusinessDetailState, ClientBusinessDetailPartialState, ClientBusinessDetailEvent, ClientBusinessDetailIntent>(
    initialState = ClientBusinessDetailState()
) {

    override fun handleIntent(intent: ClientBusinessDetailIntent): Flow<ClientBusinessDetailPartialState> {
        return when (intent) {
            is ClientBusinessDetailIntent.LoadBusiness -> flow {
                emit(ClientBusinessDetailPartialState.Loading(true))
                try {
                    when (val res = businessRepository.getClientBusinessDetail(intent.businessId)) {
                        is ApiResponse.Success -> {
                            val business = res.data.toDomain()
                            emit(ClientBusinessDetailPartialState.BusinessLoaded(business))
                        }
                        is ApiResponse.Error -> {
                            val cached = businessRepository.getBusinessById(intent.businessId)
                            if (cached != null) {
                                emit(ClientBusinessDetailPartialState.BusinessLoaded(cached))
                            } else {
                                emit(ClientBusinessDetailPartialState.Error(res.message))
                            }
                        }
                    }
                } catch (e: Exception) {
                    val cached = businessRepository.getBusinessById(intent.businessId)
                    if (cached != null) {
                        emit(ClientBusinessDetailPartialState.BusinessLoaded(cached))
                    } else {
                        emit(ClientBusinessDetailPartialState.Error(e.message ?: "خطا در دریافت اطلاعات مرکز"))
                    }
                } finally {
                    emit(ClientBusinessDetailPartialState.Loading(false))
                }
            }
            is ClientBusinessDetailIntent.RequestBooking -> flow {
                emit(ClientBusinessDetailPartialState.RequestingBooking(true))
                try {
                    when (val res = businessRepository.requestOnlineBooking(intent.businessId)) {
                        is ApiResponse.Success -> {
                            emit(ClientBusinessDetailPartialState.BookingRequested(res.data.onlineBookingRequestsCount))
                            val msg = "درخواست شما با موفقیت ثبت شد. به زودی به مدیر مرکز اطلاع داده می‌شود."
                            emit(ClientBusinessDetailPartialState.Toast(msg))
                            sendEvent(ClientBusinessDetailEvent.ShowToast(msg))
                        }
                        is ApiResponse.Error -> {
                            emit(ClientBusinessDetailPartialState.Error(res.message))
                            sendEvent(ClientBusinessDetailEvent.ShowToast(res.message))
                        }
                    }
                } catch (e: Exception) {
                    val msg = e.message ?: "خطا در ثبت درخواست نوبت آنلاین"
                    emit(ClientBusinessDetailPartialState.Error(msg))
                    sendEvent(ClientBusinessDetailEvent.ShowToast(msg))
                } finally {
                    emit(ClientBusinessDetailPartialState.RequestingBooking(false))
                }
            }
        }
    }

    override fun createErrorState(message: String): ClientBusinessDetailPartialState = ClientBusinessDetailPartialState.Error(message)

    override fun reduceState(
        currentState: ClientBusinessDetailState,
        partialState: ClientBusinessDetailPartialState
    ): ClientBusinessDetailState {
        return when (partialState) {
            is ClientBusinessDetailPartialState.Loading -> currentState.copy(isLoading = partialState.isLoading)
            is ClientBusinessDetailPartialState.BusinessLoaded -> currentState.copy(
                business = partialState.business,
                bookingRequestsCount = partialState.business.onlineBookingRequestsCount,
                isLoading = false,
                errorMessage = null
            )
            is ClientBusinessDetailPartialState.RequestingBooking -> currentState.copy(
                isRequestingBooking = partialState.isRequesting
            )
            is ClientBusinessDetailPartialState.BookingRequested -> currentState.copy(
                bookingRequestsCount = partialState.newCount,
                hasRequestedBooking = true,
                isRequestingBooking = false
            )
            is ClientBusinessDetailPartialState.Error -> currentState.copy(
                errorMessage = partialState.message,
                isLoading = false,
                isRequestingBooking = false
            )
            is ClientBusinessDetailPartialState.Toast -> currentState.copy(
                successToast = partialState.message
            )
        }
    }
}
