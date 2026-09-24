package xyz.sattar.javid.proqueue.feature.promo

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.state.BusinessStateHolder
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.PromoCampaignDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.PromoPreviewDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.PromoSmsOverviewDto
import xyz.sattar.javid.proqueue.domain.usecase.PromoSmsUseCase

data class PromoCampaignState(
    val isLoading: Boolean = true,
    val overview: PromoSmsOverviewDto? = null,
    val audience: String = "all",
    val message: String = "",
    /** Fetching the exact cost for the confirm dialog. */
    val isPreviewing: Boolean = false,
    /** Non-null while the confirm dialog is open. */
    val confirm: PromoPreviewDto? = null,
    val isSending: Boolean = false,
    /** The history entry opened in the bottom sheet, with recipients. */
    val openedCampaign: PromoCampaignDto? = null,
    val toast: UiMessage? = null,
) {
    val businessTitle: String get() = BusinessStateHolder.selectedBusiness.value?.title.orEmpty()

    sealed interface Partial {
        data class Loading(val on: Boolean) : Partial
        data class Overview(val data: PromoSmsOverviewDto) : Partial
        data class Audience(val key: String) : Partial
        data class Message(val text: String) : Partial
        data class Previewing(val on: Boolean) : Partial
        data class Confirm(val preview: PromoPreviewDto?) : Partial
        data class Sending(val on: Boolean) : Partial
        data class Opened(val campaign: PromoCampaignDto?) : Partial
        data class Toast(val msg: UiMessage?) : Partial
    }
}

sealed interface PromoCampaignIntent {
    data object Load : PromoCampaignIntent
    data class SelectAudience(val key: String) : PromoCampaignIntent
    data class UpdateMessage(val text: String) : PromoCampaignIntent
    data object InsertName : PromoCampaignIntent
    data class ApplyReadyText(val text: String) : PromoCampaignIntent
    data object RequestSend : PromoCampaignIntent
    data object ConfirmSend : PromoCampaignIntent
    data object DismissConfirm : PromoCampaignIntent
    data class OpenCampaign(val id: Long) : PromoCampaignIntent
    data object CloseCampaign : PromoCampaignIntent
    data object GetCredit : PromoCampaignIntent
    data object ToastShown : PromoCampaignIntent
}

sealed interface PromoCampaignEvent {
    /** Plans and SMS packs both live on the add-ons screen. */
    data object NavigateToAddons : PromoCampaignEvent
}

