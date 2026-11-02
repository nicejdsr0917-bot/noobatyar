package xyz.sattar.javid.proqueue.feature.settings

import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.i18n.AppLanguage
import xyz.sattar.javid.proqueue.core.i18n.AppLocale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import xyz.sattar.javid.proqueue.core.ui.components.AdaptiveGrid
import xyz.sattar.javid.proqueue.core.ui.components.ContentWidth
import xyz.sattar.javid.proqueue.core.ui.components.AdaptiveColumns
import xyz.sattar.javid.proqueue.core.ui.components.adaptivePagePadding
import xyz.sattar.javid.proqueue.core.ui.components.isWideLayout
import xyz.sattar.javid.proqueue.core.navigation.MainTab
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import coil3.compose.AsyncImage
import kotlin.math.roundToInt
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.SubscriptionDto
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import proqueue.composeapp.generated.resources.*
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.core.ui.components.BottomBarSpacer
import xyz.sattar.javid.proqueue.core.ui.components.ModerationBadge
import xyz.sattar.javid.proqueue.core.ui.components.ModerationBanner
import xyz.sattar.javid.proqueue.core.ui.components.PullToRefreshBox
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost
import xyz.sattar.javid.proqueue.core.ui.components.showToasty

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
    userViewModel: xyz.sattar.javid.proqueue.feature.profile.UserViewModel = koinViewModel(),
    onNavigateToAbout: () -> Unit = {},
    onChangeBusiness: () -> Unit = {},
    onNavigateToEditBusiness: (Long) -> Unit = {},
    onNavigateToAdvancedSettings: (Long) -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToMessages: () -> Unit = {},
    onNavigateToSmsReport: () -> Unit = {},
    onNavigateToEmergencyNotice: () -> Unit = {},
    onNavigateToTheme: () -> Unit = {},
    onNavigateToPromoCampaign: () -> Unit = {},
    onNavigateToReviews: () -> Unit = {},
    onNavigateToShowcase: () -> Unit = {},
    onNavigateToWallet: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val userState by userViewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showNotificationToast by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Initial load happens once in SettingsViewModel.init (it observes the
    // selected business). Not re-triggered here to avoid repeat requests when
    // returning to this tab.

    // This screen previously had no toast host at all, so delete-business
    // errors from SettingsState.message vanished silently.
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showToasty(it)
            viewModel.sendIntent(SettingsIntent.ClearMessage)
        }
    }

    HandleEvents(
        events = viewModel.events,
        onNavigateToAbout = onNavigateToAbout,
        onChangeBusiness = onChangeBusiness,
        onNavigateToEditBusiness = onNavigateToEditBusiness,
        onNavigateToNotifications = onNavigateToNotifications,
        onNavigateToMessages = onNavigateToMessages,
        onNavigateToSmsReport = onNavigateToSmsReport,
        onNavigateToEmergencyNotice = onNavigateToEmergencyNotice
    )

    userViewModel.events.collectWithLifecycleAware { event ->
        when (event) {
            xyz.sattar.javid.proqueue.feature.profile.UserEvent.LogoutSuccess -> onNavigateToLogin()
        }
    }

    if (showNotificationToast) {
        AlertDialog(
            onDismissRequest = { showNotificationToast = false },
            confirmButton = {
                TextButton(onClick = { showNotificationToast = false }) {
                    Text(Str.confirm)
                }
            },
            title = { Text(Str.notification_title) },
            text = { Text(Str.coming_soon_message) }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(Str.delete_business) },
            text = { Text(Str.delete_business_confirmation) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.sendIntent(SettingsIntent.OnDeleteBusinessClick)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(Str.delete)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(Str.cancel)
                }
            }
        )
    }

    SettingsContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        userName = userState.userName,
        userPhone = userState.userNumber,
        subscription = userState.subscription,
        isRefreshing = uiState.isLoading || userState.isLoading,
        onRefresh = {
            viewModel.sendIntent(SettingsIntent.RefreshSettings)
            userViewModel.sendIntent(xyz.sattar.javid.proqueue.feature.profile.UserIntent.LoadProfile)
        },
        onIntent = viewModel::sendIntent,
        onShowThemeSheet = onNavigateToTheme,
        onShowDeleteDialog = { showDeleteDialog = true },
        onNavigateToLogin = onNavigateToLogin,
        onLogout = { userViewModel.sendIntent(xyz.sattar.javid.proqueue.feature.profile.UserIntent.Logout) },
        onChangeBusiness = onChangeBusiness,
        onAdvancedSettings = {
            uiState.currentBusiness?.let {
                onNavigateToAdvancedSettings(it.id)
            }
        },
        onPromoCampaign = onNavigateToPromoCampaign,
        onReviews = onNavigateToReviews,
        onShowcase = onNavigateToShowcase,
        onWallet = onNavigateToWallet
    )
}

