package xyz.sattar.javid.proqueue.feature.messages

import xyz.sattar.javid.proqueue.core.i18n.Str
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import xyz.sattar.javid.proqueue.core.network.ApiException
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.prefs.PreferencesManager
import xyz.sattar.javid.proqueue.core.state.BusinessStateHolder
import xyz.sattar.javid.proqueue.core.ui.BaseViewModel
import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.EntitlementKeys
import xyz.sattar.javid.proqueue.domain.model.business.DEFAULT_REMINDER_MINUTES
import xyz.sattar.javid.proqueue.domain.model.business.ReminderDelivery
import xyz.sattar.javid.proqueue.domain.model.business.ReminderTemplates
import xyz.sattar.javid.proqueue.domain.usecase.BusinessUpsertUseCase
import xyz.sattar.javid.proqueue.domain.usecase.GenerateReminderMessageUseCase
import xyz.sattar.javid.proqueue.domain.usecase.user.GetMyEntitlementsUseCase

class MessagesViewModel(
    private val generateReminderMessageUseCase: GenerateReminderMessageUseCase,
    private val getMyEntitlementsUseCase: GetMyEntitlementsUseCase,
    private val businessUpsertUseCase: BusinessUpsertUseCase
) :
    BaseViewModel<MessagesState, MessagesState.PartialState, MessagesEvent, MessagesIntent>(
        initialState = MessagesState()
    ) {
    override fun handleIntent(intent: MessagesIntent): Flow<MessagesState.PartialState> {
        return when (intent) {
            MessagesIntent.Load -> load()
            is MessagesIntent.UpdateTemplate -> updateTemplate(intent.text)
            is MessagesIntent.InsertToken -> insertToken(intent.token)
            is MessagesIntent.SetReminder -> setReminder(intent.minutes)
            is MessagesIntent.ApplyReadyTemplate -> updateTemplate(intent.text)
            is MessagesIntent.SetDelivery -> setDelivery(intent.delivery)
            is MessagesIntent.SelectPanelTemplate -> flow {
                emit(MessagesState.PartialState.SetPanelTemplate(ReminderTemplates.byKey(intent.key).key))
            }
            MessagesIntent.UpgradeForPanelDelivery -> sendEvent(MessagesEvent.NavigateToAddons)
            MessagesIntent.OpenSmsReport -> sendEvent(MessagesEvent.NavigateToSmsReport)
            MessagesIntent.Save -> save()
        }
    }

    private fun load(): Flow<MessagesState.PartialState> = flow {
        val business = BusinessStateHolder.selectedBusiness.value
        if (business == null) {
            emit(MessagesState.PartialState.ShowMessage(UiMessage.warning(Str.no_business_selected)))
            return@flow
        }
        emit(MessagesState.PartialState.IsLoading(true))
        val manualTemplate = PreferencesManager.messageTemplate(business.id).first()
            ?: defaultTemplates().first()
        // The server's value, not the local preference: it is what actually
        // times the panel SMS and the owner's push. The preference used to be
        // the only thing this screen read or wrote, so a lead time picked here
        // never reached the server at all.
        val leadMinutes = business.notificationMinutesBefore.takeIf { it > 0 } ?: DEFAULT_REMINDER_MINUTES
        emit(
            MessagesState.PartialState.ApplyBusiness(
                business.id, business.title, notificationsOff = !business.notificationEnabled
            )
        )
        emit(MessagesState.PartialState.SetReminder(leadMinutes))
        emit(MessagesState.PartialState.SetDelivery(business.reminderDelivery))
        emit(MessagesState.PartialState.SetPanelTemplate(ReminderTemplates.byKey(business.reminderTemplate).key))
        emit(MessagesState.PartialState.ApplyTemplate(manualTemplate))
        emit(MessagesState.PartialState.ApplyPreview(render(manualTemplate, leadMinutes)))
        emit(MessagesState.PartialState.SetPanelPreviews(panelPreviews(leadMinutes)))
        emit(MessagesState.PartialState.LoadReadyTemplates(defaultTemplates()))
        emit(MessagesState.PartialState.SetPanelAllowed(loadPanelEntitlement()))
        emit(MessagesState.PartialState.IsLoading(false))
    }

    /**
     * Whether the plan covers server-side reminders. A failed entitlements call
     * leaves PANEL locked rather than optimistically unlocked — offering it and
     * then eating a 403 on save is the worse of the two failures.
     */
    private suspend fun loadPanelEntitlement(): Boolean = try {
        when (val response = getMyEntitlementsUseCase()) {
            is ApiResponse.Success -> response.data.hasFeature(EntitlementKeys.AUTO_REMINDER_SMS)
            is ApiResponse.Error -> false
        }
    } catch (e: Exception) {
        false
    }

    private fun updateTemplate(text: String): Flow<MessagesState.PartialState> = flow {
        emit(MessagesState.PartialState.ApplyTemplate(text))
        emit(MessagesState.PartialState.ApplyPreview(render(text)))
    }

    private fun insertToken(token: String): Flow<MessagesState.PartialState> =
        updateTemplate(uiState.value.template + token)

    private fun setReminder(minutes: Int): Flow<MessagesState.PartialState> = flow {
        emit(MessagesState.PartialState.SetReminder(minutes))
        emit(MessagesState.PartialState.ApplyPreview(render(uiState.value.template, minutes)))
        emit(MessagesState.PartialState.SetPanelPreviews(panelPreviews(minutes)))
    }

    /**
     * Selecting PANEL is refused locally when the plan doesn't cover it, so the
     * user never sees a state they cannot save.
     */
    private fun setDelivery(delivery: String): Flow<MessagesState.PartialState> = flow {
        if (delivery == ReminderDelivery.PANEL.value && !uiState.value.canUsePanelDelivery) {
            emit(MessagesState.PartialState.ShowMessage(UiMessage.warning(Str.automatic_reminders_arent_included_in)))
            return@flow
        }
        emit(MessagesState.PartialState.SetDelivery(delivery))
    }

    private fun save(): Flow<MessagesState.PartialState> = flow {
        val business = BusinessStateHolder.selectedBusiness.value
        if (business == null) {
            emit(MessagesState.PartialState.ShowMessage(UiMessage.warning(Str.no_business_selected)))
            return@flow
        }
        val state = uiState.value
        emit(MessagesState.PartialState.IsSaving(true))
        PreferencesManager.setMessageTemplate(business.id, state.template)
        PreferencesManager.setNotificationReminderMinutes(state.reminderMinutes)

        val updated = business.copy(
            reminderDelivery = state.reminderDelivery,
            reminderTemplate = state.reminderTemplateKey,
            notificationMinutesBefore = state.reminderMinutes,
            // Choosing automatic sending *is* asking for the client to be
            // reminded; with this switch off (it lives on the notifications
            // screen) the server would silently send nothing.
            enableReminderSms = if (state.isPanel) true else business.enableReminderSms,
        )
        if (updated != business) {
            try {
                val saved = businessUpsertUseCase(updated)
                if (saved != null) BusinessStateHolder.selectBusiness(saved)
            } catch (e: ApiException) {
                // The whole business goes out on this save, so a 403 can be
                // about any plan-gated setting — the server's message says
                // which. Only when this save was the one switching PANEL on
                // can the refusal be about it (the server lets a setting that
                // is already saved through): fall back to the mode that always
                // works instead of leaving the UI claiming PANEL is on.
                val switchingPanelOn = updated.reminderDelivery == ReminderDelivery.PANEL.value &&
                    business.reminderDelivery != ReminderDelivery.PANEL.value
                if (e.code == 403 && switchingPanelOn) {
                    emit(MessagesState.PartialState.SetDelivery(ReminderDelivery.MANUAL.value))
                    emit(MessagesState.PartialState.SetPanelAllowed(false))
                }
                // 403 is the plan saying no, not something breaking.
                val text = e.message ?: Str.couldnt_save_settings
                emit(
                    MessagesState.PartialState.ShowMessage(
                        if (e.code == 403) UiMessage.warning(text) else UiMessage.error(text)
                    )
                )
                emit(MessagesState.PartialState.IsSaving(false))
                return@flow
            } catch (e: Exception) {
                emit(MessagesState.PartialState.ShowMessage(UiMessage.error(e.message ?: Str.couldnt_save_settings)))
                emit(MessagesState.PartialState.IsSaving(false))
                return@flow
            }
        }
        emit(MessagesState.PartialState.IsSaving(false))
        emit(
            MessagesState.PartialState.ShowMessage(
                UiMessage.success(
                    if (state.isPanel) Str.saved_reminders_are_sent_automatically
                    else Str.settings_saved
                )
            )
        )
    }

    override fun reduceState(
        currentState: MessagesState,
        partialState: MessagesState.PartialState
    ): MessagesState {
        return when (partialState) {
            is MessagesState.PartialState.IsLoading -> currentState.copy(isLoading = partialState.loading)
            is MessagesState.PartialState.IsSaving -> currentState.copy(isSaving = partialState.saving)
            is MessagesState.PartialState.ShowMessage -> currentState.copy(message = partialState.msg)
            is MessagesState.PartialState.ApplyBusiness -> currentState.copy(
                businessId = partialState.id,
                businessTitle = partialState.title,
                notificationsOff = partialState.notificationsOff
            )

            is MessagesState.PartialState.ApplyTemplate -> currentState.copy(template = partialState.text)
            is MessagesState.PartialState.ApplyPreview -> currentState.copy(preview = partialState.text)
            is MessagesState.PartialState.SetReminder -> currentState.copy(reminderMinutes = partialState.minutes)
            is MessagesState.PartialState.LoadReadyTemplates -> currentState.copy(readyTemplates = partialState.list)
            is MessagesState.PartialState.SetDelivery -> currentState.copy(reminderDelivery = partialState.delivery)
            is MessagesState.PartialState.SetPanelTemplate -> currentState.copy(reminderTemplateKey = partialState.key)
            is MessagesState.PartialState.SetPanelPreviews -> currentState.copy(panelPreviews = partialState.previews)
            is MessagesState.PartialState.SetPanelAllowed -> currentState.copy(canUsePanelDelivery = partialState.allowed)
        }
    }

    override fun createErrorState(message: String): MessagesState.PartialState =
        MessagesState.PartialState.ShowMessage(UiMessage.error(message))

    private fun render(
        template: String,
        minutes: Int = uiState.value.reminderMinutes
    ): String {
        val business = BusinessStateHolder.selectedBusiness.value
        return generateReminderMessageUseCase.generatePreview(
            template = template,
            businessTitle = business?.title ?: Str.your_business,
            businessAddress = business?.address ?: "--",
            serviceDuration = business?.defaultServiceDuration,
            reminderMinutes = minutes
        )
    }

    private fun panelPreviews(minutes: Int): Map<String, String> =
        ReminderTemplates.all.associate { it.key to render(it.text, minutes) }

    private fun defaultTemplates(): List<String> = listOf(
        "با سلام {visitor} عزیز 🌹؛ یادآوری نوبت شما در {business} برای ساعت {time}. مدت زمان خدمت به شما حدود {duration} است. لطفاً تا {minutes} دقیقه دیگر در محل حضور داشته باشید.", // i18n-keep: customer SMS template
        "سلام {visitor} جان! 👋 منتظر دیدار شما در {business} هستیم. نوبت شما: {time}. زمان خدمت به شما حدود: {duration} دقیقه. ممنون می‌شیم {minutes} دقیقه دیگه تشریف بیارید.", // i18n-keep: customer SMS template
        "{visitor} گرامی؛ نوبت {business} شما ساعت {time} آغاز می‌شود (مدت خدمت به شما حدود: {duration} دقیقه). لطفا جهت پذیرش، {minutes} دقیقه دیگر مراجعه فرمایید.", // i18n-keep: customer SMS template
        "درود بر شما {visitor} عزیز؛ زمان نوبت شما در {business}: ساعت {time}. این سرویس حدود {duration} زمان می‌برد. لطفا {minutes} دقیقه دیگر حضور بهم رسانید. 🕒", // i18n-keep: customer SMS template
        "نوبت دهی {business}: {visitor} عزیز، نوبت شما ساعت {time} است. مدت کار حدود: {duration} . لطفا {minutes} دقیقه دیگر تشریف بیاورید. 📍" // i18n-keep: customer SMS template
    )
}
