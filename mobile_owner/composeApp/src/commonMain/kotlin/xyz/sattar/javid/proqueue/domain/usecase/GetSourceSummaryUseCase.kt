package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.domain.AppointmentRepository
import xyz.sattar.javid.proqueue.feature.home.SourceSummary

/** Bookings the app and the website brought in over a range; null when unknown. */
class GetSourceSummaryUseCase(private val repository: AppointmentRepository) {
    suspend operator fun invoke(businessId: Long, dateFrom: Long, dateTo: Long): SourceSummary? =
        repository.getSourceCounts(businessId, dateFrom, dateTo)?.let { counts ->
            SourceSummary(app = counts["APP"] ?: 0, web = counts["WEB"] ?: 0)
        }
}
