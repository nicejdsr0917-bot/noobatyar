package xyz.sattar.javid.proqueue.feature.messages

import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
import xyz.sattar.javid.proqueue.domain.model.business.DEFAULT_REMINDER_MINUTES
import xyz.sattar.javid.proqueue.domain.model.business.ReminderDelivery
import xyz.sattar.javid.proqueue.domain.model.business.ReminderTemplates

data class MessagesState(
    val businessId: Long? = null,
    val businessTitle: String = "",
    /** The owner's own wording, used only for MANUAL (sent from their SIM). */
    val template: String = "",
    /** [template] rendered with sample values. */
    val preview: String = "",
    val reminderMinutes: Int = DEFAULT_REMINDER_MINUTES,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val message: UiMessage? = null,
    val readyTemplates: List<String> = emptyList(),
    /** [ReminderDelivery] value currently chosen for this business. */
    val reminderDelivery: String = ReminderDelivery.MANUAL.value,
    /** Key of the fixed PANEL wording — see [ReminderTemplates]. */
    val reminderTemplateKey: String = ReminderTemplates.DEFAULT_KEY,
    /** Each fixed PANEL wording rendered with sample values, by key. */
    val panelPreviews: Map<String, String> = emptyMap(),
    /** PANEL is only selectable with the `auto_reminder_sms` entitlement. */
    val canUsePanelDelivery: Boolean = false,
    /** The business's notifications are off, so the server sends nothing. */
    val notificationsOff: Boolean = false,
) {
    val isPanel: Boolean get() = reminderDelivery == ReminderDelivery.PANEL.value

    sealed interface PartialState {
        data class IsLoading(val loading: Boolean) : PartialState
        data class IsSaving(val saving: Boolean) : PartialState
        data class ShowMessage(val msg: UiMessage?) : PartialState
        data class ApplyBusiness(val id: Long, val title: String, val notificationsOff: Boolean) : PartialState
        data class ApplyTemplate(val text: String) : PartialState
        data class ApplyPreview(val text: String) : PartialState
        data class SetReminder(val minutes: Int) : PartialState
        data class LoadReadyTemplates(val list: List<String>) : PartialState
        data class SetDelivery(val delivery: String) : PartialState
        data class SetPanelTemplate(val key: String) : PartialState
        data class SetPanelPreviews(val previews: Map<String, String>) : PartialState
        data class SetPanelAllowed(val allowed: Boolean) : PartialState
    }
}

/** Lead times offered for the reminder, in minutes before the appointment. */
val reminderLeadTimeOptions: List<Int> = listOf(10, 15, 20, 30, 45, 60, 120)
