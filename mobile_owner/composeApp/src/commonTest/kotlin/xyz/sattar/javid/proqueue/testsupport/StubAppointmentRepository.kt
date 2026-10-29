package xyz.sattar.javid.proqueue.testsupport

import xyz.sattar.javid.proqueue.domain.model.appointment.Appointment
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails
import xyz.sattar.javid.proqueue.domain.model.DashboardStats
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentOrdering
import xyz.sattar.javid.proqueue.domain.AppointmentRepository

/** Every member throws; a test subclasses it and overrides what it exercises. */
open class StubAppointmentRepository : AppointmentRepository {
    override suspend fun createAppointment(appointment: Appointment): Long = TODO("not stubbed")

    override suspend fun getWaitingQueue(businessId: Long, date: Long): List<AppointmentWithDetails> = TODO("not stubbed")

    override suspend fun getTodayAppointments(businessId: Long): List<AppointmentWithDetails> = TODO("not stubbed")

    override suspend fun getAllAppointmentsForBusiness(businessId: Long): List<AppointmentWithDetails> = TODO("not stubbed")

    override suspend fun updateAppointmentStatus(appointmentId: Long, status: String): Boolean = TODO("not stubbed")

    override suspend fun updateAppointment(appointmentId: Long, date: Long, duration: Int?, description: String?, selectedServices: String?): Boolean = TODO("not stubbed")

    override suspend fun removeAppointment(appointmentId: Long): Boolean = TODO("not stubbed")

    override suspend fun getVisitorHistoryForBusiness(visitorId: Long, businessId: Long): List<AppointmentWithDetails> = TODO("not stubbed")

    override suspend fun getTodayStats(businessId: Long, date: Long): DashboardStats = TODO("not stubbed")

    override suspend fun getAppointmentById(appointmentId: Long): Appointment? = TODO("not stubbed")

    override suspend fun getAllWaitingAppointments(businessId: Long): List<AppointmentWithDetails> = TODO("not stubbed")

    override suspend fun deleteAppointmentsByVisitorId(visitorId: Long): Unit = TODO("not stubbed")

    override suspend fun getConflictingAppointments(businessId: Long, startTime: Long, endTime: Long, defaultDuration: Int): List<AppointmentWithDetails> = TODO("not stubbed")

    override suspend fun getAppointmentsForDate(businessId: Long, date: Long): List<AppointmentWithDetails> = TODO("not stubbed")

    override suspend fun getDailyCounts(businessId: Long, days: Int, daysAhead: Int): List<xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.DailyCountDto> = TODO("not stubbed")

    override suspend fun getRangeReport(
        businessId: Long,
        dateFrom: Long,
        dateTo: Long,
        ordering: xyz.sattar.javid.proqueue.domain.model.CustomerOrdering,
        page: Int,
        pageSize: Int
    ): xyz.sattar.javid.proqueue.domain.model.RangeReport? = TODO("not stubbed")

    override suspend fun syncAppointments(
        businessId: Long,
        visitorId: Long?,
        status: String?,
        date: Long?,
        dateFrom: Long?,
        dateTo: Long?,
        ordering: AppointmentOrdering?,
        page: Int,
        pageSize: Int,
        
        replaceWindow: Boolean
    ): Boolean = TODO("not stubbed")
}
