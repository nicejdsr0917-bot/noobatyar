package xyz.sattar.javid.proqueue.feature.createBusiness

import xyz.sattar.javid.proqueue.core.i18n.Str
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import xyz.sattar.javid.proqueue.core.ui.components.AdaptiveColumns
import xyz.sattar.javid.proqueue.core.ui.components.adaptivePagePadding
import xyz.sattar.javid.proqueue.core.ui.components.showToasty
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.ui.components.AppButton
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.EntitlementKeys
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.EntitlementsResponseDto


/**
 * Standalone "advanced settings" screen (payment / capacity / reminders),
 * separated from the business-edit form. It reuses [CreateBusinessViewModel]:
 * the business is loaded fully, only the advanced fields are edited here, and on
 * save the basic fields are passed through unchanged from the loaded business.
 */
@Composable
fun AdvancedSettingsRoute(
    businessId: Long,
    viewModel: CreateBusinessViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    onOpenPromoCampaign: () -> Unit = {},
    onOpenReminders: () -> Unit = {},
    section: Int = AdvancedSection.ALL
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Advanced fields (mirrors the ones hoisted in CreateBusinessRoute).
    var maxAppointmentsPerHour by remember { mutableStateOf("") }
    var depositMode by remember { mutableStateOf(DepositMode.NONE.value) }
    var depositAmount by remember { mutableStateOf("") }
    var acceptedPaymentMethods by remember { mutableStateOf(setOf<String>()) }
    var cardNumber by remember { mutableStateOf("") }
    var cardOwnerName by remember { mutableStateOf("") }
    var merchantId by remember { mutableStateOf("") }
    var paymentLink by remember { mutableStateOf("") }
    var closedWeekdays by remember { mutableStateOf(setOf<Int>()) }

    LaunchedEffect(businessId) {
        if (businessId != 0L) viewModel.sendIntent(CreateBusinessIntent.LoadBusiness(businessId))
    }
    LaunchedEffect(Unit) {
        viewModel.sendIntent(CreateBusinessIntent.LoadEntitlements)
    }
    LaunchedEffect(uiState.business) {
        uiState.business?.let {
            maxAppointmentsPerHour = it.maxAppointmentsPerHour?.toString() ?: ""
            depositMode = it.depositMode ?: DepositMode.NONE.value
            depositAmount = it.depositAmount?.toString() ?: ""
            acceptedPaymentMethods = it.acceptedPaymentMethods?.toSet() ?: setOf()
            cardNumber = it.cardNumber
            cardOwnerName = it.cardOwnerName
            merchantId = it.merchantId
            paymentLink = it.paymentLink
            closedWeekdays = it.closedWeekdays.toSet()
        }
    }

    // What was last loaded from (or saved to) the server, to tell the owner
    // whether anything on screen is still unsaved. Settings they pay for
    // should never look saved when they aren't — or vice versa.
    val loaded = uiState.business
    val isDirty = loaded != null && (
        maxAppointmentsPerHour != (loaded.maxAppointmentsPerHour?.toString() ?: "") ||
            depositMode != (loaded.depositMode ?: DepositMode.NONE.value) ||
            depositAmount != (loaded.depositAmount?.toString() ?: "") ||
            acceptedPaymentMethods != (loaded.acceptedPaymentMethods?.toSet() ?: emptySet<String>()) ||
            cardNumber != loaded.cardNumber ||
            cardOwnerName != loaded.cardOwnerName ||
            merchantId != loaded.merchantId ||
            paymentLink != loaded.paymentLink
        )

    HandleEvents(
        events = viewModel.events,
        onContinue = onNavigateBack,
        onNavigateBack = onNavigateBack
    )

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showToasty(it)
            viewModel.sendIntent(CreateBusinessIntent.ClearMessage)
        }
    }

    Scaffold(
        snackbarHost = { ToastyHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (section) {
                            AdvancedSection.PAYMENT -> Str.payment_methods_title
                            AdvancedSection.CAPACITY -> Str.hourly_capacity_control
                            AdvancedSection.DEPOSIT -> Str.take_deposit
                            AdvancedSection.HOLD -> Str.hold_minutes_title
                            AdvancedSection.PROMO -> Str.promotional_sms_campaign
                            AdvancedSection.REMINDERS -> Str.automatic_appointment_reminders
                            else -> Str.advanced_settings_title
                        },
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
            val business = uiState.business
            if (business != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.background,
                    shadowElevation = 8.dp
                ) {
                  Column(modifier = Modifier.padding(top = 10.dp)) {
                    SaveStatus(isDirty = isDirty)
                    AppButton(
                        text = if (isDirty) Str.save_changes else Str.accept,
                        enabled = isDirty,
                        onClick = {
                            viewModel.sendIntent(
                                CreateBusinessIntent.CreateBusiness(
                                    title = business.title,
                                    category = business.category,
                                    phone = business.phone,
                                    // Owned by the business-edit form; carried
                                    // through unchanged for the same reason as
                                    // services/allowClientAddService below.
                                    extraPhones = business.extraPhones,
                                    socialLinks = business.socialLinks,
                                    address = business.address,
                                    latitude = business.latitude,
                                    longitude = business.longitude,
                                    locationPrivacy = business.locationPrivacy,
                                    cityCode = business.cityCode,
                                    defaultProgress = business.defaultServiceDuration.toString(),
                                    workStartHour = business.workStartHour,
                                    workEndHour = business.workEndHour,
                                    workStartMinute = business.workStartMinute,
                                    workEndMinute = business.workEndMinute,
                                    closedWeekdays = closedWeekdays.toList(),
                                    allowAnonymousView = business.allowAnonymousView,
                                    notifyOwnerBySms = business.notifyOwnerBySms,
                                    bio = business.bio,
                                    logoBytes = business.logoBytes,
                                    maxAppointmentsPerHour = maxAppointmentsPerHour.toIntOrNull(),
                                    depositMode = depositMode,
                                    depositAmount = depositAmount.toIntOrNull() ?: 0,
                                    acceptedPaymentMethods = acceptedPaymentMethods.joinToString(","),
                                    cardNumber = cardNumber,
                                    cardOwnerName = cardOwnerName,
                                    merchantId = merchantId,
                                    paymentLink = paymentLink,
                                    // Owned by other screens (business edit /
                                    // reminder messages / service picker) —
                                    // passed through unchanged so saving this
                                    // tab can't reset them to their defaults.
                                    // services/allowClientAddService were
                                    // missing here entirely: CreateBusiness's
                                    // defaults (emptyList()/false) silently
                                    // wiped the business's whole service menu
                                    // every time an owner saved anything on
                                    // this screen.
                                    noticeEnabled = business.noticeEnabled,
                                    noticeMessage = business.noticeMessage,
                                    reminderDelivery = business.reminderDelivery,
                                    services = business.services,
                                    allowClientAddService = business.allowClientAddService
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentWidth(Alignment.CenterHorizontally)
                            .widthIn(max = 480.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .navigationBarsPadding(),
                        isLoading = uiState.isLoading
                    )
                  }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        val business = uiState.business
        if (business == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                repeat(4) {
                    xyz.sattar.javid.proqueue.core.ui.components.ListItemShimmer(height = 88.dp)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(paddingValues)
                    .padding(horizontal = adaptivePagePadding())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // What the plan unlocks as a narrow side panel on a wide
                // window, so the tabs being edited keep most of the width.
                AdaptiveColumns(
                    startWeight = 0.75f,
                    endWeight = 1.6f,
                    start = {
                if (section == AdvancedSection.ALL) PlanSummaryCard(entitlements = uiState.entitlements)
                    },
                    end = {
                AdvancedSettingsTabs(
                    entitlements = uiState.entitlements,
                    plans = uiState.plans,
                    onUpgrade = { planId -> viewModel.sendIntent(CreateBusinessIntent.UpgradePlan(planId)) },
                    isLoading = uiState.isLoading,
                    acceptedPaymentMethods = acceptedPaymentMethods,
                    onAcceptedPaymentMethods = { acceptedPaymentMethods = it },
                    cardNumber = cardNumber,
                    onCardNumber = { cardNumber = it },
                    cardOwnerName = cardOwnerName,
                    onCardOwnerName = { cardOwnerName = it },
                    maxAppointmentsPerHour = maxAppointmentsPerHour,
                    onMaxAppointmentsPerHour = { maxAppointmentsPerHour = it },
                    depositMode = depositMode,
                    onDepositMode = { depositMode = it },
                    depositAmount = depositAmount,
                    onDepositAmount = { depositAmount = it },
                    merchantId = merchantId,
                    onMerchantId = { merchantId = it },
                    paymentLink = paymentLink,
                    onPaymentLink = { paymentLink = it },
                    onOpenPromoCampaign = onOpenPromoCampaign,
                    onOpenReminders = onOpenReminders,
                    section = section,
                    businessId = businessId.takeIf { it != 0L }
                )
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

            }
        }
    }
}

/** Which paid capabilities this plan unlocks, at a glance. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanSummaryCard(entitlements: EntitlementsResponseDto?) {
    val features = listOf(
        EntitlementKeys.ONLINE_GATEWAY to Str.online_gateway,
        EntitlementKeys.DEPOSIT to Str.deposit_card_transfer,
        EntitlementKeys.CAPACITY_CONTROL to Str.capacity_control,
        EntitlementKeys.PROMOTIONAL_SMS to Str.promotional_sms,
        EntitlementKeys.AUTO_REMINDER_SMS to Str.automatic_reminders,
    ).filterNot { entitlements?.isComingSoon(it.first) == true }
    val active = features.count { entitlements?.hasFeature(it.first) == true }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xFF6D28D9), Color(0xFF8B5CF6), Color(0xFFA855F7)))
            )
            .padding(18.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.WorkspacePremium, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(
                    Str.pro_business_features,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (entitlements != null) {
                    Text(
                        Str.of_active(active.toString().toPersianDigits(), features.size.toString().toPersianDigits()),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                features.forEach { (key, label) ->
                    val on = entitlements?.hasFeature(key) == true
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = if (on) 0.24f else 0.10f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (on) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = if (on) 1f else 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            label,
                            color = Color.White.copy(alpha = if (on) 1f else 0.75f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

/** One line above the save button saying whether what is on screen is saved. */
@Composable
private fun SaveStatus(isDirty: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isDirty) Color(0xFFF59E0B) else Color(0xFF10B981))
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (isDirty) Str.you_have_unsaved_changes else Str.all_settings_are_saved,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
