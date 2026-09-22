package xyz.sattar.javid.proqueue.feature.lastVisitors

import androidx.compose.runtime.Immutable
import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
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
    val filter: AppointmentFilter = AppointmentFilter(),
    val showFilterSheet: Boolean = false,
    // ── Tab 2: range report ───────────────────────────────────────────────
    /** Requested window. The server may narrow it; [rangeReport] carries what
     *  was actually measured and the reducer syncs these back to it. */
    val rangeFrom: Long = 0L,
    val rangeTo: Long = 0L,
    /** 0 = customers, 1 = appointments. */
    val rangeSubTab: Int = 0,
    val rangeLoading: Boolean = false,
    val rangeReport: RangeReport? = null,
    /** Appointments inside the window, loaded separately from the report's
     *  summary — the report aggregates, this lists. */
    val rangeAppointments: List<AppointmentWithDetails> = emptyList(),
    val rangeFailed: Boolean = false
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
        data class RangeSubTabSelected(val index: Int) : PartialState()
        data class RangeLoading(val isLoading: Boolean) : PartialState()
        data class RangeLoaded(
            val report: RangeReport,
            val appointments: List<AppointmentWithDetails>
        ) : PartialState()
        data object RangeFailed : PartialState()
        /** A further page of customers, appended to the ones already shown. */
        data class RangeCustomersAppended(val report: RangeReport) : PartialState()
    }
}

data class AppointmentFilter(
    val status: String? = null,
    val date: Long? = null,
    val dateFrom: Long? = null,
    val dateTo: Long? = null,
    val ordering: AppointmentOrdering = AppointmentOrdering.DATE_DESC
)

