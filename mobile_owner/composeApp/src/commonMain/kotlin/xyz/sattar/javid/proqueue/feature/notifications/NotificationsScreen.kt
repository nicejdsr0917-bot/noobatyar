package xyz.sattar.javid.proqueue.feature.notifications

import xyz.sattar.javid.proqueue.core.ui.components.AdaptiveColumns
import xyz.sattar.javid.proqueue.core.ui.components.adaptivePagePadding
import xyz.sattar.javid.proqueue.core.utils.AppInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import proqueue.composeapp.generated.resources.*
import xyz.sattar.javid.proqueue.core.permissions.rememberNotificationPermissionLauncher
import xyz.sattar.javid.proqueue.domain.model.business.ReminderDelivery
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.core.ui.components.AppButton
import xyz.sattar.javid.proqueue.core.ui.components.ChoiceTile
import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
import xyz.sattar.javid.proqueue.core.ui.components.showToasty
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import xyz.sattar.javid.proqueue.feature.messages.reminderLeadTimeOptions
import xyz.sattar.javid.proqueue.core.ui.components.FeatureGate
import xyz.sattar.javid.proqueue.ui.theme.AppTheme
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.EntitlementKeys

@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToPayment: (String) -> Unit = {},
    onNavigateToMessages: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberNotificationPermissionLauncher { granted ->
        viewModel.sendIntent(NotificationsIntent.PermissionResult(granted))
    }

    HandleEffects(
        events = viewModel.events,
        onNavigateBack = onNavigateBack,
        showSnackbar = { message ->
            scope.launch { snackbarHostState.showToasty(message) }
        },
        onRequestPermission = { permissionLauncher.launch() },
        onOpenPaymentUrl = onNavigateToPayment
    )

    // Error partials (e.g. "PANEL isn't in your plan") were written to state
    // and never rendered anywhere, so the tap just silently did nothing.
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showToasty(it)
            viewModel.sendIntent(NotificationsIntent.ClearMessage)
        }
    }

    NotificationsPhoneContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onNavigateToMessages = onNavigateToMessages,
        onIntent = viewModel::sendIntent
    )
}

@Composable
private fun NotificationsPhoneContent(
    uiState: NotificationsState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onNavigateToMessages: () -> Unit,
    onIntent: (NotificationsIntent) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.reminders_notifications_item),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 8.dp) {
                AppButton(
                    text = stringResource(Res.string.save_settings),
                    onClick = { onIntent(NotificationsIntent.SaveSettings) },
                    isLoading = uiState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding()
                )
            }
        },
        snackbarHost = { ToastyHost(hostState = snackbarHostState) },
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
            Spacer(modifier = Modifier.height(4.dp))

            // The owner's own alerts beside the customer's reminder on a wide
            // window: two independent settings, so neither should push the
            // other below the fold.
            AdaptiveColumns(
                spacing = 14.dp,
                start = {
            FeatureGate(
                entitlements = uiState.entitlements,
                plans = uiState.plans,
                featureKey = EntitlementKeys.PUSH_NOTIFICATIONS,
                title = stringResource(Res.string.enable_notifications),
                description = "برای دریافت اعلان نوبت جدید و یادآوری روی گوشی یا مرورگر خودتان، پلن شما باید این ویژگی را پوشش دهد.",
                onUpgrade = { planId -> onIntent(NotificationsIntent.UpgradePlan(planId)) }
            ) {
                OwnerCard(uiState = uiState, onIntent = onIntent)
            }
                },
                end = {
            // The client's reminder rides on the same schedule, so it only
            // makes sense once the business's notifications are on.
            if (uiState.isNotificationsEnabled) {
                ClientCard(uiState = uiState, onIntent = onIntent, onNavigateToMessages = onNavigateToMessages)
            }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(42.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (checked) 1f else 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon, null,
                        tint = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
            if (checked) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }
}

@Composable
private fun SubTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface)
}

