package xyz.sattar.javid.proqueue.feature.appointments

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.ui.components.PullToRefreshBox
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost
import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
import xyz.sattar.javid.proqueue.core.ui.components.showToasty
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.core.utils.openUrl
import xyz.sattar.javid.proqueue.core.utils.rememberReducedMotion
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.ClientAppointmentDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.toDomain
import xyz.sattar.javid.proqueue.feature.booking.LocalReducedMotion
import xyz.sattar.javid.proqueue.feature.booking.enterStagger
import xyz.sattar.javid.proqueue.feature.booking.pressScale
import xyz.sattar.javid.proqueue.feature.discovery.BusinessAvatar
import xyz.sattar.javid.proqueue.feature.discovery.Hairline
import xyz.sattar.javid.proqueue.ui.theme.LocalIsDarkTheme
import xyz.sattar.javid.proqueue.feature.discovery.Radius
import xyz.sattar.javid.proqueue.feature.discovery.Space
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits
import kotlin.time.Clock

@Composable
fun MyAppointmentsScreen(
    onOpenBusiness: (Long) -> Unit,
    bottomPadding: Dp,
    onOpenAppointment: (Long) -> Unit = {},
    onCompletePayment: (businessId: Long, appointmentId: Long) -> Unit = { _, _ -> },
    viewModel: MyAppointmentsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(AppointmentTab.UPCOMING.name) }
    val reduced = rememberReducedMotion()

    // Also on coming back from the bank gateway, whose result lands server-side.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            when (msg) {
                is AppointmentsMessage.Info -> snackbar.showToasty(UiMessage.success(msg.text))
                is AppointmentsMessage.Error -> snackbar.showToasty(UiMessage.error(msg.text))
                is AppointmentsMessage.OpenUrl -> openUrl(msg.url)
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalReducedMotion provides reduced) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("نوبت‌های من", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (state.isSignedIn) TextButton(onClick = viewModel::signOut) { Text("خروج") }
                }
                if (state.isSignedIn) {
                    TabSwitch(AppointmentTab.valueOf(tab)) { tab = it.name }
                }
                Hairline()
                when {
                    !state.isSignedIn -> Message("هنوز نوبتی نگرفته‌اید. از صفحهٔ هر مرکز «رزرو نوبت» را بزنید؛ نوبت‌هایتان اینجا می‌آید.")
                    state.isLoading && state.appointments.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    state.errorMessage != null && state.appointments.isEmpty() -> Column(
                        Modifier.fillMaxSize().padding(Space.xl),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(state.errorMessage.orEmpty(), textAlign = TextAlign.Center)
                        OutlinedButton(onClick = viewModel::refresh) { Text("تلاش دوباره") }
                    }
                    else -> PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = viewModel::refresh) {
                        val current = AppointmentTab.valueOf(tab)
                        AnimatedContent(
                            targetState = current,
                            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                            label = "appointmentsTab",
                        ) { shownTab ->
                            val shown = state.appointments.filter { tabOf(it.status) == shownTab }
                            if (shown.isEmpty()) {
                                Message("نوبتی در این بخش ندارید")
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(start = Space.page, end = Space.page, top = Space.md, bottom = bottomPadding + Space.md),
                                    verticalArrangement = Arrangement.spacedBy(Space.md),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    itemsIndexed(shown, key = { _, a -> a.id }) { index, apt ->
                                        AppointmentCard(
                                            apt = apt,
                                            modifier = Modifier.enterStagger(index, key = shownTab),
                                            onOpen = { onOpenAppointment(apt.id) },
                                            onCompletePayment = { onCompletePayment(apt.business.id, apt.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            ToastyHost(snackbar, Modifier.align(Alignment.TopCenter))
        }
    }
}

/** Upcoming / past / cancelled, as on the web; the picked segment slides its fill. */
@Composable
private fun TabSwitch(selected: AppointmentTab, onSelect: (AppointmentTab) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.page)
            .padding(bottom = Space.md)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, Radius.md)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AppointmentTab.entries.forEach { t ->
            val isSelected = t == selected
            val bg by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent, label = "tabBg")
            val fg by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "tabFg",
            )
            Surface(
                onClick = {
                    if (!isSelected) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelect(t)
                },
                shape = Radius.sm,
                color = bg,
                modifier = Modifier.weight(1f).height(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(t.label, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
internal fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(Space.xl), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Status pill; violet for the in-between states, the semantic colours otherwise. */
@Composable
internal fun StatusChip(status: String) {
    val dark = LocalIsDarkTheme.current
    val color = when (status) {
        "CONFIRMED", "COMPLETED", "WAITING", "IN_PROGRESS" -> if (dark) Color(0xFF34D399) else Color(0xFF059669)
        "CANCELLED", "NO_SHOW" -> MaterialTheme.colorScheme.error
        "LOCKED" -> if (dark) Color(0xFFFBBF24) else Color(0xFFB45309)
        else -> MaterialTheme.colorScheme.primary
    }
    Text(
        statusLabel(status),
        color = color,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
internal fun InfoChip(icon: ImageVector, text: String) {
    Row(
        Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun ClientAppointmentDto.dateLine(): String =
    "${DateTimeUtils.getJalaliDate(appointmentDate).toPersianDigits()} · ساعت ${DateTimeUtils.formatTime(appointmentDate).toPersianDigits()}"

@Composable
private fun AppointmentCard(
    apt: ClientAppointmentDto,
    modifier: Modifier,
    onOpen: () -> Unit,
    onCompletePayment: () -> Unit,
) {
    val business = remember(apt.business) { apt.business.toDomain() }
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onOpen,
        shape = Radius.md,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth().pressScale(interaction, 0.98f),
    ) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BusinessAvatar(business, 44.dp)
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text(business.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.height(2.dp))
                    StatusChip(apt.status)
                }
            }
            Column {
                Text("زمان نوبت", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(apt.dateLine(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            val now = Clock.System.now().toEpochMilliseconds()
            if (showsCountdown(apt.status) || apt.reminderPushSent || apt.reminderSmsSent) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    if (showsCountdown(apt.status)) InfoChip(Icons.Outlined.Schedule, timeLeftLabel(now, apt.appointmentDate))
                    if (apt.reminderPushSent) InfoChip(Icons.Outlined.NotificationsActive, "یادآور اعلان")
                    if (apt.reminderSmsSent) InfoChip(Icons.Outlined.Sms, "یادآور پیامک")
                }
            }
            if (apt.status == "LOCKED") {
                OutlinedButton(onClick = onCompletePayment, shape = Radius.md, modifier = Modifier.fillMaxWidth()) {
                    Text("تکمیل پرداخت", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
