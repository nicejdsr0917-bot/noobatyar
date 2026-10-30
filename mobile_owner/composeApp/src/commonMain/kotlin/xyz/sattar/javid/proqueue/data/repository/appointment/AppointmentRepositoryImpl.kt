package xyz.sattar.javid.proqueue.data.repository.appointment

import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.data.localDataSource.appointment.AppointmentLocalSource
import xyz.sattar.javid.proqueue.data.localDataSource.business.BusinessLocalSource
import xyz.sattar.javid.proqueue.data.localDataSource.visitor.VisitorLocalSource
import xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.AppointmentApi
import xyz.sattar.javid.proqueue.domain.AppointmentRepository
import xyz.sattar.javid.proqueue.domain.model.appointment.Appointment
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails
import xyz.sattar.javid.proqueue.domain.model.DashboardStats
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentOrdering
import xyz.sattar.javid.proqueue.domain.model.visitor.Visitor
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.ExperimentalTime

class AppointmentRepositoryImpl(
    private val appointmentDao: AppointmentLocalSource,
    private val businessDao: BusinessLocalSource,
    private val visitorDao: VisitorLocalSource,
    private val appointmentApiService: AppointmentApi
) : AppointmentRepository {
    override suspend fun getRangeReport(
        businessId: Long,
        dateFrom: Long,
        dateTo: Long,
        ordering: xyz.sattar.javid.proqueue.domain.model.CustomerOrdering,
        page: Int,
        pageSize: Int
    ): xyz.sattar.javid.proqueue.domain.model.RangeReport? {
        return try {
            val visitorsResponse = appointmentApiService.getRangeVisitors(
                businessId = businessId,
                dateFrom = dateFrom,
                dateTo = dateTo,
                ordering = ordering.apiValue,
                page = page,
                pageSize = pageSize
            )
            val visitorsPage = when (visitorsResponse) {
                is ApiResponse.Success -> visitorsResponse.data
                is ApiResponse.Error -> return null
            }

            xyz.sattar.javid.proqueue.domain.model.RangeReport(
                dateFrom = visitorsPage.dateFrom,
                dateTo = visitorsPage.dateTo,
                clamped = visitorsPage.clamped,
                customers = visitorsPage.results.map {
                    xyz.sattar.javid.proqueue.domain.model.RangeCustomer(
                        visitorId = it.visitorId,
                        fullName = it.fullName,
                        phoneNumber = it.phoneNumber,
                        appointmentCount = it.appointmentCount,
                        lastVisit = it.lastVisit
                    )
                },
                customersTotal = visitorsPage.count,
                currentPage = visitorsPage.currentPage,
                totalPages = visitorsPage.totalPages
            )
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getDailyCounts(
        businessId: Long,
        days: Int,
        daysAhead: Int
    ): List<xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.DailyCountDto> {
        return try {
            when (val response = appointmentApiService.getDailyCounts(businessId, days, daysAhead)) {
                is ApiResponse.Success -> response.data
                is ApiResponse.Error -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun createAppointment(appointment: Appointment): Long {
        return try {
            val request = xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model.request.CreateAppointmentRequestDto(
                businessId = appointment.businessId,
                visitorId = appointment.visitorId,
                appointmentDate = appointment.appointmentDate,
                serviceDuration = appointment.serviceDuration,
                description = appointment.description,
                selectedServices = appointment.selectedServices
            )
            val response = appointmentApiService.createAppointment(request)
            if (response is ApiResponse.Success) {
                val dto = response.data
                val created = Appointment(
                    id = dto.id,
                    businessId = appointment.businessId,
                    visitorId = dto.visitor.id,
                    appointmentDate = dto.appointmentDate,
                    serviceDuration = dto.serviceDuration,
                    status = dto.status,
                    description = dto.description,
                    selectedServices = dto.selectedServices,
                    paymentReceipt = dto.paymentReceipt,
                    trackingCode = dto.trackingCode,
                    paymentReference = dto.paymentReference,
                    depositPaymentMethod = dto.depositPaymentMethod,
                    createdAt = DateTimeUtils.parseIsoToEpochMillis(dto.createdAt),
                    updatedAt = DateTimeUtils.parseIsoToEpochMillis(dto.updatedAt)
                )
                appointmentDao.upsertAppointment(created)
                dto.id
            } else if (response is ApiResponse.Error) {
                throw xyz.sattar.javid.proqueue.core.network.ApiException(response.message, response.code)
            } else {
                -1L
            }
        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun getWaitingQueue(businessId: Long, date: Long): List<AppointmentWithDetails> {
        return try {
            val appointments = appointmentDao.getWaitingQueue(businessId, date)

            appointments.map { appointment ->
                val visitor = visitorDao.getVisitorById(appointment.visitor.id)
                val business = businessDao.getBusinessById(appointment.business.id)

                AppointmentWithDetails(
                    appointment = appointment.appointment,
                    visitor = visitor ?: throw Exception("Visitor not found"),
                    business = business ?: throw Exception("Business not found")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun updateAppointmentStatus(appointmentId: Long, status: String): Boolean {
        return try {
            val request = xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model.request.UpdateAppointmentRequestDto(
                status = status
            )
            val response = appointmentApiService.updateAppointment(appointmentId, request)
            if (response is ApiResponse.Success) {
                appointmentDao.updateAppointmentStatus(
                    appointmentId,
                    status,
                    Clock.System.now().toEpochMilliseconds()
                )
                true
            } else if (response is ApiResponse.Error) {
                throw Exception(response.message)
            } else {
                false
            }
        } catch (e: Exception) {
            throw e
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun updateAppointment(appointmentId: Long, date: Long, duration: Int?, description: String?, selectedServices: String?): Boolean {
        return try {
            val request = xyz.sattar.javid.proqueue.data.remoteDataSource.appointment.model.request.UpdateAppointmentRequestDto(
                appointmentDate = date,
                serviceDuration = duration,
                description = description,
                selectedServices = selectedServices
            )
            val response = appointmentApiService.updateAppointment(appointmentId, request)
            if (response is ApiResponse.Success) {
                appointmentDao.updateAppointment(
                    appointmentId,
                    date,
                    duration,
                    description,
                    selectedServices,
                    Clock.System.now().toEpochMilliseconds()
                )
                true
            } else if (response is ApiResponse.Error) {
                throw Exception(response.message)
            } else {
                false
            }
        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun removeAppointment(appointmentId: Long): Boolean {
        return try {
            val response = appointmentApiService.deleteAppointment(appointmentId)
            if (response is ApiResponse.Success) {
                appointmentDao.removeAppointmentAndReorder(appointmentId)
                true
            } else if (response is ApiResponse.Error) {
                throw Exception(response.message)
            } else {
                false
            }
        } catch (e: Exception) {
            throw e
        }
    }

    override suspend fun getVisitorHistoryForBusiness(visitorId: Long, businessId: Long): List<AppointmentWithDetails> {
        return try {
            appointmentDao.getVisitorHistoryForBusiness(visitorId, businessId) 
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getTodayStats(businessId: Long, date: Long): DashboardStats {
        return try {
            DashboardStats(
                totalAppointments = appointmentDao.getTodayAppointmentsCount(businessId, date),
                noShowCount = appointmentDao.getTodayNoShowCount(businessId, date),
                cancelledCount = appointmentDao.getTodayCancelledCount(businessId, date)
            )
        } catch (e: Exception) {
            DashboardStats(0, 0, 0)
        }
    }
    override suspend fun getAppointmentById(appointmentId: Long): Appointment? {
        return try {
            appointmentDao.getAppointmentById(appointmentId)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getAllWaitingAppointments(businessId: Long): List<AppointmentWithDetails> {
        return try {
            appointmentDao.getAllWaitingAppointments(businessId) 
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getTodayAppointments(businessId: Long): List<AppointmentWithDetails> {
        return try {
            appointmentDao.getTodayAppointments(businessId) 
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getAllAppointmentsForBusiness(businessId: Long): List<AppointmentWithDetails> {
        return try {
            appointmentDao.getAllAppointmentsForBusiness(businessId) 
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun deleteAppointmentsByVisitorId(visitorId: Long) {
        try {
            appointmentDao.deleteAppointmentsByVisitorId(visitorId)
        } catch (e: Exception) {
            // Log error
        }
    }

    override suspend fun getConflictingAppointments(
        businessId: Long,
        startTime: Long,
        endTime: Long,
        defaultDuration: Int
    ): List<AppointmentWithDetails> {
        return try {
            appointmentDao.getConflictingAppointments(businessId, startTime, endTime, defaultDuration) 
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getAppointmentsForDate(businessId: Long, date: Long): List<AppointmentWithDetails> {
        return try {
            appointmentDao.getAppointmentsForDate(businessId, date) 
        } catch (e: Exception) {
            emptyList()
        }
    }

    @OptIn(ExperimentalTime::class)
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
    ): Boolean {
        return try {
            val response = appointmentApiService.queryAppointments(
                businessId = businessId,
                visitorId = visitorId,
                status = status,
                date = date,
                dateFrom = dateFrom,
                dateTo = dateTo,
                ordering = ordering,
                page = page,
                pageSize = pageSize
            )

            if (response is ApiResponse.Success) {
                val appointmentsDto = response.data.results

                // 1. Sync Visitors
                val visitors = appointmentsDto.map { dto ->
                    Visitor(
                        id = dto.visitor.id,
                        fullName = dto.visitor.fullName,
                        phoneNumber = dto.visitor.phoneNumber,
                        createdAt = dto.visitor.createdAt?.let { DateTimeUtils.parseIsoToEpochMillis(it) }
                            ?: Clock.System.now().toEpochMilliseconds()
                    )
                }
                visitorDao.upsertVisitors(visitors)

                // 2. Sync Appointments
                val appointments = appointmentsDto.map { dto ->
                    Appointment(
                        id = dto.id,
                        businessId = businessId,
                        visitorId = dto.visitor.id,
                        appointmentDate = dto.appointmentDate,
                        serviceDuration = dto.serviceDuration,
                        status = dto.status,
                        description = dto.description,
                        selectedServices = dto.selectedServices,
                        // Without this the inline receipt preview on the queue
                        // card never has anything to show: the API returns it,
                        // but sync dropped it before it reached the DB.
                        paymentReceipt = dto.paymentReceipt,
                    trackingCode = dto.trackingCode,
                    paymentReference = dto.paymentReference,
                    depositPaymentMethod = dto.depositPaymentMethod,
                        createdAt = DateTimeUtils.parseIsoToEpochMillis(dto.createdAt),
                        updatedAt = DateTimeUtils.parseIsoToEpochMillis(dto.updatedAt)
                    )
                }
                val window = if (replaceWindow && visitorId == null && status == null &&
                    response.data.next == null && (response.data.currentPage ?: page) == 1
                ) syncWindow(date, dateFrom, dateTo) else null
                if (window != null) {
                    appointmentDao.replaceWindow(businessId, window.first, window.second, appointments)
                } else {
                    appointmentDao.upsertAppointments(appointments)
                }
                true
            } else {
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        /**
         * Asia/Tehran as a fixed UTC+3:30 (Iran dropped DST in 2022). Not
         * `TimeZone.of("Asia/Tehran")`: on wasm that needs the js-joda tz
         * database, which the web panel does not ship, so it threw and took
         * the whole home screen down.
         */
        private val SERVER_ZONE = FixedOffsetTimeZone(UtcOffset(hours = 3, minutes = 30))

        /**
         * Mirrors the backend's `_utc_day_start` (appointment_query_view.py),
         * which the `date`, `date_from` and `date_to` filters use: it takes
         * the UTC calendar date D of the epoch-ms it is sent, then returns
         * D 00:00 *in the server's TIME_ZONE (Asia/Tehran)*. So `date=X`
         * selects [D 00:00 Tehran, D+1 00:00 Tehran), which is NOT the UTC day
         * and not the Tehran day of X. Must stay in step with the server;
         * the server cannot change while old app versions are in the field.
         */
        @OptIn(ExperimentalTime::class)
        internal fun serverDayWindow(epochMillis: Long): Pair<Long, Long> {
            val d = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.UTC).date
            val from = d.atStartOfDayIn(SERVER_ZONE).toEpochMilliseconds()
            val to = d.plus(1, DateTimeUnit.DAY).atStartOfDayIn(SERVER_ZONE).toEpochMilliseconds()
            return from to to
        }

        /**
         * The [from, to) window the server's date filters select, or null
         * when the request has no date bound ("everything" is not a window
         * we can safely replace).
         */
        internal fun syncWindow(date: Long?, dateFrom: Long?, dateTo: Long?): Pair<Long, Long>? {
            if (date != null) return serverDayWindow(date)
            if (dateFrom == null || dateTo == null) return null
            val from = serverDayWindow(dateFrom).first
            val to = serverDayWindow(dateTo).second
            return if (to > from) from to to else null
        }
    }
}
