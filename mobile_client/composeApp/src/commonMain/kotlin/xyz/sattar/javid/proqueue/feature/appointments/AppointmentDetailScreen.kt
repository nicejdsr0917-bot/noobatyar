package xyz.sattar.javid.proqueue.feature.appointments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.utils.AppConfig
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost
import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
import xyz.sattar.javid.proqueue.core.ui.components.showToasty
import xyz.sattar.javid.proqueue.core.utils.openMap
import xyz.sattar.javid.proqueue.core.utils.openPhoneDial
import xyz.sattar.javid.proqueue.core.utils.rememberReducedMotion
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.ClientAppointmentDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.toDomain
import xyz.sattar.javid.proqueue.feature.booking.LocalReducedMotion
import xyz.sattar.javid.proqueue.feature.booking.enterStagger
import xyz.sattar.javid.proqueue.feature.businessDetail.TopBar
import xyz.sattar.javid.proqueue.feature.discovery.BusinessAvatar
import xyz.sattar.javid.proqueue.feature.discovery.Hairline
import xyz.sattar.javid.proqueue.feature.discovery.Radius
import xyz.sattar.javid.proqueue.feature.discovery.Space
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits
import kotlin.time.Clock

/** A media path from the API, made absolute the way business logos are. */
internal fun mediaUrl(path: String?): String? = path?.takeIf { it.isNotBlank() }?.let {
    if (it.startsWith("http")) it else "${AppConfig.baseUrl}$it"
}

/**
 * The appointment slip («فیش نوبت»): the web's appointment page, plus the
 * services, note and the payment proof the visitor sent.
 */
