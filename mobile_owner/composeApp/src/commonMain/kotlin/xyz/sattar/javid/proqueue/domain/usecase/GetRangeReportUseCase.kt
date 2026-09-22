package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.domain.AppointmentRepository
import xyz.sattar.javid.proqueue.domain.model.RangeReport

/**
 * Loads the مراجعین screen's range report. [MAX_RANGE_DAYS] mirrors the
 * server's own cap (backend/appointment/views/range_visitors_view.py) and is
 * what the date picker clamps against, so the UI can't offer a range the
 * server would silently narrow.
 */
class GetRangeReportUseCase(private val repository: AppointmentRepository) {

    suspend operator fun invoke(
        businessId: Long,
        dateFrom: Long,
        dateTo: Long,
        page: Int = 1,
        pageSize: Int = 20
    ): RangeReport? = repository.getRangeReport(
        businessId = businessId,
        dateFrom = dateFrom,
        dateTo = dateTo,
        page = page,
        pageSize = pageSize
    )

    companion object {
        const val MAX_RANGE_DAYS = 31
        const val DAY_MILLIS = 24L * 60 * 60 * 1000

        /** Widest allowed window, as a span in millis (31 inclusive days). */
        const val MAX_RANGE_MILLIS = (MAX_RANGE_DAYS - 1) * DAY_MILLIS
    }
}
