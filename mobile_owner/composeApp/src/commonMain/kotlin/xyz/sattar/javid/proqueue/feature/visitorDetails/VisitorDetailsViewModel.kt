package xyz.sattar.javid.proqueue.feature.visitorDetails

import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.ui.components.ToastyType
import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import xyz.sattar.javid.proqueue.core.state.BusinessStateHolder
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.domain.AppointmentRepository
import xyz.sattar.javid.proqueue.domain.MessageRepository
import xyz.sattar.javid.proqueue.domain.VisitorRepository
import xyz.sattar.javid.proqueue.domain.model.message.Message
import xyz.sattar.javid.proqueue.domain.usecase.GenerateReminderMessageUseCase
import xyz.sattar.javid.proqueue.domain.usecase.SendMessageUseCase
import xyz.sattar.javid.proqueue.domain.usecase.SyncAppointmentsUseCase
import xyz.sattar.javid.proqueue.feature.visitorDetails.VisitorDetailsState.PartialState

class VisitorDetailsViewModel(
    private val visitorRepository: VisitorRepository,
    private val appointmentRepository: AppointmentRepository,
    private val messageRepository: MessageRepository,
    private val sendMessageUseCase: SendMessageUseCase,
    private val generateReminderMessageUseCase: GenerateReminderMessageUseCase,
    private val syncAppointmentsUseCase: SyncAppointmentsUseCase
) : BaseViewModel<VisitorDetailsState, PartialState, VisitorDetailsEvent, VisitorDetailsIntent>(
    initialState = VisitorDetailsState()
) {

    /**
     * Server SMS history, keyed by (visitorId, businessId). Held here so the
     * local-only refreshes after send/delete don't drop it from the list.
     * Keyed because a response that lands late for visitor A must never be
     * merged into visitor B's list.
     */
    private val remoteMessages = mutableMapOf<Pair<Long, Long>, List<Message>>()

    /** The (visitorId, businessId) of the most recent load; older loads are stale. */
    private var latestLoad: Pair<Long, Long>? = null

    private suspend fun mergedMessages(visitorId: Long, businessId: Long): List<Message> =
        (messageRepository.getMessagesForVisitorAndBusiness(visitorId, businessId) +
            remoteMessages[visitorId to businessId].orEmpty())
            .sortedByDescending { it.sentAt }

    override fun handleIntent(intent: VisitorDetailsIntent): Flow<PartialState> {
        return when (intent) {
            is VisitorDetailsIntent.LoadVisitorDetails -> loadVisitorDetails(intent.visitorId)
            VisitorDetailsIntent.BackPress -> {
                sendEvent(VisitorDetailsEvent.NavigateBack)
            }
            is VisitorDetailsIntent.OnSendMessage -> flow {
                try {
                    val success = sendMessageUseCase(
                        appointmentId = intent.appointmentId,
                        type = intent.type,
                        content = intent.content,
                        businessTitle = intent.businessTitle
                    )
                    if (!success) emit(PartialState.ShowMessage(Str.couldnt_save_message))

                    val visitor = uiState.value.visitor
                    val business = BusinessStateHolder.selectedBusiness.value
                    if (visitor != null && business != null) {
                        val updatedMessages = mergedMessages(visitor.id, business.id)
                        emit(
                            PartialState.DetailsLoaded(
                                visitor = visitor,
                                appointments = uiState.value.appointments,
                                messages = updatedMessages
                            )
                        )
                    }
                } catch (e: Exception) {
                    emit(PartialState.ShowMessage(e.message ?: Str.couldnt_save_message))
                }
            }
            is VisitorDetailsIntent.DeleteMessage -> flow {
                try {
                    val success = messageRepository.deleteMessage(intent.id)
                    if (!success) {
                        emit(PartialState.ShowMessage(Str.couldnt_delete_message))
                    }
                    val visitor = uiState.value.visitor
                    val business = BusinessStateHolder.selectedBusiness.value
                    if (visitor != null && business != null) {
                        val updatedMessages = mergedMessages(visitor.id, business.id)
                        emit(
                            PartialState.DetailsLoaded(
                                visitor = visitor,
                                appointments = uiState.value.appointments,
                                messages = updatedMessages
                            )
                        )
                    }
                } catch (e: Exception) {
                    emit(PartialState.ShowMessage(e.message ?: Str.couldnt_delete_message_2))
                }
            }
        }
    }

    override fun reduceState(
        currentState: VisitorDetailsState,
        partialState: PartialState
    ): VisitorDetailsState {
        return when (partialState) {
            is PartialState.IsLoading -> currentState.copy(isLoading = partialState.isLoading)
            is PartialState.ShowMessage -> currentState.copy(message = UiMessage(partialState.message, partialState.type), isLoading = false)
            is PartialState.DetailsLoaded -> currentState.copy(
                visitor = partialState.visitor,
                appointments = partialState.appointments,
                messages = partialState.messages,
                isLoading = false
            )
        }
    }

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

    override fun createErrorState(message: String): PartialState = PartialState.ShowMessage(message)

    private fun loadVisitorDetails(visitorId: Long): Flow<PartialState> = flow {
        emit(PartialState.IsLoading(true))
        try {
            val business = BusinessStateHolder.selectedBusiness.value
            if (business == null) {
                emit(PartialState.ShowMessage(Str.no_business_selected_2, ToastyType.Warning))
                return@flow
            }

            val key = visitorId to business.id
            latestLoad = key

            // Sync first: for a visitor who booked online (linked to their own
            // account, not this owner's), the direct visitor fetch below can
            // still 404 on a stale cache, but the nested visitor data riding
            // along with their appointments lets this upsert the local cache
            // before we give up on them.
            syncAppointmentsUseCase(businessId = business.id, visitorId = visitorId)

            // SMS history is a nice-to-have next to the visitor's own data —
            // never let it take the whole screen down with it.
            remoteMessages[key] = try {
                messageRepository.getRemoteMessages(
                    visitorId = visitorId,
                    businessId = business.id,
                    businessTitle = business.title
                )
            } catch (_: Exception) {
                emptyList()
            }

            // A newer load started while this one was waiting on the network:
            // drop this one rather than overwrite the newer visitor's screen.
            if (latestLoad != key) return@flow

            val visitor = visitorRepository.getVisitorById(visitorId)
            if (visitor != null) {
                val appointments = appointmentRepository.getVisitorHistoryForBusiness(visitorId, business.id)
                val messages = mergedMessages(visitorId, business.id)
                if (latestLoad != key) return@flow
                emit(PartialState.DetailsLoaded(visitor, appointments, messages))
            } else {
                emit(PartialState.ShowMessage(Str.information_not_found))
            }
        } catch (e: Exception) {
            emit(PartialState.ShowMessage(e.message ?: Str.couldnt_load_information))
        }
    }
}
