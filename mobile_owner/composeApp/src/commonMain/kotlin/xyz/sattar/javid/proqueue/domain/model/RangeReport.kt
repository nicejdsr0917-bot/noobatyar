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
 * How the مشتری‌ها tab orders its customers. Applied by the server
 * ([apiValue] is its `ordering` parameter) because the list is paged — sorting
 * the rows in hand would only order the current page.
 */
enum class CustomerOrdering(val apiValue: String, val label: String) {
    /** Most appointments first — the regulars. */
    VISITS("visits", "بیشترین نوبت"),
    /** Latest appointment first — who was in most recently. */
    RECENT("recent", "آخرین مراجعه"),
}

/**
 * The مشتری‌ها tab's data for one window: who was in it, and how often.
 *
 * It used to carry the window's totals (appointments, completed, no-shows)
 * as well, from a second call to the stats endpoint. Those duplicated the
 * home dashboard and the نوبت‌ها tab's own count, so the tab now shows only
 * what nothing else does — the customer roll-up — and makes one request.
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
    val customers: List<RangeCustomer> = emptyList(),
    val customersTotal: Int = 0,
    val currentPage: Int = 1,
    val totalPages: Int = 1
) {
    val hasMoreCustomers: Boolean get() = currentPage < totalPages
}