@Composable
fun SettingsContent(
    modifier: Modifier = Modifier,
    uiState: SettingsState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    userName: String? = null,
    userPhone: String? = null,
    subscription: xyz.sattar.javid.proqueue.data.remoteDataSource.user.model.SubscriptionDto? = null,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onIntent: (SettingsIntent) -> Unit,
    onShowThemeSheet: () -> Unit,
    onShowDeleteDialog: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onLogout: () -> Unit = {},
    onChangeBusiness: () -> Unit = {},
    onAdvancedSettings: () -> Unit,
    onPromoCampaign: () -> Unit = {},
    onReviews: () -> Unit = {},
    onShowcase: () -> Unit = {},
    onWallet: () -> Unit = {}
) {
    // No top app bar here: the profile hero below is this screen's header, so a
    // separate toolbar showing the business title would just overlap it. We only
    // reserve the status-bar inset so the hero sits below the status bar.
    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = { ToastyHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            refreshTab = MainTab.Settings,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = adaptivePagePadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            if (isWideLayout()) {
                // Desktop: the sidebar beside this page already lists the
                // customer pages, advanced settings, about and logout, so
                // repeating them here was a second copy of the menu. Only
                // what this page alone offers is left — who you are, the
                // business itself, and the app's theme — at the list width the
                // settings page is meant for, centred under the header.
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        modifier = Modifier.widthIn(max = ContentWidth.List),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ProfileHeaderCard(
                            userName = userName,
                            userPhone = userPhone,
                            business = uiState.currentBusiness,
                            subscription = subscription
                        )
                        WalletCard(onClick = onWallet)
                        uiState.currentBusiness?.let { business ->
                            ModerationBanner(
                                business = business,
                                onEditClick = { onIntent(SettingsIntent.OnEditBusinessClick(business.id)) }
                            )
                        }
                        SettingsGroupLabel(Str.manage_business)
                        SettingsTiles(
                            tiles = listOf(
                                SettingsTile(
                                    icon = Icons.Rounded.Factory,
                                    title = Str.change_business,
                                    subtitle = Str.switch_to_another_business_from,
                                    onClick = { onIntent(SettingsIntent.OnChangeBusinessClick) }
                                ),
                                SettingsTile(
                                    icon = Icons.Rounded.Edit,
                                    title = Str.edit_business_title,
                                    subtitle = Str.name_logo_opening_hours_and,
                                    onClick = {
                                        uiState.currentBusiness?.let { business ->
                                            onIntent(SettingsIntent.OnEditBusinessClick(business.id))
                                        }
                                    }
                                ),
                                SettingsTile(
                                    icon = Icons.Rounded.PhotoLibrary,
                                    title = Str.showcase_title,
                                    subtitle = Str.showcase_subtitle,
                                    onClick = onShowcase
                                ),
                                SettingsTile(
                                    icon = Icons.Rounded.Delete,
                                    title = Str.delete_business,
                                    subtitle = Str.remove_this_business_from_your,
                                    danger = true,
                                    onClick = onShowDeleteDialog
                                )
                            )
                        )
                        // The app's look, not the business's — its own group.
                        SettingsGroupLabel(Str.app_appearance)
                        SettingsCard {
                            Column {
                                SettingsItem(
                                    icon = Icons.Rounded.Palette,
                                    title = Str.theme_appearance,
                                    subtitle = Str.theme_settings,
                                    onClick = onShowThemeSheet
                                )
                                HorizontalDivider()
                                LanguageSettingsItem()
                            }
                        }
                    }
                }
            } else {
            // Phone: the full list — there is no sidebar, so this page is the
            // way to everything below. Who you are and which business you run
            // first, then how the business talks to customers and looks.
            AdaptiveColumns(
                startWeight = 1f,
                endWeight = 1.15f,
                start = {
            // Profile identity hero — the owner sees who they are and which
            // business they're currently managing, front and center.
            ProfileHeaderCard(
                userName = userName,
                userPhone = userPhone,
                business = uiState.currentBusiness,
                subscription = subscription
            )

            // Add-on credit sits with the plan badge above: both are about what
            // the owner has paid for, not about the current business.
            WalletCard(onClick = onWallet)

            // Moderation state of the active business. Only renders for
            // pending/rejected/suspended — an approved business just keeps its
            // badge in the hero above. Separate from the plan/subscription
            // badge on purpose: "not reviewed yet" is not "plan lapsed".
            uiState.currentBusiness?.let { business ->
                ModerationBanner(
                    business = business,
                    onEditClick = { onIntent(SettingsIntent.OnEditBusinessClick(business.id)) }
                )
            }

            // Advanced settings — a separate, eye-catching card with a looping
            // shine animation to draw the owner in (upsell to premium features).
            AdvancedSettingsPromoCard(onClick = onAdvancedSettings)

            // Business actions: change + delete, grouped together.
            BusinessActionsCard(
                business = uiState.currentBusiness,
                onIntent = onIntent,
                onShowDeleteDialog = onShowDeleteDialog,
                onShowcase = onShowcase
            )
                },
                end = {
            SettingsGroupLabel(Str.customer_contact)
            SettingsCard {
                Column {
                    // Its own row rather than an inline switch+textfield card —
                    // this is a nav list, and a form sitting between two other
                    // rows broke the rhythm of it. The actual editor lives on
                    // EmergencyNoticeScreen.
                    SettingsItem(
                        icon = Icons.Rounded.Campaign,
                        title = Str.notice_section_title,
                        subtitle = Str.notice_settings_subtitle,
                        onClick = { onIntent(SettingsIntent.OnEmergencyNoticeClick) }
                    )

                    HorizontalDivider()

                    SettingsItem(
                        icon = Icons.AutoMirrored.Rounded.Message,
                        title = Str.messages_auto_item,
                        subtitle = Str.messages_auto_subtitle,
                        onClick = { onIntent(SettingsIntent.OnMessagesClick) }
                    )

                    HorizontalDivider()

                    SettingsItem(
                        icon = Icons.Rounded.LocalOffer,
                        title = Str.promotional_sms,
                        subtitle = Str.send_discounts_and_news_to,
                        onClick = onPromoCampaign
                    )

                    HorizontalDivider()

                    SettingsItem(
                        icon = Icons.Rounded.RateReview,
                        title = Str.reviews_title,
                        subtitle = Str.reviews_subtitle,
                        onClick = onReviews
                    )

                    HorizontalDivider()

                    SettingsItem(
                        icon = Icons.Rounded.Sms,
                        title = Str.sms_report_title,
                        subtitle = Str.sms_report_settings_subtitle,
                        onClick = { onIntent(SettingsIntent.OnSmsReportClick) }
                    )

                    HorizontalDivider()

                    SettingsItem(
                        icon = Icons.Rounded.Notifications,
                        title = Str.reminders_notifications_item,
                        subtitle = Str.reminders_notifications_subtitle,
                        onClick = { onIntent(SettingsIntent.OnNotificationsClick) }
                    )
                }
            }

            SettingsGroupLabel(Str.appearance_info)
            SettingsCard {
                Column {
                    SettingsItem(
                        icon = Icons.Rounded.Palette,
                        title = Str.theme_appearance,
                        subtitle = Str.theme_settings,
                        onClick = onShowThemeSheet
                    )

                    HorizontalDivider()

                    LanguageSettingsItem()

                    HorizontalDivider()

                    SettingsItem(
                        icon = Icons.Rounded.Info,
                        title = Str.about_us_label,
                        subtitle = Str.about_us_subtitle,
                        onClick = { onIntent(SettingsIntent.OnAboutClick) }
                    )
                }
            }

            // Logout (previously reached via the top-bar avatar, which this
            // screen no longer shows).
            SettingsCard {
                SettingsItem(
                    icon = Icons.AutoMirrored.Rounded.Logout,
                    title = Str.logout_label,
                    subtitle = null,
                    onClick = onLogout,
                    tint = MaterialTheme.colorScheme.error,
                    centerVertically = true
                )
            }
                }
            )
            }

            // Subtle Noobatyar branding footer (moved out of the header, which now
            // belongs to the owner's own identity).
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Image(
                        painter = painterResource(Res.drawable.main_icon),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp).alpha(0.7f)
                    )
                    Text(
                        text = Str.appName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${Str.app_version} ${uiState.appVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            BottomBarSpacer()
        }
        }
    }
}