@Composable
fun AppointmentDetailScreen(
    appointmentId: Long,
    onNavigateBack: () -> Unit,
    onOpenBusiness: (Long) -> Unit,
    onCompletePayment: (businessId: Long, appointmentId: Long) -> Unit,
    viewModel: AppointmentDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var confirmCancel by remember { mutableStateOf(false) }
    var zoomReceipt by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(appointmentId) { viewModel.load(appointmentId) }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            when (msg) {
                is AppointmentsMessage.Info -> snackbar.showToasty(UiMessage.success(msg.text))
                is AppointmentsMessage.Error -> snackbar.showToasty(UiMessage.error(msg.text))
                is AppointmentsMessage.OpenUrl -> Unit
            }
        }
    }

    CompositionLocalProvider(LocalReducedMotion provides rememberReducedMotion()) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(Modifier.fillMaxSize()) {
                TopBar(title = "جزئیات نوبت", showDivider = true, onBack = onNavigateBack)
                val apt = state.appointment
                when {
                    state.isLoading && apt == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    apt == null -> Message(state.errorMessage ?: "نوبت پیدا نشد")
                    else -> Slip(
                        apt,
                        cancelling = state.cancelling,
                        onCompletePayment = { onCompletePayment(apt.business.id, apt.id) },
                        onOpenBusiness = { onOpenBusiness(apt.business.id) },
                        onCancel = { confirmCancel = true },
                        onZoomReceipt = { zoomReceipt = it },
                    )
                }
            }
            ToastyHost(snackbar, Modifier.align(Alignment.TopCenter))
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("لغو نوبت") },
            text = { Text("این نوبت لغو شود؟ این کار برگشت‌پذیر نیست.") },
            confirmButton = {
                TextButton(onClick = { confirmCancel = false; viewModel.cancel() }) {
                    Text("بله، لغو شود", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("انصراف") } }
        )
    }

    zoomReceipt?.let { url ->
        Dialog(onDismissRequest = { zoomReceipt = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.9f)).clickable { zoomReceipt = null },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(model = url, contentDescription = "عکس رسید", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Section(title: String, index: Int, content: @Composable () -> Unit) {
    Surface(
        shape = Radius.md,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().enterStagger(index),
    ) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun Field(label: String, value: String, ltr: Boolean = false, accent: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Space.md))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content),
            fontWeight = FontWeight.Bold,
            color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun Slip(
    apt: ClientAppointmentDto,
    cancelling: Boolean,
    onCompletePayment: () -> Unit,
    onOpenBusiness: () -> Unit,
    onCancel: () -> Unit,
    onZoomReceipt: (String) -> Unit,
) {
    val business = remember(apt.business) { apt.business.toDomain() }
    val now = Clock.System.now().toEpochMilliseconds()
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(Space.page),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            // Header: the business's own logo, as on the booking pages.
            Column(
                Modifier.fillMaxWidth().enterStagger(0),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                BusinessAvatar(business, 76.dp)
                Text(business.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                StatusChip(apt.status)
                if (apt.status == "WAITING") {
                    val ahead = apt.queuePosition ?: 0
                    Text(
                        if (ahead == 0) "نوبت شماست" else "${ahead.toPersianDigits()} نفر جلوی شما هستند",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Section("اطلاعات نوبت", 1) {
                Field("زمان", apt.dateLine())
                Field("کد پیگیری", "#${apt.id.toString().toPersianDigits()}", ltr = true)
                if (showsCountdown(apt.status)) Field("مانده تا نوبت", timeLeftLabel(now, apt.appointmentDate))
                if (apt.selectedServices.isNotEmpty()) Field("خدمات", apt.selectedServices.joinToString("، "))
                apt.description?.takeIf { it.isNotBlank() }?.let { Field("توضیح", it) }
                if (apt.reminderPushSent || apt.reminderSmsSent) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        if (apt.reminderPushSent) InfoChip(Icons.Outlined.NotificationsActive, "اعلان یادآوری فرستاده شد")
                        if (apt.reminderSmsSent) InfoChip(Icons.Outlined.Sms, "پیامک یادآوری فرستاده شد")
                    }
                }
            }

            val method = depositMethodLabel(apt.depositPaymentMethod)
            val reference = apt.paymentReference?.takeIf { it.isNotBlank() }
            val receipt = mediaUrl(apt.paymentReceipt)
            if (method != null || reference != null || receipt != null) {
                Section("پرداخت بیعانه", 2) {
                    method?.let { Field("روش", it) }
                    reference?.let { Field("شمارهٔ پیگیری", it.toPersianDigits(), ltr = true) }
                    if (receipt != null) {
                        Text("عکس رسید", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AnimatedVisibility(visible = true, enter = fadeIn() + scaleIn(initialScale = 0.96f), exit = fadeOut()) {
                            AsyncImage(
                                model = receipt,
                                contentDescription = "عکس رسید",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(4f / 3f)
                                    .clip(Radius.md)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable { onZoomReceipt(receipt) },
                            )
                        }
                    }
                }
            }

            Section("اطلاعات تماس", 3) {
                val address = apt.business.address?.takeIf { it.isNotBlank() }
                val lat = business.latitude
                val lng = business.longitude
                Field(
                    "آدرس",
                    address ?: "ثبت نشده",
                    accent = address != null && lat != null && lng != null,
                    onClick = if (lat != null && lng != null) ({ openMap(lat, lng, business.title) }) else null,
                )
                val phone = apt.business.phone?.takeIf { it.isNotBlank() }
                Field(
                    "تلفن",
                    phone?.toPersianDigits() ?: "ثبت نشده",
                    ltr = phone != null,
                    accent = phone != null,
                    onClick = phone?.let { p -> { openPhoneDial(p) } },
                )
            }
        }
        Hairline()
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = Space.page, vertical = Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            if (apt.status == "LOCKED") {
                Button(onClick = onCompletePayment, shape = Radius.md, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("تکمیل پرداخت", fontWeight = FontWeight.Bold)
                }
            }
            OutlinedButton(onClick = onOpenBusiness, shape = Radius.md, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("صفحهٔ مرکز")
            }
            if (apt.canCancelAt(now)) {
                TextButton(onClick = onCancel, enabled = !cancelling, modifier = Modifier.fillMaxWidth()) {
                    Text(if (cancelling) "در حال لغو…" else "لغو نوبت", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
