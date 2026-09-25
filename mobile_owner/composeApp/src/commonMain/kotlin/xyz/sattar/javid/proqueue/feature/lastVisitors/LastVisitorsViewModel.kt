package xyz.sattar.javid.proqueue.feature.lastVisitors

import xyz.sattar.javid.proqueue.domain.model.CustomerOrdering
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
    initialState = LastVisitorsState(filter = AppointmentFilter().withResolvedDates())
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
            LastVisitorsIntent.LoadAppointments -> flow {
                emitAll(loadAppointments())
                // The refresh button and a business switch reload whatever
                // is on screen, report included.
                if (uiState.value.selectedTab == RANGE_REPORT_TAB) {
                    emitAll(loadReportIfStale(uiState.value.filter, force = true))
                }
            }
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
                // The report is a server round trip, so it is fetched when its
                // tab is opened and only if the shared window moved since the
                // last fetch — not on every switch back to it.
                if (intent.index == RANGE_REPORT_TAB) {
                    emitAll(loadReportIfStale(uiState.value.filter))
                }
            }
            is LastVisitorsIntent.OnAppointmentClick -> {
                sendEvent(LastVisitorsEvent.NavigateToVisitorDetails(intent.visitorId))
            }
            is LastVisitorsIntent.OnFilterChanged -> applyFilter(intent.filter)
            is LastVisitorsIntent.OnDateRangeChanged -> applyFilter(
                uiState.value.filter.copy(date = null, dateFrom = intent.from, dateTo = intent.to)
            )
            is LastVisitorsIntent.ShowFilterSheet -> flow {
                emit(LastVisitorsState.PartialState.ShowFilterSheet(intent.show))
            }
            // Clears what the filter sheet sets — status and ordering. The
            // date window belongs to the date bar and stays where it is.
            LastVisitorsIntent.ClearFilter -> applyFilter(
                uiState.value.filter.copy(status = null, ordering = AppointmentFilter().ordering)
            )
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
            LastVisitorsIntent.LoadMoreRangeCustomers -> loadMoreRangeCustomers()
            is LastVisitorsIntent.OnCustomerOrderingChanged -> flow {
                if (intent.ordering == uiState.value.customerOrdering) return@flow
                emit(LastVisitorsState.PartialState.CustomerOrderingChanged(intent.ordering))
                // Same window, new order: the pages already loaded are in the
                // old order, so start again from page one. The new order is
                // passed in rather than read back from uiState, which hasn't
                // necessarily been reduced yet at this point.
                emitAll(loadReportIfStale(uiState.value.filter, force = true, ordering = intent.ordering))
            }
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

    /**
     * One filter change, applied to every tab: the lists reload, and the
     * report too if it is the tab on screen (otherwise it catches up when
     * opened — see [loadReportIfStale]).
     */
    private fun applyFilter(filter: AppointmentFilter): Flow<LastVisitorsState.PartialState> = flow {
        val resolved = filter.withResolvedDates()
        emit(LastVisitorsState.PartialState.UpdateFilter(resolved))
        emit(LastVisitorsState.PartialState.ShowFilterSheet(false))
        emitAll(loadAppointments(resolved))
        if (uiState.value.selectedTab == RANGE_REPORT_TAB) {
            emitAll(loadReportIfStale(resolved))
        }
    }

    /**
     * The report window for the shared filter: the same days, expressed the
     * way the report endpoint takes them (start of first day, start of last
     * day), then clamped to the report's cap.
     */
    private fun reportWindow(filter: AppointmentFilter): Pair<Long, Long> {
        val f = filter.withResolvedDates()
        val from = f.dateFrom!!
        val lastDayStart = from + ((f.dateTo!! - from) / GetRangeReportUseCase.DAY_MILLIS) *
            GetRangeReportUseCase.DAY_MILLIS
        return clampRange(from, lastDayStart)
    }

    private fun loadReportIfStale(
        filter: AppointmentFilter,
        force: Boolean = false,
        ordering: CustomerOrdering = uiState.value.customerOrdering
    ): Flow<LastVisitorsState.PartialState> = flow {
        val window = reportWindow(filter)
        val state = uiState.value
        val stale = state.rangeReport == null || state.rangeFailed ||
            window != (state.rangeFrom to state.rangeTo)
        if (!force && !stale) return@flow
        emit(LastVisitorsState.PartialState.RangeSelected(window.first, window.second))
        emitAll(loadRangeReport(window.first, window.second, ordering))
    }

    private fun loadRangeReport(
        from: Long,
        to: Long,
        ordering: CustomerOrdering
    ): Flow<LastVisitorsState.PartialState> = flow {
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
                dateTo = to,
                ordering = ordering
            )
            if (report == null) {
                emit(LastVisitorsState.PartialState.RangeFailed)
                return@flow
            }
            emit(LastVisitorsState.PartialState.RangeLoaded(report))
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
                ordering = uiState.value.customerOrdering,
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
            is LastVisitorsState.PartialState.RangeLoading ->
                currentState.copy(rangeLoading = partialState.isLoading)
            is LastVisitorsState.PartialState.RangeLoaded ->
                // rangeFrom/rangeTo stay as requested, not snapped to what the
                // server measured: they are the staleness key for
                // loadReportIfStale, and the shared date bar — not the report —
                // owns the dates on screen. A clamp is surfaced by the note
                // RangeReportSection shows off report.clamped.
                currentState.copy(
                    rangeReport = partialState.report,
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
            is LastVisitorsState.PartialState.CustomerOrderingChanged ->
                currentState.copy(customerOrdering = partialState.ordering)
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
                // The window comes from the shared date bar; withResolvedDates
                // is a no-op for any filter that went through applyFilter, and
                // fills in the default (next 7 days) for any that didn't.
                val resolved = filter.withResolvedDates()
                val effectiveDateFrom = resolved.dateFrom
                val effectiveDateTo = resolved.dateTo

                // Sync with server using filters
                syncAppointmentsUseCase(
                    businessId = business.id,
                    status = filter.status,
                    date = null,
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
                emit(LastVisitorsState.PartialState.ShowMessage(UiMessage.warning("لطفاً ابتدا یک کسب‌وکار انتخاب کنید")))
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

/**
 * Fills in whichever end of the date window is missing, so every filter the
 * screen holds names both days. A single [AppointmentFilter.date] becomes that
 * whole day; no dates at all becomes the default window, the next seven days —
 * what an owner opening the screen cold is looking for.
 */
internal fun AppointmentFilter.withResolvedDates(): AppointmentFilter {
    date?.let { day ->
        val start = DateTimeUtils.startOfDayMillis(day)
        return copy(date = null, dateFrom = start, dateTo = DateRanges.endOfDay(start))
    }
    return when {
        dateFrom != null && dateTo != null -> this
        dateFrom != null -> copy(dateTo = DateRanges.endOfDay(DateTimeUtils.startOfDayMillis(dateFrom)))
        dateTo != null -> copy(dateFrom = DateTimeUtils.startOfDayMillis(dateTo))
        else -> {
            val (from, to) = DateRanges.nextWeek()
            copy(dateFrom = from, dateTo = to)
        }
    }
}
