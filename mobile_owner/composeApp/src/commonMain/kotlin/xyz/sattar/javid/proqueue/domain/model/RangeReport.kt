package xyz.sattar.javid.proqueue.domain.model

/** One customer's activity inside the reported window. */
data class RangeCustomer(
    val visitorId: Long,
    val fullName: String,
    val phoneNumber: String,
    val appointmentCount: Int,
    val lastVisit: Long?
)

/**
 * The "گزارش بازه‌ای" tab's data for one window: how the window went, and who
 * was in it.
 *
 * [dateFrom]/[dateTo] are the window the *server* measured, not the one that
 * was asked for — it caps anything wider than a month (see
 * backend/appointment/views/range_visitors_view.py). When [clamped] is true
 * the UI moves its own picker to match, so the numbers on screen and the
 * dates above them always describe the same range.
 */
data class RangeReport(
    val dateFrom: Long,
    val dateTo: Long,
    val clamped: Boolean = false,
    val totalAppointments: Int = 0,
    val completedAppointments: Int = 0,
    val noShowAppointments: Int = 0,
    val uniqueVisitors: Int = 0,
    val customers: List<RangeCustomer> = emptyList(),
    val customersTotal: Int = 0,
    val currentPage: Int = 1,
    val totalPages: Int = 1
) {
    val hasMoreCustomers: Boolean get() = currentPage < totalPages
}
