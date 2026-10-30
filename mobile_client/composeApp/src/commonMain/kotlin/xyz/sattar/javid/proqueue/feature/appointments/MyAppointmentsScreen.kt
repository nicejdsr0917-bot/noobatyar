package xyz.sattar.javid.proqueue.feature.appointments

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.ClientAppointmentDto
import xyz.sattar.javid.proqueue.feature.booking.formatCardNumber
import xyz.sattar.javid.proqueue.feature.discovery.Hairline
import xyz.sattar.javid.proqueue.feature.discovery.Space
import kotlin.time.Clock

@Composable
fun MyAppointmentsScreen(
    onOpenBusiness: (Long) -> Unit,
    bottomPadding: Dp,
    viewModel: MyAppointmentsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var confirmCancel by remember { mutableStateOf<Long?>(null) }

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

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("نوبت‌های من", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (state.isSignedIn) TextButton(onClick = viewModel::signOut) { Text("خروج") }
            }
            Hairline()
            when {
                !state.isSignedIn -> Message("هنوز نوبتی نگرفته‌اید. از صفحهٔ هر مرکز «رزرو نوبت» را بزنید؛ نوبت‌هایتان اینجا می‌آید.")
                state.isLoading && state.appointments.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.errorMessage != null && state.appointments.isEmpty() -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.errorMessage.orEmpty(), textAlign = TextAlign.Center)
                    OutlinedButton(onClick = viewModel::refresh) { Text("تلاش دوباره") }
                }
                else -> PullToRefreshBox(isRefreshing = state.isLoading, onRefresh = viewModel::refresh) {
                    if (state.appointments.isEmpty()) {
                        Message("نوبتی ندارید")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = Space.page, end = Space.page, top = Space.md, bottom = bottomPadding + Space.md),
                            verticalArrangement = Arrangement.spacedBy(Space.md),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.appointments, key = { it.id }) { apt ->
                                AppointmentCard(
                                    apt = apt,
                                    busy = state.busyId == apt.id,
                                    onOpen = { onOpenBusiness(apt.business.id) },
                                    onCancel = { confirmCancel = apt.id },
                                    onPayOnline = { viewModel.payOnline(apt.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
        ToastyHost(snackbar, Modifier.align(Alignment.TopCenter))
    }

    confirmCancel?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmCancel = null },
            title = { Text("لغو نوبت") },
            text = { Text("این نوبت لغو شود؟ این کار برگشت‌پذیر نیست.") },
            confirmButton = {
                TextButton(onClick = { confirmCancel = null; viewModel.cancel(id) }) {
                    Text("لغو نوبت", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = null }) { Text("انصراف") } }
        )
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(Space.xl), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AppointmentCard(
    apt: ClientAppointmentDto,
    busy: Boolean,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onPayOnline: () -> Unit,
) {
    val acceptsOnline = "ONLINE" in apt.business.acceptedPaymentMethods.orEmpty()
    Column(Modifier.fillMaxWidth()) {
        TextButton(onClick = onOpen, contentPadding = PaddingValues(0.dp)) {
            Text(apt.business.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Text(
            "${DateTimeUtils.getJalaliDate(apt.appointmentDate).toPersianDigits()} · ساعت ${DateTimeUtils.formatTime(apt.appointmentDate).toPersianDigits()}",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            statusLabel(apt.status),
            style = MaterialTheme.typography.labelLarge,
            color = if (apt.status == "LOCKED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
        if (apt.status == "LOCKED" && !apt.cardNumber.isNullOrBlank()) {
            Text(
                "کارت به کارت: ${formatCardNumber(apt.cardNumber)}${apt.cardOwnerName?.let { " ($it)" }.orEmpty()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            if (apt.status == "LOCKED" && acceptsOnline) {
                TextButton(onClick = onPayOnline, enabled = !busy) { Text("پرداخت آنلاین") }
            }
            if (apt.status in CANCELLABLE_STATUSES && apt.appointmentDate > Clock.System.now().toEpochMilliseconds()) {
                TextButton(onClick = onCancel, enabled = !busy) {
                    Text("لغو نوبت", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Spacer(Modifier.height(Space.sm))
        Hairline()
    }
}
