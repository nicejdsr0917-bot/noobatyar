package xyz.sattar.javid.proqueue.feature.lastVisitors

import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.core.state.BusinessStateHolder
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.domain.AppointmentRepository
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentOrdering
import xyz.sattar.javid.proqueue.domain.usecase.GenerateReminderMessageUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GetRangeReportUseCase
import xyz.sattar.javid.proqueue.domain.usecase.MarkAppointmentCompletedUseCase
import xyz.sattar.javid.proqueue.domain.usecase.MarkAppointmentNoShowUseCase
import xyz.sattar.javid.proqueue.domain.usecase.RemoveAppointmentUseCase
import xyz.sattar.javid.proqueue.domain.usecase.SendMessageUseCase
import xyz.sattar.javid.proqueue.domain.usecase.SyncAppointmentsUseCase
import kotlin.time.ExperimentalTime

class LastVisitorsViewModel(
    private val removeAppointmentUseCase: RemoveAppointmentUseCase,
    private val markAppointmentCompletedUseCase: MarkAppointmentCompletedUseCase,
    private val markAppointmentNoShowUseCase: MarkAppointmentNoShowUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val generateReminderMessageUseCase: GenerateReminderMessageUseCase,
    private val syncAppointmentsUseCase: SyncAppointmentsUseCase,
    private val getRangeReportUseCase: GetRangeReportUseCase,
    private val appointmentRepository: AppointmentRepository
) : BaseViewModel<LastVisitorsState, LastVisitorsState.PartialState, LastVisitorsEvent, LastVisitorsIntent>(
    initialState = LastVisitorsState()
) {

    init {
        viewModelScope.launch {
            BusinessStateHolder.selectedBusiness.collectLatest {
                sendIntent(LastVisitorsIntent.LoadAppointments)
            }
        }
    }

    override fun handleIntent(intent: LastVisitorsIntent): Flow<LastVisitorsState.PartialState> {
        return when (intent) {
            LastVisitorsIntent.LoadAppointments -> loadAppointments()
            is LastVisitorsIntent.OnAppointmentOptionsClick -> flow {
                emit(LastVisitorsState.PartialState.ShowOptionsDialog(intent.appointmentId))
            }
            LastVisitorsIntent.OnCreateAppointmentClick -> sendEvent(LastVisitorsEvent.NavigateToCreateAppointment)
            is LastVisitorsIntent.OnEditAppointment -> {
                sendEvent(LastVisitorsEvent.NavigateToEditAppointment(intent.appointmentId))
            }
            is LastVisitorsIntent.OnDeleteAppointment -> deleteAppointment(intent.appointmentId)
            is LastVisitorsIntent.OnUpdateStatus -> updateStatus(intent.appointmentId, intent.status)
            is LastVisitorsIntent.OnMarkCompleted -> markCompleted(intent.appointmentId)
            is LastVisitorsIntent.OnMarkNoShow -> markNoShow(intent.appointmentId)
            LastVisitorsIntent.DismissDialog -> flow {
                emit(LastVisitorsState.PartialState.ShowOptionsDialog(null))
            }
            is LastVisitorsIntent.OnTabSelected -> flow {
                emit(LastVisitorsState.PartialState.TabSelected(intent.index))
                // Load the report the first time its tab is opened, not on
                // every switch back to it — it's a server round trip, and the
                // window it describes doesn't change on its own.
                if (intent.index == RANGE_REPORT_TAB && uiState.value.rangeReport == null) {
                    val (from, to) = defaultRangeIfUnset(uiState.value.rangeFrom, uiState.value.rangeTo)
                    emit(LastVisitorsState.PartialState.RangeSelected(from, to))
                    emitAll(loadRangeReport(from, to))
                }
            }
            is LastVisitorsIntent.OnAppointmentClick -> {
                sendEvent(LastVisitorsEvent.NavigateToVisitorDetails(intent.visitorId))
            }
            is LastVisitorsIntent.OnFilterChanged -> flow {
                emit(LastVisitorsState.PartialState.UpdateFilter(intent.filter))
                emit(LastVisitorsState.PartialState.ShowFilterSheet(false))
                emitAll(loadAppointments(intent.filter))
            }
            is LastVisitorsIntent.ShowFilterSheet -> flow {
                emit(LastVisitorsState.PartialState.ShowFilterSheet(intent.show))
            }
            LastVisitorsIntent.ClearFilter -> flow {
                val defaultFilter = AppointmentFilter()
                emit(LastVisitorsState.PartialState.UpdateFilter(defaultFilter))
                emitAll(loadAppointments(defaultFilter))
            }
            is LastVisitorsIntent.OnSendMessage -> flow {
                try {
                    val success = sendMessageUseCase(
                        appointmentId = intent.appointmentId,
                        type = intent.type,
                        content = intent.content,
                        businessTitle = intent.businessTitle
                    )
                    // Previously this had no success path at all — sending a
                    // reminder either failed loudly or succeeded completely
                    // silently, with nothing to tell them it actually went out.
                    emit(
                        if (success) LastVisitorsState.PartialState.ShowMessage(UiMessage.success("پیام ارسال شد"))
                        else LastVisitorsState.PartialState.ShowMessage(UiMessage.error("خطا در ثبت پیام"))
                    )
                } catch (e: Exception) {
                    emit(LastVisitorsState.PartialState.ShowMessage(UiMessage.error(e.message ?: "خطا در ثبت پیام")))
                }
            }
            LastVisitorsIntent.ClearMessage -> flow { emit(LastVisitorsState.PartialState.ClearMessage) }
            is LastVisitorsIntent.OnRangeChanged -> flow {
                val (from, to) = clampRange(intent.from, intent.to)
                emit(LastVisitorsState.PartialState.RangeSelected(from, to))
                emitAll(loadRangeReport(from, to))
            }
            is LastVisitorsIntent.OnRangeSubTabSelected -> flow {
                emit(LastVisitorsState.PartialState.RangeSubTabSelected(intent.index))
            }
            LastVisitorsIntent.LoadRangeReport -> flow {
                val state = uiState.value
                val (from, to) = defaultRangeIfUnset(state.rangeFrom, state.rangeTo)
                emit(LastVisitorsState.PartialState.RangeSelected(from, to))
                emitAll(loadRangeReport(from, to))
            }
            LastVisitorsIntent.LoadMoreRangeCustomers -> loadMoreRangeCustomers()
        }
    }

    /**
     * Keeps the requested window inside the server's one-month cap by pulling
     * the *start* forward, so the recent end — the part the owner is looking
     * at — is what survives. Matches the server's own clamp direction
     * (backend/appointment/views/range_visitors_view.py), so a range that
     * passes here is never narrowed again on arrival.
     */
    private fun clampRange(from: Long, to: Long): Pair<Long, Long> {
        val end = maxOf(from, to)
        val start = minOf(from, to)
        val earliest = end - GetRangeReportUseCase.MAX_RANGE_MILLIS
        return maxOf(start, earliest) to end
    }

    private fun defaultRangeIfUnset(from: Long, to: Long): Pair<Long, Long> {
        if (from != 0L && to != 0L) return from to to
        val today = DateTimeUtils.startOfTodayMillis()
        return (today - 29L * GetRangeReportUseCase.DAY_MILLIS) to today
    }

    private fun loadRangeReport(from: Long, to: Long): Flow<LastVisitorsState.PartialState> = flow {
        emit(LastVisitorsState.PartialState.RangeLoading(true))
        val business = BusinessStateHolder.selectedBusiness.value
        if (business == null) {
            emit(LastVisitorsState.PartialState.RangeLoading(false))
            return@flow
        }
        try {
            val report = getRangeReportUseCase(
                businessId = business.id,
                dateFrom = from,
                dateTo = to
            )
            if (report == null) {
                emit(LastVisitorsState.PartialState.RangeFailed)
                return@flow
            }

            // The appointment rows come through the same sync-then-read path
            // the other tabs use, over the window the server actually
            // measured — so a clamped range doesn't list appointments from
            // days the summary above them never counted.
            syncAppointmentsUseCase(
                businessId = business.id,
                status = null,
                date = null,
                dateFrom = report.dateFrom,
                dateTo = report.dateTo,
                ordering = AppointmentOrdering.DATE_DESC
            )
            val appointments = appointmentRepository
                .getAllAppointmentsForBusiness(business.id)
                .filter {
                    it.appointment.appointmentDate >= report.dateFrom &&
                        it.appointment.appointmentDate <= report.dateTo + GetRangeReportUseCase.DAY_MILLIS
                }
                .sortedByDescending { it.appointment.appointmentDate }

            emit(LastVisitorsState.PartialState.RangeLoaded(report, appointments))
        } catch (e: Exception) {
            emit(LastVisitorsState.PartialState.RangeFailed)
        }
    }

    private fun loadMoreRangeCustomers(): Flow<LastVisitorsState.PartialState> = flow {
        val state = uiState.value
        val current = state.rangeReport ?: return@flow
        if (!current.hasMoreCustomers || state.rangeLoading) return@flow
        val business = BusinessStateHolder.selectedBusiness.value ?: return@flow

        emit(LastVisitorsState.PartialState.RangeLoading(true))
        try {
            val next = getRangeReportUseCase(
                businessId = business.id,
                dateFrom = current.dateFrom,
                dateTo = current.dateTo,
                page = current.currentPage + 1
            )
            if (next == null) {
                emit(LastVisitorsState.PartialState.RangeLoading(false))
                return@flow
            }
            emit(LastVisitorsState.PartialState.RangeCustomersAppended(next))
        } catch (e: Exception) {
            emit(LastVisitorsState.PartialState.RangeLoading(false))
        }
    }

    override fun reduceState(
        currentState: LastVisitorsState,
        partialState: LastVisitorsState.PartialState
    ): LastVisitorsState {
        return when (partialState) {
            is LastVisitorsState.PartialState.IsLoading ->
                currentState.copy(isLoading = partialState.isLoading)
            is LastVisitorsState.PartialState.ShowMessage ->
                currentState.copy(message = partialState.message, isLoading = false)
            is LastVisitorsState.PartialState.LoadAppointments ->
                currentState.copy(
                    appointments = partialState.appointments,
                    totalCount = partialState.totalCount,
                    isLoading = false
                )
            is LastVisitorsState.PartialState.ShowOptionsDialog ->
                currentState.copy(
                    selectedAppointmentId = partialState.appointmentId,
                    showOptionsDialog = partialState.appointmentId != null
                )
            is LastVisitorsState.PartialState.TabSelected ->
                currentState.copy(selectedTab = partialState.index)
            is LastVisitorsState.PartialState.UpdateFilter ->
                currentState.copy(filter = partialState.filter)
            is LastVisitorsState.PartialState.ShowFilterSheet ->
                currentState.copy(showFilterSheet = partialState.show)
            LastVisitorsState.PartialState.ClearMessage ->
                currentState.copy(message = null)
            is LastVisitorsState.PartialState.RangeSelected ->
                currentState.copy(rangeFrom = partialState.from, rangeTo = partialState.to)
            is LastVisitorsState.PartialState.RangeSubTabSelected ->
                currentState.copy(rangeSubTab = partialState.index)
            is LastVisitorsState.PartialState.RangeLoading ->
                currentState.copy(rangeLoading = partialState.isLoading)
            is LastVisitorsState.PartialState.RangeLoaded ->
                currentState.copy(
                    rangeReport = partialState.report,
                    rangeAppointments = partialState.appointments,
                    // Snap the picker onto what the server measured, so a
                    // clamped range can't leave the header showing dates the
                    // numbers below it never covered.
                    rangeFrom = partialState.report.dateFrom,
                    rangeTo = partialState.report.dateTo,
                    rangeLoading = false,
                    rangeFailed = false
                )
            LastVisitorsState.PartialState.RangeFailed ->
                currentState.copy(rangeLoading = false, rangeFailed = true)
            is LastVisitorsState.PartialState.RangeCustomersAppended ->
                currentState.copy(
                    rangeReport = partialState.report.copy(
                        customers = (currentState.rangeReport?.customers ?: emptyList()) +
                            partialState.report.customers
                    ),
                    rangeLoading = false
                )
        }
    }

    override fun createErrorState(message: String): LastVisitorsState.PartialState =
        LastVisitorsState.PartialState.ShowMessage(UiMessage.error(message))

    fun generateReminderMessage(
        businessId: Long,
        businessTitle: String,
        businessAddress: String,
        visitorName: String,
        appointmentMillis: Long,
        reminderMinutes: String,
        serviceDuration: Int?
    ): String {
        return generateReminderMessageUseCase(
            businessId = businessId,
            businessTitle = businessTitle,
            businessAddress = businessAddress,
            visitorName = visitorName,
            appointmentMillis = appointmentMillis,
            reminderMinutes = reminderMinutes,
            serviceDuration = serviceDuration
        )
    }

    @OptIn(ExperimentalTime::class)
    private fun loadAppointments(filter: AppointmentFilter = uiState.value.filter): Flow<LastVisitorsState.PartialState> = flow {
        emit(LastVisitorsState.PartialState.IsLoading(true))
        try {
            val business = BusinessStateHolder.selectedBusiness.value
            if (business != null) {
                // این تب باید فقط ۷ روز آینده را نشان دهد. وقتی فیلتر کاربر نه
                // یک روز خاص (filter.date) و نه بازه‌ی تاریخ صریحی (مثلاً از
                // نمودار روند) مشخص کرده، پیش‌فرض را به [امروز, امروز+۷روز]
                // محدود می‌کنیم — قبلاً بدون این پیش‌فرض همه‌ی نوبت‌های
                // گذشته/آینده بارگذاری می‌شدند.
                val effectiveDateFrom: Long?
                val effectiveDateTo: Long?
                if (filter.date != null) {
                    effectiveDateFrom = filter.dateFrom
                    effectiveDateTo = filter.dateTo
                } else {
                    effectiveDateFrom = filter.dateFrom ?: DateTimeUtils.startOfTodayMillis()
                    effectiveDateTo = filter.dateTo ?: (DateTimeUtils.startOfTodayMillis() + 7L * 24 * 60 * 60 * 1000L)
                }

                // Sync with server using filters
                syncAppointmentsUseCase(
                    businessId = business.id,
                    status = filter.status,
                    date = filter.date,
                    dateFrom = effectiveDateFrom,
                    dateTo = effectiveDateTo,
                    ordering = filter.ordering
                )

                // Load from DB. getAllAppointmentsForBusiness always returns rows
                // ordered by appointmentDate DESC (its DAO query is hardcoded), so
                // date-range and ordering can't be left to it — both are applied
                // here, on top of the status/date filters that already worked.
                val appointments = appointmentRepository.getAllAppointmentsForBusiness(business.id)

                val filtered = appointments.filter { app ->
                    (filter.status == null || app.appointment.status == filter.status) &&
                    (filter.date == null || DateTimeUtils.isSameDay(app.appointment.appointmentDate, filter.date)) &&
                    (effectiveDateFrom == null || app.appointment.appointmentDate >= effectiveDateFrom) &&
                    (effectiveDateTo == null || app.appointment.appointmentDate <= effectiveDateTo)
                }

                val ordered = when (filter.ordering) {
                    AppointmentOrdering.DATE_ASC -> filtered.sortedBy { it.appointment.appointmentDate }
                    AppointmentOrdering.DATE_DESC -> filtered.sortedByDescending { it.appointment.appointmentDate }
                    AppointmentOrdering.CREATED_AT_ASC -> filtered.sortedBy { it.appointment.createdAt }
                    AppointmentOrdering.CREATED_AT_DESC -> filtered.sortedByDescending { it.appointment.createdAt }
                }

                emit(
                    LastVisitorsState.PartialState.LoadAppointments(
                        appointments = ordered,
                        totalCount = ordered.size
                    )
                )
            } else {
                emit(LastVisitorsState.PartialState.ShowMessage(UiMessage.error("لطفاً ابتدا یک کسب‌وکار انتخاب کنید")))
            }
        } catch (e: Exception) {
            emit(LastVisitorsState.PartialState.ShowMessage(UiMessage.error(e.message ?: "خطا در بارگذاری نوبت‌ها")))
        } finally {
            emit(LastVisitorsState.PartialState.IsLoading(false))
        }
    }

    private fun deleteAppointment(appointmentId: Long): Flow<LastVisitorsState.PartialState> = flow {
        try {
            val success = removeAppointmentUseCase(appointmentId)
            if (success) {
                emit(LastVisitorsState.PartialState.ShowOptionsDialog(null))
                emit(LastVisitorsState.PartialState.ShowMessage(UiMessage.success("نوبت حذف شد")))
                emitAll(loadAppointments())
            } else {
                emit(LastVisitorsState.PartialState.ShowMessage(UiMessage.error("خطا در حذف نوبت")))
            }
        } catch (e: Exception) {
            emit(LastVisitorsState.PartialState.ShowMessage(UiMessage.error(e.message ?: "خطا در حذف نوبت")))
        }
    }

    private fun markCompleted(appointmentId: Long): Flow<LastVisitorsState.PartialState> = flow {
        try {
            val success = markAppointmentCompletedUseCase(appointmentId)
            if (success) {
                emit(LastVisitorsState.PartialState.ShowMessage(UiMessage.success("نوبت تکمیل شد")))
                emitAll(loadAppointments())
            } else {
                emit(LastVisitorsState.PartialState.ShowMessage(UiMessage.error("خطا در تکمیل نوبت")))
            }
        } catch (e: Exception) {
            emit(LastVisitorsState.PartialState.ShowMessage(UiMessage.error(e.message ?: "خطا در تکمیل نوبت")))
        }
    }

    private fun markNoShow(appointmentId: Long): Flow<LastVisitorsState.PartialState> = flow {
        try {
            val success = markAppointmentNoShowUseCase(appointmentId)
            if (success) {
                emit(LastVisitorsState.PartialState.ShowMessage(UiMessage.success("عدم حضور ثبت شد")))
                emitAll(loadAppointments())
            } else {
                emit(LastVisitorsState.PartialState.ShowMessage(UiMessage.error("خطا در ثبت عدم مراجعه")))
            }
        } catch (e: Exception) {
            emit(LastVisitorsState.PartialState.ShowMessage(UiMessage.error(e.message ?: "خطا در ثبت عدم مراجعه")))
        }
    }

    private fun updateStatus(appointmentId: Long, status: String): Flow<LastVisitorsState.PartialState> = flow {
        try {
            val success = appointmentRepository.updateAppointmentStatus(appointmentId, status)
            if (success) {
                val confirmation = if (status == "CANCELLED") "درخواست رد شد" else "درخواست تأیید شد"
                emit(LastVisitorsState.PartialState.ShowMessage(UiMessage.success(confirmation)))
                emitAll(loadAppointments())
            } else {
                emit(LastVisitorsState.PartialState.ShowMessage(UiMessage.error("خطا در تغییر وضعیت نوبت")))
            }
        } catch (e: Exception) {
            emit(LastVisitorsState.PartialState.ShowMessage(UiMessage.error(e.message ?: "خطا در تغییر وضعیت نوبت")))
        }
    }
}
