package xyz.sattar.javid.proqueue.feature.businessDetail

import androidx.compose.runtime.Immutable
import xyz.sattar.javid.proqueue.domain.model.business.Business

@Immutable
data class ClientBusinessDetailState(
    val isLoading: Boolean = true,
    val business: Business? = null,
    val hasRequestedBooking: Boolean = false,
    val isRequestingBooking: Boolean = false,
    val bookingRequestsCount: Int = 0,
    val errorMessage: String? = null,
    val successToast: String? = null
)

sealed interface ClientBusinessDetailIntent {
    data class LoadBusiness(val businessId: Long) : ClientBusinessDetailIntent
    data class RequestBooking(val businessId: Long) : ClientBusinessDetailIntent
}

sealed interface ClientBusinessDetailPartialState {
    data class Loading(val isLoading: Boolean) : ClientBusinessDetailPartialState
    data class BusinessLoaded(val business: Business) : ClientBusinessDetailPartialState
    data class RequestingBooking(val isRequesting: Boolean) : ClientBusinessDetailPartialState
    data class BookingRequested(val newCount: Int) : ClientBusinessDetailPartialState
    data class Error(val message: String?) : ClientBusinessDetailPartialState
    data class Toast(val message: String?) : ClientBusinessDetailPartialState
}

sealed interface ClientBusinessDetailEvent {
    data class ShowToast(val message: String) : ClientBusinessDetailEvent
}
