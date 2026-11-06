package xyz.sattar.javid.proqueue.feature.settings

import xyz.sattar.javid.proqueue.core.ui.components.ForwardChevron
import xyz.sattar.javid.proqueue.feature.createBusiness.AdvancedSection
import xyz.sattar.javid.proqueue.feature.home.FirstVisitDiscountSheet
import xyz.sattar.javid.proqueue.feature.home.HomeCard
import xyz.sattar.javid.proqueue.feature.home.FollowersListDialog
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Timer
import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
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
    onNavigateToAdvancedSettings: (Long, Int) -> Unit = { _, _ -> },
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToMessages: () -> Unit = {},
    onNavigateToSmsReport: () -> Unit = {},
    onNavigateToEmergencyNotice: () -> Unit = {},
    onNavigateToTheme: () -> Unit = {},
    onNavigateToPromoCampaign: () -> Unit = {},
    onNavigateToReviews: () -> Unit = {},
    onNavigateToShowcase: () -> Unit = {},
    onNavigateToStories: () -> Unit = {},
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
        onAdvancedSection = { section ->
            uiState.currentBusiness?.let {
                onNavigateToAdvancedSettings(it.id, section)
            }
        },
        onPromoCampaign = onNavigateToPromoCampaign,
        onReviews = onNavigateToReviews,
        onShowcase = onNavigateToShowcase,
        onStories = onNavigateToStories,
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
    onAdvancedSection: (Int) -> Unit,
    onPromoCampaign: () -> Unit = {},
    onReviews: () -> Unit = {},
    onShowcase: () -> Unit = {},
    onStories: () -> Unit = {},
    onWallet: () -> Unit = {}
) {
    // The followers list and the first-visit discount open over this screen.
    var showFollowers by remember { mutableStateOf(false) }
    var showFirstVisit by remember { mutableStateOf(false) }
    uiState.currentBusiness?.id?.let { businessId ->
        if (showFollowers) FollowersListDialog(businessId) { showFollowers = false }
        if (showFirstVisit) FirstVisitDiscountSheet(businessId) { showFirstVisit = false }
    }
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
                                    icon = Icons.Rounded.AutoStories,
                                    title = Str.stories_title,
                                    subtitle = Str.stories_subtitle,
                                    onClick = onStories
                                ),
                                SettingsTile(
                                    icon = Icons.Rounded.Groups,
                                    title = Str.followers_list_title,
                                    subtitle = Str.followers_settings_subtitle,
                                    onClick = { showFollowers = true }
                                ),
                                SettingsTile(
                                    icon = Icons.Rounded.LocalOffer,
                                    title = Str.first_visit_title,
                                    subtitle = Str.first_visit_settings_subtitle,
                                    onClick = { showFirstVisit = true }
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

            // Business, booking and payment settings, grouped. Every setting
            // that used to hide behind «تنظیمات پیشرفته» is a row here that
            // opens its own focused screen.
            ProfileSection(Str.profile_group_business, Icons.Rounded.Storefront, Color(0xFF8B5CF6)) {
                ProfileRow(Icons.Rounded.Factory, Str.change_business, Str.switch_to_another_business_from, Color(0xFF8B5CF6),
                    onClick = { onIntent(SettingsIntent.OnChangeBusinessClick) })
                ProfileRow(Icons.Rounded.Edit, Str.edit_business_title, Str.name_logo_opening_hours_and, Color(0xFF8B5CF6),
                    onClick = { uiState.currentBusiness?.let { onIntent(SettingsIntent.OnEditBusinessClick(it.id)) } })
                ProfileRow(Icons.Rounded.PhotoLibrary, Str.showcase_title, Str.showcase_subtitle, Color(0xFF8B5CF6),
                    onClick = onShowcase, last = true)
            }

            ProfileSection(Str.profile_group_booking, Icons.Rounded.EventSeat, Color(0xFF3B82F6)) {
                ProfileRow(Icons.Rounded.Groups, Str.slot_capacity_title, Str.capacity_row_subtitle, Color(0xFF3B82F6),
                    onClick = { onAdvancedSection(AdvancedSection.CAPACITY) })
                ProfileRow(Icons.Rounded.Timer, Str.hold_minutes_title, Str.hold_minutes_row_subtitle, Color(0xFF3B82F6),
                    onClick = { onAdvancedSection(AdvancedSection.HOLD) }, last = true)
            }

            ProfileSection(Str.profile_group_payment, Icons.Rounded.Payments, Color(0xFF10B981)) {
                ProfileRow(Icons.Rounded.CreditCard, Str.payment_methods_title, Str.payment_methods_subtitle, Color(0xFF10B981),
                    onClick = { onAdvancedSection(AdvancedSection.PAYMENT) })
                ProfileRow(Icons.Rounded.AccountBalanceWallet, Str.take_deposit, Str.deposit_row_subtitle, Color(0xFF10B981),
                    onClick = { onAdvancedSection(AdvancedSection.DEPOSIT) }, last = true)
            }
                },
                end = {
            ProfileSection(Str.profile_group_messages, Icons.AutoMirrored.Rounded.Message, Color(0xFFF59E0B)) {
                ProfileRow(Icons.Rounded.Campaign, Str.notice_section_title, Str.notice_settings_subtitle, Color(0xFFF59E0B),
                    onClick = { onIntent(SettingsIntent.OnEmergencyNoticeClick) })
                ProfileRow(Icons.AutoMirrored.Rounded.Message, Str.messages_auto_item, Str.messages_auto_subtitle, Color(0xFFF59E0B),
                    onClick = { onIntent(SettingsIntent.OnMessagesClick) })
                ProfileRow(Icons.Rounded.Sms, Str.sms_report_title, Str.sms_report_settings_subtitle, Color(0xFFF59E0B),
                    onClick = { onIntent(SettingsIntent.OnSmsReportClick) })
                ProfileRow(Icons.Rounded.Notifications, Str.reminders_notifications_item, Str.reminders_notifications_subtitle, Color(0xFFF59E0B),
                    onClick = { onIntent(SettingsIntent.OnNotificationsClick) }, last = true)
            }

            ProfileSection(Str.profile_group_social, Icons.Rounded.Groups, Color(0xFFEC4899)) {
                ProfileRow(Icons.Rounded.Groups, Str.followers_list_title, Str.followers_settings_subtitle, Color(0xFFEC4899),
                    onClick = { showFollowers = true })
                ProfileRow(Icons.Rounded.AutoStories, Str.stories_title, Str.stories_subtitle, Color(0xFFEC4899),
                    onClick = onStories)
                ProfileRow(Icons.Rounded.LocalOffer, Str.first_visit_title, Str.first_visit_settings_subtitle, Color(0xFFEC4899),
                    onClick = { showFirstVisit = true })
                ProfileRow(Icons.Rounded.RateReview, Str.reviews_title, Str.reviews_subtitle, Color(0xFFEC4899),
                    onClick = onReviews)
                ProfileRow(Icons.Rounded.Campaign, Str.promotional_sms, Str.send_discounts_and_news_to, Color(0xFFEC4899),
                    onClick = onPromoCampaign, last = true)
            }

            ProfileSection(Str.profile_group_account, Icons.Rounded.AccountCircle, Color(0xFF64748B)) {
                ProfileRow(Icons.Rounded.AccountBalanceWallet, Str.wallet_title, Str.wallet_settings_subtitle, Color(0xFF64748B),
                    onClick = onWallet)
                ProfileRow(Icons.Rounded.Palette, Str.theme_appearance, Str.theme_settings, Color(0xFF64748B),
                    onClick = onShowThemeSheet)
                LanguageSettingsItem(profileStyle = true)
                ProfileRow(Icons.Rounded.Info, Str.about_us_label, Str.about_us_subtitle, Color(0xFF64748B),
                    onClick = { onIntent(SettingsIntent.OnAboutClick) })
                ProfileRow(Icons.AutoMirrored.Rounded.Logout, Str.logout_label, null, MaterialTheme.colorScheme.error,
                    onClick = onLogout, last = true)
            }

            ProfileSection(Str.profile_group_danger, Icons.Rounded.Warning, MaterialTheme.colorScheme.error) {
                ProfileRow(Icons.Rounded.Delete, Str.delete_business, Str.remove_this_business_from_your, MaterialTheme.colorScheme.error,
                    onClick = onShowDeleteDialog, danger = true, last = true)
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
 * The desktop page's business actions as an even grid of tiles instead of a
 * list stretched across the page. As many columns as fit at [MinTileWidth]
 * each — one row held all seven tiles once the list grew, and every title was
 * cut to «تغییر کس…» — down to a single stacked column in a small window. Two
 * subtitle lines are reserved on every tile so they all come out the same height.
 */
@Composable
private fun SettingsTiles(tiles: List<SettingsTile>) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = (maxWidth / MinTileWidth).toInt().coerceIn(1, tiles.size)
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

/** Wide enough for the longest title («تغییر کسب‌وکار») on one line. */
private val MinTileWidth = 190.dp

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
    onShowcase: () -> Unit = {},
    onStories: () -> Unit = {},
    onFollowers: () -> Unit = {},
    onFirstVisit: () -> Unit = {}
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
                icon = Icons.Rounded.AutoStories,
                title = Str.stories_title,
                subtitle = null,
                onClick = onStories,
                tint = MaterialTheme.colorScheme.onSurface
            )

            HorizontalDivider()

            SettingsItem(
                icon = Icons.Rounded.Groups,
                title = Str.followers_list_title,
                subtitle = null,
                onClick = onFollowers,
                tint = MaterialTheme.colorScheme.onSurface
            )

            HorizontalDivider()

            SettingsItem(
                icon = Icons.Rounded.LocalOffer,
                title = Str.first_visit_title,
                subtitle = null,
                onClick = onFirstVisit,
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

/** A group of profile rows under one titled card: icon chip + name on top. */
@Composable
private fun ProfileSection(
    title: String,
    icon: ImageVector,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    HomeCard(contentPadding = 0.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
        content()
    }
}

/** One tappable row: tinted icon chip, title, subtitle, chevron. */
@Composable
private fun ProfileRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    accent: Color,
    onClick: () -> Unit,
    danger: Boolean = false,
    last: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            imageVector = ForwardChevron,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
    if (!last) HorizontalDivider(
        modifier = Modifier.padding(start = 70.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
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
                                // Customers who follow the page in the client
                                // app / website. A count only: who follows is
                                // never sent to the owner.
                                Text(
                                    text = Str.followers_count(business.followersCount.toString().toPersianDigits()),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.8f)
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
private fun LanguageSettingsItem(profileStyle: Boolean = false) {
    var showPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val languageName = if (AppLocale.isEnglish) Str.language_name_en else Str.language_name_fa
    if (profileStyle) {
        ProfileRow(Icons.Rounded.Language, Str.language, languageName, Color(0xFF64748B), onClick = { showPicker = true })
    } else {
        SettingsItem(
            icon = Icons.Rounded.Language,
            title = Str.language,
            subtitle = languageName,
            onClick = { showPicker = true }
        )
    }
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
                imageVector = ForwardChevron,
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