private class SettingsTile(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
    val danger: Boolean = false
)

/**
 * The desktop page's business actions as one even row of tiles instead of a
 * list stretched across the page — stacked instead when the content column
 * is too narrow for a row (a small browser window). Two subtitle lines are
 * reserved on every tile so they all come out the same height.
 */
@Composable
private fun SettingsTiles(tiles: List<SettingsTile>) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 480.dp) tiles.size else 1
        AdaptiveGrid(
            count = tiles.size,
            compactColumns = columns,
            mediumColumns = columns,
            expandedColumns = columns
        ) { index ->
            SettingsTileCard(tiles[index])
        }
    }
}

@Composable
private fun SettingsTileCard(tile: SettingsTile) {
    val accent = if (tile.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Card(
        onClick = tile.onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tile.icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = tile.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (tile.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = tile.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Change, edit and delete the current business — the one group both the
 *  phone and the desktop page keep. */
@Composable
private fun BusinessActionsCard(
    business: Business?,
    onIntent: (SettingsIntent) -> Unit,
    onShowDeleteDialog: () -> Unit,
    onShowcase: () -> Unit = {}
) {
    SettingsCard {
        Column {
            SettingsItem(
                icon = Icons.Rounded.Factory,
                title = Str.change_business,
                subtitle = null,
                onClick = { onIntent(SettingsIntent.OnChangeBusinessClick) },
                tint = MaterialTheme.colorScheme.onSurface
            )

            HorizontalDivider()

            SettingsItem(
                icon = Icons.Rounded.Edit,
                title = Str.edit_business_title,
                subtitle = null,
                onClick = {
                    business?.let { onIntent(SettingsIntent.OnEditBusinessClick(it.id)) }
                },
                tint = MaterialTheme.colorScheme.onSurface
            )

            HorizontalDivider()

            SettingsItem(
                icon = Icons.Rounded.PhotoLibrary,
                title = Str.showcase_title,
                subtitle = null,
                onClick = onShowcase,
                tint = MaterialTheme.colorScheme.onSurface
            )

            HorizontalDivider()

            SettingsItem(
                icon = Icons.Rounded.Delete,
                title = Str.delete_business,
                subtitle = null,
                onClick = onShowDeleteDialog,
                tint = MaterialTheme.colorScheme.error,
                centerVertically = true
            )
        }
    }
}

/** A quiet heading over a group of settings rows, so related rows read as
 *  one block instead of one long undifferentiated list. */
@Composable
private fun SettingsGroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun AdvancedSettingsPromoCard(onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "adv-promo")
    val shine by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "shine"
    )
    // Gentle breathing scale so the card subtly "pulls" the eye.
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = pulse; scaleY = pulse }
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                )
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val bandWidthPx = widthPx * 0.35f
            val x = -bandWidthPx + shine * (widthPx + bandWidthPx)

            // The moving diagonal shine band.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(with(density) { bandWidthPx.toDp() })
                    .offset { IntOffset(x.roundToInt(), 0) }
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.28f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = Str.advanced_settings_title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = Str.advanced_settings_promo,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
                Icon(
                    imageVector = if (AppLocale.isRtl) Icons.Rounded.ChevronLeft else Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun ProfileHeaderCard(
    userName: String?,
    userPhone: String?,
    business: Business?,
    subscription: SubscriptionDto?
) {
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary
        )
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().background(gradient)) {
            // Decorative oversized glyph in the corner.
            Icon(
                imageVector = Icons.Rounded.AccountCircle,
                contentDescription = null,
                modifier = Modifier
                    .size(170.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 45.dp, y = (-40).dp),
                tint = Color.White.copy(alpha = 0.10f)
            )

            Column(modifier = Modifier.padding(20.dp)) {
                // Owner identity
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userName?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: Str.text_2,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userName ?: Str.noobatyar_user,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        if (!userPhone.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Phone,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = userPhone,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    SubscriptionBadge(subscription = subscription)
                }

                // Active business — so the owner always knows which one they're managing.
                if (business != null) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (business.logoPath.isNotEmpty()) {
                                    val url = if (business.logoPath.startsWith("http")) business.logoPath
                                    else "${xyz.sattar.javid.proqueue.core.utils.AppConfig.baseUrl}${business.logoPath}"
                                    AsyncImage(
                                        model = url,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Storefront,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = Str.active_business,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                // Name only, under the logo that is already
                                // next to it. The category line that used to
                                // sit here ("دندان‌پزشکی", "آرایشگاه") told the
                                // owner something they picked themselves and
                                // never see again — the logo and the name are
                                // what identify a business everywhere else in
                                // the app, so they are all this row carries.
                                Text(
                                    text = business.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 2
                                )
                            }

                            // The decorative "verified" tick used to sit here
                            // unconditionally, which now reads as a moderation
                            // claim. Show the real state when we know it.
                            if (business.moderationStatus != null) {
                                ModerationBadge(business = business)
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Verified,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionBadge(subscription: SubscriptionDto?) {
    val isValid = subscription?.isValid == true
    val isVip = subscription?.plan?.isVip == true
    val label = when {
        isVip -> Str.subscription_vip
        isValid -> Str.subscription_active
        else -> Str.subscription_none
    }
    val icon = if (isValid) Icons.Rounded.Star else Icons.Rounded.Info

    Surface(
        color = Color.White.copy(alpha = if (isValid) 0.25f else 0.12f),
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                MaterialTheme.colorScheme.outlineVariant.copy(
                    alpha = 0.3f
                )
            )
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(/*modifier = Modifier.padding(vertical = 12.dp)*/) {
            content()
        }
    }
}

/** Switches the UI language in place; see core/i18n/AppLocale. */
@Composable
private fun LanguageSettingsItem() {
    var showPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    SettingsItem(
        icon = Icons.Rounded.Language,
        title = Str.language,
        subtitle = if (AppLocale.isEnglish) Str.language_name_en else Str.language_name_fa,
        onClick = { showPicker = true }
    )
    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(Str.language_choose) },
            text = {
                Column {
                    AppLanguage.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showPicker = false
                                    scope.launch { AppLocale.change(option) }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = AppLocale.language == option, onClick = null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                if (option == AppLanguage.EN) Str.language_name_en else Str.language_name_fa,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) { Text(Str.cancel) }
            }
        )
    }
}

@Composable
private fun WalletCard(onClick: () -> Unit) {
    SettingsCard {
        SettingsItem(
            icon = Icons.Rounded.AccountBalanceWallet,
            title = Str.wallet_title,
            subtitle = Str.wallet_settings_subtitle,
            onClick = onClick
        )
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    centerVertically: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = tint.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.size(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = tint,
                fontWeight = if (subtitle == null) FontWeight.Bold else FontWeight.Normal
            )

            Spacer(modifier = Modifier.height(4.dp))
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = tint.copy(alpha = 0.7f)
                )
            }
        }
        if (!centerVertically) {
            Icon(
                imageVector = if (AppLocale.isRtl) Icons.Rounded.ChevronLeft else Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = tint.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun HandleEvents(
    events: kotlinx.coroutines.flow.Flow<SettingsEvent>,
    onNavigateToAbout: () -> Unit,
    onChangeBusiness: () -> Unit,
    onNavigateToEditBusiness: (Long) -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToMessages: () -> Unit,
    onNavigateToSmsReport: () -> Unit,
    onNavigateToEmergencyNotice: () -> Unit
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    events.collectWithLifecycleAware { event ->
        when (event) {
            SettingsEvent.NavigateToAbout -> {
                scope.launch { onNavigateToAbout() }
            }

            SettingsEvent.NavigateToBusinessSelection -> {
                scope.launch { onChangeBusiness() }
            }

            is SettingsEvent.NavigateToEditBusiness -> {
                scope.launch { onNavigateToEditBusiness(event.businessId) }
            }

            SettingsEvent.BusinessDeleted -> {
                scope.launch { onChangeBusiness() }
            }

            SettingsEvent.NavigateToNotifications -> {
                scope.launch { onNavigateToNotifications() }
            }

            SettingsEvent.NavigateToMessages -> {
                scope.launch { onNavigateToMessages() }
            }

            SettingsEvent.NavigateToSmsReport -> {
                scope.launch { onNavigateToSmsReport() }
            }

            SettingsEvent.NavigateToEmergencyNotice -> {
                scope.launch { onNavigateToEmergencyNotice() }
            }
        }
    }
}
