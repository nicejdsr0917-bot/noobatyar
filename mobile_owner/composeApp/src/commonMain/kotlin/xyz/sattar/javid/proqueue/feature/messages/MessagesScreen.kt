package xyz.sattar.javid.proqueue.feature.messages

import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.ui.components.AdaptiveColumns
import xyz.sattar.javid.proqueue.core.ui.components.adaptivePagePadding
import xyz.sattar.javid.proqueue.core.utils.AppInfo
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import proqueue.composeapp.generated.resources.*
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.core.ui.components.AppButton
import xyz.sattar.javid.proqueue.core.ui.components.ChoiceTile
import xyz.sattar.javid.proqueue.core.ui.components.SmsBubble
import xyz.sattar.javid.proqueue.core.ui.components.SmsCounter
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost
import xyz.sattar.javid.proqueue.core.ui.components.showToasty
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import xyz.sattar.javid.proqueue.domain.model.business.PanelSmsSuffix
import xyz.sattar.javid.proqueue.domain.model.business.ReminderDelivery
import xyz.sattar.javid.proqueue.domain.model.business.ReminderTemplates
import xyz.sattar.javid.proqueue.domain.model.business.SmsSegments

private val SuccessGreen = Color(0xFF10B981)
private val WarningAmber = Color(0xFFF59E0B)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessagesScreen(
    viewModel: MessagesViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToAddons: () -> Unit = {},
    onNavigateToSmsReport: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    viewModel.events.collectWithLifecycleAware { event ->
        when (event) {
            MessagesEvent.NavigateToAddons -> onNavigateToAddons()
            MessagesEvent.NavigateToSmsReport -> onNavigateToSmsReport()
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { snackbarHostState.showToasty(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.sendIntent(MessagesIntent.Load)
    }

    Scaffold(
        snackbarHost = { ToastyHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        Str.messages_auto_item,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = Str.back)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                shadowElevation = 8.dp
            ) {
                AppButton(
                    onClick = { viewModel.sendIntent(MessagesIntent.Save) },
                    text = Str.messages_save,
                    isLoading = uiState.isSaving,
                    enabled = !uiState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding()
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = adaptivePagePadding()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Setup on one side, the resulting SMS on the other: on a wide
            // window the preview stays in view while the owner edits, instead
            // of sitting a scroll below the text box it reflects.
            AdaptiveColumns(
                startWeight = 1.25f,
                endWeight = 1f,
                spacing = 14.dp,
                start = {
            // ── 1. Who sends ──
            Section(
                step = 1,
                title = Str.reminder_delivery_title,
                subtitle = Str.reminder_delivery_subtitle
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChoiceTile(
                        icon = Icons.Rounded.PhoneAndroid,
                        title = Str.manually_from_your_phone,
                        badge = Str.free,
                        lockedBadge = Str.mobile_app_only,
                        selected = !uiState.isPanel,
                        // Manual delivery opens the phone's own SMS app. A
                        // browser has no SIM card to hand that to, so on web
                        // it is shown but can't be picked. An owner already on
                        // MANUAL keeps it; only choosing PANEL changes it.
                        locked = AppInfo.isWeb,
                        onClick = { viewModel.sendIntent(MessagesIntent.SetDelivery(ReminderDelivery.MANUAL.value)) },
                        modifier = Modifier.weight(1f)
                    )
                    ChoiceTile(
                        icon = Icons.Rounded.CloudUpload,
                        title = Str.automatically_from_panel,
                        badge = Str.uses_sms_credit,
                        selected = uiState.isPanel,
                        locked = !uiState.canUsePanelDelivery,
                        onClick = { viewModel.sendIntent(MessagesIntent.SetDelivery(ReminderDelivery.PANEL.value)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(12.dp))

                AnimatedContent(
                    targetState = Triple(uiState.isPanel, uiState.canUsePanelDelivery, uiState.notificationsOff),
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "delivery-status"
                ) { (panel, allowed, notificationsOff) ->
                    when {
                        panel && notificationsOff -> StatusBanner(
                            icon = Icons.Rounded.WarningAmber,
                            tint = WarningAmber,
                            text = Str.notifications_are_off_for_this +
                                Str.server_wont_send_any_reminders
                        )
                        panel -> StatusBanner(
                            icon = Icons.Rounded.CheckCircle,
                            tint = SuccessGreen,
                            text = Str.minutes_before_each_appointment_server(uiState.reminderMinutes.toString().toPersianDigits()) +
                                Str.sends_reminder_sms_to_customer,
                            actionLabel = Str.sent_sms_report,
                            actionIcon = Icons.AutoMirrored.Rounded.ReceiptLong,
                            onAction = { viewModel.sendIntent(MessagesIntent.OpenSmsReport) }
                        )
                        !allowed -> StatusBanner(
                            icon = Icons.Rounded.Lock,
                            tint = MaterialTheme.colorScheme.primary,
                            text = Str.reminder_delivery_panel_locked +
                                Str.with_automatic_sending_you_no,
                            actionLabel = Str.turn_on_automatic_sending,
                            onAction = { viewModel.sendIntent(MessagesIntent.UpgradeForPanelDelivery) }
                        )
                        else -> StatusBanner(
                            icon = Icons.Rounded.Info,
                            tint = MaterialTheme.colorScheme.primary,
                            text = Str.at_reminder_time_you_get +
                                Str.to_customer
                        )
                    }
                }
            }

            // ── 2. When ──
            Section(
                step = 2,
                title = Str.reminder_lead_time_title,
                subtitle = Str.reminder_lead_time_description,
                icon = Icons.Rounded.Schedule
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    reminderLeadTimeOptions.forEach { minutes ->
                        FilterChip(
                            selected = uiState.reminderMinutes == minutes,
                            onClick = { viewModel.sendIntent(MessagesIntent.SetReminder(minutes)) },
                            label = { Text(Str.min(minutes.toString().toPersianDigits())) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // ── 3. What ──
            AnimatedContent(
                targetState = uiState.isPanel,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "text-mode"
            ) { panel ->
                if (panel) {
                    Section(
                        step = 3,
                        title = Str.choose_sms_text,
                        subtitle = Str.with_automatic_sending_text_is +
                            Str.so_it_always_complies_with
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ReminderTemplates.all.forEach { tpl ->
                                PanelTemplateOption(
                                    label = tpl.label,
                                    preview = uiState.panelPreviews[tpl.key].orEmpty(),
                                    selected = uiState.reminderTemplateKey == tpl.key,
                                    onClick = { viewModel.sendIntent(MessagesIntent.SelectPanelTemplate(tpl.key)) }
                                )
                            }
                        }
                    }
                } else {
                    Section(
                        step = 3,
                        title = Str.message_text,
                        subtitle = Str.tap_variable_to_add_it
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                        ) {
                            MessageToken.entries.forEach { token ->
                                AssistChip(
                                    onClick = { viewModel.sendIntent(MessagesIntent.InsertToken(token.token)) },
                                    label = { Text("+ ${token.label}") },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = AssistChipDefaults.assistChipColors(
                                        labelColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = uiState.template,
                            onValueChange = { viewModel.sendIntent(MessagesIntent.UpdateTemplate(it)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            maxLines = 8,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = Str.messages_ready_subtitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                        ) {
                            uiState.readyTemplates.forEachIndexed { index, tpl ->
                                SuggestionChip(
                                    onClick = { viewModel.sendIntent(MessagesIntent.ApplyReadyTemplate(tpl)) },
                                    label = { Text(Str.template((index + 1).toString().toPersianDigits())) },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                }
            }

                },
                end = {
            // ── 4. What the customer receives ──
            val body = if (uiState.isPanel) uiState.panelPreviews[uiState.reminderTemplateKey].orEmpty()
            else uiState.preview
            val finalText = if (uiState.isPanel) PanelSmsSuffix.sign(body) else body
            Section(
                step = 4,
                title = Str.messages_preview_label,
                subtitle = Str.exact_sms_customer_receives_with,
                icon = Icons.Rounded.Sms
            ) {
                SmsBubble(
                    body = if (uiState.isPanel) body.replace(".", "").trimEnd() else body,
                    lockedSuffix = uiState.isPanel
                )
                Spacer(Modifier.height(12.dp))
                val parts = SmsSegments.parts(finalText).toString().toPersianDigits()
                SmsCounter(
                    text = finalText,
                    footnote = if (uiState.isPanel) {
                        Str.each_persian_sms_is_70 +
                            Str.sms_are_deducted_from_your(parts)
                    } else {
                        Str.each_persian_sms_is_70_2
                    }
                )
            }
                }
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Section(
    step: Int,
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (icon != null) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                    } else {
                        Text(
                            step.toString().toPersianDigits(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.2f
                )
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun StatusBanner(
    icon: ImageVector,
    tint: Color,
    text: String,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    onAction: () -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = tint.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (actionLabel != null) {
                TextButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.padding(start = 18.dp)
                ) {
                    if (actionIcon != null) {
                        Icon(actionIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun PanelTemplateOption(
    label: String,
    preview: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(selected = selected, onClick = onClick, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (selected) primary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

enum class MessageToken(private val labelRef: () -> String, val token: String) {
    Visitor(Str::customer_name, "{visitor}"),
    Business(Str::business_name, "{business}"),
    Address(Str::address, "{address}"),
    Date(Str::date, "{date}"),
    Time(Str::time, "{time}"),
    Minutes(Str::reminder_minutes, "{minutes}"),
    Duration(Str::service_time, "{duration}");

    val label: String get() = labelRef()
}
