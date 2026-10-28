package xyz.sattar.javid.proqueue.domain

import xyz.sattar.javid.proqueue.domain.model.appointment.Appointment
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails
import xyz.sattar.javid.proqueue.domain.model.DashboardStats
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentOrdering

interface AppointmentRepository {
    suspend fun createAppointment(appointment: Appointment): Long
    suspend fun getWaitingQueue(businessId: Long, date: Long): List<AppointmentWithDetails>
    suspend fun getTodayAppointments(businessId: Long): List<AppointmentWithDetails>
    suspend fun getAllAppointmentsForBusiness(businessId: Long): List<AppointmentWithDetails>
    suspend fun updateAppointmentStatus(appointmentId: Long, status: String): Boolean
    suspend fun updateAppointment(appointmentId: Long, date: Long, duration: Int?, description: String?, selectedServices: String? = null): Boolean
    suspend fun removeAppointment(appointmentId: Long): Boolean
    suspend fun getVisitorHistory(visitorId: Long): List<AppointmentWithDetails>
    suspend fun getVisitorHistoryForBusiness(visitorId: Long, businessId: Long): List<AppointmentWithDetails>
    suspend fun getTodayStats(businessId: Long, date: Long): DashboardStats
    suspend fun getAppointmentById(appointmentId: Long): Appointment?
    suspend fun getAllWaitingAppointments(businessId: Long): List<AppointmentWithDetails>
    suspend fun deleteAppointmentsByVisitorId(visitorId: Long)
    suspend fun getConflictingAppointments(businessId: Long, startTime: Long, endTime: Long, defaultDuration: Int): List<AppointmentWithDetails>
    suspend fun getAppointmentsForDate(businessId: Long, date: Long): List<AppointmentWithDetails>
    suspend fun getDailyCounts(businessId: Long, days: Int, daysAhead: Int = 0): List<xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.DailyCountDto>

    /**
     * Range report for the مراجعین screen's third tab. Server-only, with no
     * local-cache read: the summary is a whole-window aggregate and the
     * customer rows are grouped in the database, neither of which can be
     * rebuilt from the paged rows the local cache holds (and on web that
     * cache is only an in-memory source anyway).
     *
     * Returns null when the call fails, so the caller can tell "no data in
     * this range" (an empty report) apart from "couldn't load".
     */
    suspend fun getRangeReport(
        businessId: Long,
        dateFrom: Long,
        dateTo: Long,
        ordering: xyz.sattar.javid.proqueue.domain.model.CustomerOrdering =
            xyz.sattar.javid.proqueue.domain.model.CustomerOrdering.VISITS,
        page: Int = 1,
        pageSize: Int = 20
    ): xyz.sattar.javid.proqueue.domain.model.RangeReport?

    suspend fun syncAppointments(
        businessId: Long,
        visitorId: Long? = null,
        status: String? = null,
        date: Long? = null,
        dateFrom: Long? = null,
        dateTo: Long? = null,
        ordering: AppointmentOrdering? = AppointmentOrdering.DATE_DESC,
        page: Int = 1,
        pageSize: Int = 20
    ): Boolean
}
