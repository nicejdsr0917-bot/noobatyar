package xyz.sattar.javid.proqueue.feature.lastVisitors

sealed interface LastVisitorsIntent {
    data object LoadAppointments : LastVisitorsIntent
    data class OnAppointmentOptionsClick(val appointmentId: Long) : LastVisitorsIntent
    data object OnCreateAppointmentClick : LastVisitorsIntent
    data class OnEditAppointment(val appointmentId: Long) : LastVisitorsIntent
    data class OnDeleteAppointment(val appointmentId: Long) : LastVisitorsIntent
    data class OnUpdateStatus(val appointmentId: Long, val status: String) : LastVisitorsIntent
    data class OnMarkCompleted(val appointmentId: Long) : LastVisitorsIntent
    data class OnMarkNoShow(val appointmentId: Long) : LastVisitorsIntent
    data object DismissDialog : LastVisitorsIntent
    data class OnTabSelected(val index: Int) : LastVisitorsIntent
    data class OnAppointmentClick(val visitorId: Long) : LastVisitorsIntent
    data class OnSendMessage(
        val appointmentId: Long,
        val type: String,
        val content: String,
        val businessTitle: String
    ) : LastVisitorsIntent
    data class OnFilterChanged(val filter: AppointmentFilter) : LastVisitorsIntent
    data class ShowFilterSheet(val show: Boolean) : LastVisitorsIntent
    data object ClearFilter : LastVisitorsIntent
    /** Range report: pick a window. Clamped to one month before it is sent. */
    data class OnRangeChanged(val from: Long, val to: Long) : LastVisitorsIntent
    /** Range report: switch between the customer list and the appointment list. */
    data class OnRangeSubTabSelected(val index: Int) : LastVisitorsIntent
    data object LoadRangeReport : LastVisitorsIntent
    data object LoadMoreRangeCustomers : LastVisitorsIntent
    /** Dispatched right after a toast is shown so it can't be re-shown/deduped. */
    data object ClearMessage : LastVisitorsIntent
}
