package xyz.sattar.javid.proqueue.feature.lastVisitors

import androidx.compose.runtime.Immutable
import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
import xyz.sattar.javid.proqueue.domain.model.CustomerOrdering
import xyz.sattar.javid.proqueue.domain.model.RangeReport
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentOrdering
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails

@Immutable
data class LastVisitorsState(
    val isLoading: Boolean = false,
    val appointments: List<AppointmentWithDetails> = emptyList(),
    val totalCount: Int = 0,
    val message: UiMessage? = null,
    val selectedAppointmentId: Long? = null,
    val showOptionsDialog: Boolean = false,
    val selectedTab: Int = 1,
    /** Status, ordering, and the date window every tab shares. The view
     *  model always stores it with both dates filled in (see
     *  [LastVisitorsViewModel.withResolvedDates]), so the date bar never has
     *  a blank end to render. */
    val filter: AppointmentFilter = AppointmentFilter(),
    val showFilterSheet: Boolean = false,
    // ── Tab 2: range report ───────────────────────────────────────────────
    /** The window [rangeReport] was requested for — the shared filter's
     *  dates, clamped to the report's cap. Compared against the filter to
     *  tell whether the report on screen is stale. */
    val rangeFrom: Long = 0L,
    val rangeTo: Long = 0L,
    val rangeLoading: Boolean = false,
    val rangeReport: RangeReport? = null,
    val rangeFailed: Boolean = false,
    val customerOrdering: CustomerOrdering = CustomerOrdering.VISITS
) {
    sealed class PartialState {
        data class IsLoading(val isLoading: Boolean) : PartialState()
        data class ShowMessage(val message: UiMessage) : PartialState()
        data object ClearMessage : PartialState()
        data class LoadAppointments(
            val appointments: List<AppointmentWithDetails>,
            val totalCount: Int
        ) : PartialState()
        data class ShowOptionsDialog(val appointmentId: Long?) : PartialState()
        data class TabSelected(val index: Int) : PartialState()
        data class UpdateFilter(val filter: AppointmentFilter) : PartialState()
        data class ShowFilterSheet(val show: Boolean) : PartialState()
        data class RangeSelected(val from: Long, val to: Long) : PartialState()
        data class RangeLoading(val isLoading: Boolean) : PartialState()
        data class RangeLoaded(val report: RangeReport) : PartialState()
        data object RangeFailed : PartialState()
        /** A further page of customers, appended to the ones already shown. */
        data class RangeCustomersAppended(val report: RangeReport) : PartialState()
        data class CustomerOrderingChanged(val ordering: CustomerOrdering) : PartialState()
    }
}

data class AppointmentFilter(
    val status: String? = null,
    /** A single day. Folded into [dateFrom]/[dateTo] on arrival; kept only
     *  so callers that still think in "one day" can pass one. */
    val date: Long? = null,
    /** Start of the first day in the window. */
    val dateFrom: Long? = null,
    /** Last millisecond of the last day in the window (inclusive). */
    val dateTo: Long? = null,
    val ordering: AppointmentOrdering = AppointmentOrdering.DATE_DESC
)