class PromoCampaignViewModel(
    private val promoSms: PromoSmsUseCase
) : BaseViewModel<PromoCampaignState, PromoCampaignState.Partial, PromoCampaignEvent, PromoCampaignIntent>(
    initialState = PromoCampaignState()
) {
    private var pollJob: Job? = null

    override fun handleIntent(intent: PromoCampaignIntent): Flow<PromoCampaignState.Partial> = when (intent) {
        PromoCampaignIntent.Load -> load()
        is PromoCampaignIntent.SelectAudience -> single(PromoCampaignState.Partial.Audience(intent.key))
        is PromoCampaignIntent.UpdateMessage -> single(PromoCampaignState.Partial.Message(clamp(intent.text)))
        PromoCampaignIntent.InsertName -> single(PromoCampaignState.Partial.Message(clamp(uiState.value.message + NAME_TOKEN)))
        is PromoCampaignIntent.ApplyReadyText -> single(PromoCampaignState.Partial.Message(clamp(intent.text)))
        PromoCampaignIntent.RequestSend -> requestSend()
        PromoCampaignIntent.ConfirmSend -> send()
        PromoCampaignIntent.DismissConfirm -> single(PromoCampaignState.Partial.Confirm(null))
        is PromoCampaignIntent.OpenCampaign -> openCampaign(intent.id)
        PromoCampaignIntent.CloseCampaign -> single(PromoCampaignState.Partial.Opened(null))
        PromoCampaignIntent.GetCredit -> flow {
            emit(PromoCampaignState.Partial.Confirm(null))
            emitAll(sendEvent(PromoCampaignEvent.NavigateToAddons))
        }
        PromoCampaignIntent.ToastShown -> single(PromoCampaignState.Partial.Toast(null))
    }

    private fun single(p: PromoCampaignState.Partial) = flow { emit(p) }

    private fun clamp(text: String): String {
        val max = uiState.value.overview?.maxMessageChars ?: 250
        return if (text.length > max) text.take(max) else text
    }

    private fun businessId(): Long? = BusinessStateHolder.selectedBusiness.value?.id

    private fun load(): Flow<PromoCampaignState.Partial> = flow {
        val id = businessId() ?: run {
            emit(PromoCampaignState.Partial.Toast(UiMessage.error("کسب‌وکار انتخاب نشده")))
            return@flow
        }
        when (val r = promoSms.overview(id)) {
            is ApiResponse.Success -> {
                emit(PromoCampaignState.Partial.Overview(r.data))
                if (uiState.value.message.isBlank()) {
                    r.data.readyTexts.firstOrNull()?.let { emit(PromoCampaignState.Partial.Message(it)) }
                }
                pollWhileSending(r.data)
            }
            is ApiResponse.Error -> emit(PromoCampaignState.Partial.Toast(UiMessage.error(r.message)))
        }
        emit(PromoCampaignState.Partial.Loading(false))
    }

    /** Refresh the history every few seconds while a campaign is still going out. */
    private fun pollWhileSending(data: PromoSmsOverviewDto) {
        val sending = data.campaigns.any { it.status == "SENDING" }
        if (!sending) {
            pollJob?.cancel()
            return
        }
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            delay(4000)
            pollJob = null
            sendIntent(PromoCampaignIntent.Load)
        }
    }

    private fun requestSend(): Flow<PromoCampaignState.Partial> = flow {
        val id = businessId() ?: return@flow
        val state = uiState.value
        if (state.message.isBlank()) {
            emit(PromoCampaignState.Partial.Toast(UiMessage.error("متن پیامک را بنویسید.")))
            return@flow
        }
        emit(PromoCampaignState.Partial.Previewing(true))
        when (val r = promoSms.preview(id, state.audience, state.message)) {
            is ApiResponse.Success -> {
                if (r.data.recipientCount == 0) {
                    emit(PromoCampaignState.Partial.Toast(UiMessage.error("در این گروه مشتری‌ای با شماره‌ی معتبر نیست.")))
                } else {
                    emit(PromoCampaignState.Partial.Confirm(r.data))
                }
            }
            is ApiResponse.Error -> emit(PromoCampaignState.Partial.Toast(UiMessage.error(r.message)))
        }
        emit(PromoCampaignState.Partial.Previewing(false))
    }

    private fun send(): Flow<PromoCampaignState.Partial> = flow {
        val id = businessId() ?: return@flow
        val state = uiState.value
        emit(PromoCampaignState.Partial.Sending(true))
        when (val r = promoSms.send(id, state.audience, state.message)) {
            is ApiResponse.Success -> {
                emit(PromoCampaignState.Partial.Confirm(null))
                emit(
                    PromoCampaignState.Partial.Toast(
                        UiMessage.success("کمپین ثبت شد و ارسال برای ${r.data.recipientCount} نفر آغاز شد.")
                    )
                )
                emitAll(load())
            }
            is ApiResponse.Error -> {
                emit(PromoCampaignState.Partial.Confirm(null))
                emit(PromoCampaignState.Partial.Toast(UiMessage.error(r.message)))
            }
        }
        emit(PromoCampaignState.Partial.Sending(false))
    }

    private fun openCampaign(campaignId: Long): Flow<PromoCampaignState.Partial> = flow {
        val id = businessId() ?: return@flow
        when (val r = promoSms.campaign(id, campaignId)) {
            is ApiResponse.Success -> emit(PromoCampaignState.Partial.Opened(r.data))
            is ApiResponse.Error -> emit(PromoCampaignState.Partial.Toast(UiMessage.error(r.message)))
        }
    }

    override fun reduceState(
        currentState: PromoCampaignState,
        partialState: PromoCampaignState.Partial
    ): PromoCampaignState = when (partialState) {
        is PromoCampaignState.Partial.Loading -> currentState.copy(isLoading = partialState.on)
        is PromoCampaignState.Partial.Overview -> currentState.copy(overview = partialState.data)
        is PromoCampaignState.Partial.Audience -> currentState.copy(audience = partialState.key)
        is PromoCampaignState.Partial.Message -> currentState.copy(message = partialState.text)
        is PromoCampaignState.Partial.Previewing -> currentState.copy(isPreviewing = partialState.on)
        is PromoCampaignState.Partial.Confirm -> currentState.copy(confirm = partialState.preview)
        is PromoCampaignState.Partial.Sending -> currentState.copy(isSending = partialState.on)
        is PromoCampaignState.Partial.Opened -> currentState.copy(openedCampaign = partialState.campaign)
        is PromoCampaignState.Partial.Toast -> currentState.copy(toast = partialState.msg)
    }

    override fun createErrorState(message: String): PromoCampaignState.Partial =
        PromoCampaignState.Partial.Toast(UiMessage.error(message))

    companion object {
        const val NAME_TOKEN = "{name}"
        const val BUSINESS_TOKEN = "{business}"
    }
}