/** The owner's own reminder (push on their phone) and when it fires. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OwnerCard(uiState: NotificationsState, onIntent: (NotificationsIntent) -> Unit) {
    SectionCard(
        icon = Icons.Rounded.Notifications,
        title = stringResource(Res.string.enable_notifications),
        subtitle = "اعلان نوبت جدید و یادآوری نوبت‌ها روی گوشی خودتان",
        checked = uiState.isNotificationsEnabled,
        onCheckedChange = { onIntent(NotificationsIntent.ToggleNotifications(it)) }
    ) {
        // The switch is the saved server setting, shared across every device
        // the owner uses — it stays on even when *this* device never granted
        // permission, which reads as a broken toggle unless it's said.
        if (!uiState.hasPermission) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.errorContainer).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.NotificationsOff, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "این دستگاه اجازه‌ی نمایش اعلان را نداده؛ یادآوری‌ها روی همین گوشی دیده نمی‌شوند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onIntent(NotificationsIntent.ToggleNotifications(true)) }) {
                    Text("اجازه بده", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        SubTitle(stringResource(Res.string.notification_reminder_time))
        Spacer(Modifier.height(10.dp))
        val current = uiState.reminderMinutes.toIntOrNull()
        // A value saved earlier from the old free-text field stays selectable.
        val options = (reminderLeadTimeOptions + listOfNotNull(current)).distinct().sorted()
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEach { minutes ->
                FilterChip(
                    selected = current == minutes,
                    onClick = { onIntent(NotificationsIntent.UpdateReminderMinutes(minutes.toString())) },
                    leadingIcon = if (current == minutes) {
                        { Icon(Icons.Rounded.Timer, null, Modifier.size(16.dp)) }
                    } else null,
                    label = { Text("${minutes.toString().toPersianDigits()} دقیقه") },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

/** Whether the customer is reminded too, and who sends it. */
@Composable
private fun ClientCard(
    uiState: NotificationsState,
    onIntent: (NotificationsIntent) -> Unit,
    onNavigateToMessages: () -> Unit
) {
    val panel = uiState.reminderDelivery == ReminderDelivery.PANEL.value
    SectionCard(
        icon = Icons.Rounded.Sms,
        title = "یادآوری برای مشتری",
        subtitle = "پیش از نوبت، پیامک یادآوری برای مشتری فرستاده شود",
        checked = uiState.remindClient,
        onCheckedChange = { onIntent(NotificationsIntent.ToggleRemindClient(it)) }
    ) {
        SubTitle(stringResource(Res.string.reminder_delivery_title))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceTile(
                icon = Icons.Rounded.PhoneAndroid,
                title = "دستی از گوشی شما",
                badge = "رایگان",
                selected = !panel,
                // A browser has no SIM card to send a manual SMS from.
                locked = AppInfo.isWeb,
                lockedBadge = "فقط در اپ موبایل",
                onClick = { onIntent(NotificationsIntent.SetDelivery(ReminderDelivery.MANUAL.value)) },
                modifier = Modifier.weight(1f)
            )
            ChoiceTile(
                icon = Icons.Rounded.CloudUpload,
                title = "خودکار از پنل",
                badge = "از اعتبار پیامک",
                selected = panel,
                locked = !uiState.canUsePanelDelivery,
                onClick = { onIntent(NotificationsIntent.SetDelivery(ReminderDelivery.PANEL.value)) },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (panel) stringResource(Res.string.reminder_delivery_panel_description)
            else stringResource(Res.string.reminder_delivery_manual_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!uiState.canUsePanelDelivery) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(Res.string.reminder_delivery_panel_locked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onNavigateToMessages,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.EditNote, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("متن پیامک یادآوری")
        }
    }
}


@Composable
fun HandleEffects(
    events: Flow<NotificationsEvent>,
    onNavigateBack: () -> Unit,
    showSnackbar: (UiMessage) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenPaymentUrl: (String) -> Unit = {}
) {
    val savedMessage = stringResource(Res.string.settings_saved)
    events.collectWithLifecycleAware { event ->
        when (event) {
            NotificationsEvent.NavigateBack -> onNavigateBack()
            NotificationsEvent.ShowSavedConfirmation -> showSnackbar(UiMessage.success(savedMessage))
            NotificationsEvent.RequestPermission -> onRequestPermission()
            is NotificationsEvent.ShowToast -> showSnackbar(event.message)
            is NotificationsEvent.OpenUrl -> onOpenPaymentUrl(event.url)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewNotificationsScreen() {
    AppTheme {
    }
}
