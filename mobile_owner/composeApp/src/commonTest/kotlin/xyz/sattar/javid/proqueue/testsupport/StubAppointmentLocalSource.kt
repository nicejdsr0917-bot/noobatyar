package xyz.sattar.javid.proqueue.testsupport

import xyz.sattar.javid.proqueue.domain.model.appointment.Appointment
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails
import xyz.sattar.javid.proqueue.data.localDataSource.appointment.AppointmentLocalSource

/** Every member throws; a test subclasses it and overrides what it exercises. */
open class StubAppointmentLocalSource : AppointmentLocalSource {
    override suspend fun getWaitingQueue(businessId: Long, date: Long): List<AppointmentWithDetails> = TODO("not stubbed")

    override suspend fun getAllWaitingAppointments(businessId: Long): List<AppointmentWithDetails> = TODO("not stubbed")

    override suspend fun getTodayAppointments(businessId: Long): List<AppointmentWithDetails> = TODO("not stubbed")

    override suspend fun getAllAppointmentsForBusiness(businessId: Long): List<AppointmentWithDetails> = TODO("not stubbed")

    override suspend fun getVisitorHistoryForBusiness(visitorId: Long, businessId: Long): List<AppointmentWithDetails> = TODO("not stubbed")

    override suspend fun upsertAppointment(appointment: Appointment): Long = TODO("not stubbed")

    override suspend fun upsertAppointments(appointments: List<Appointment>): Unit = TODO("not stubbed")

    override suspend fun updateAppointmentStatus(appointmentId: Long, status: String, updatedAt: Long): Unit = TODO("not stubbed")

    override suspend fun updateAppointment(appointmentId: Long, date: Long, duration: Int?, description: String?, selectedServices: String?, updatedAt: Long): Unit = TODO("not stubbed")

    override suspend fun getTodayAppointmentsCount(businessId: Long, date: Long): Int = TODO("not stubbed")

    override suspend fun getTodayNoShowCount(businessId: Long, date: Long): Int = TODO("not stubbed")

    override suspend fun getTodayCancelledCount(businessId: Long, date: Long): Int = TODO("not stubbed")

    override suspend fun removeAppointmentAndReorder(appointmentId: Long): Unit = TODO("not stubbed")

    override suspend fun getAppointmentById(appointmentId: Long): Appointment? = TODO("not stubbed")

    override suspend fun getConflictingAppointments(
        businessId: Long,
        startTime: Long,
        endTime: Long,
        defaultDuration: Int
    ): List<AppointmentWithDetails> = TODO("not stubbed")

    override suspend fun getAppointmentsForDate(businessId: Long, date: Long): List<AppointmentWithDetails> = TODO("not stubbed")

    override suspend fun deleteAppointmentsByVisitorId(visitorId: Long): Unit = TODO("not stubbed")

    override suspend fun clearAll(): Unit = TODO("not stubbed")

    override suspend fun replaceWindow(
        businessId: Long,
        fromInclusive: Long,
        toExclusive: Long,
        appointments: List<Appointment>
    ): Unit = TODO("not stubbed")
}
