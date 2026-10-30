package xyz.sattar.javid.proqueue.feature.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.isoDayNumber
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost
import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
import xyz.sattar.javid.proqueue.core.ui.components.showToasty
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.core.utils.SystemBackHandler
import xyz.sattar.javid.proqueue.core.utils.openUrl
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.TimeSlotDto
import xyz.sattar.javid.proqueue.feature.businessDetail.TopBar
import xyz.sattar.javid.proqueue.feature.discovery.Hairline
import xyz.sattar.javid.proqueue.feature.discovery.Radius
import xyz.sattar.javid.proqueue.feature.discovery.Space

@Composable
fun BookingScreen(
    businessId: Long,
    onNavigateBack: () -> Unit,
    onOpenMyAppointments: () -> Unit,
    viewModel: BookingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(businessId) { viewModel.load(businessId) }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            when (msg) {
                is BookingMessage.Error -> snackbar.showToasty(UiMessage.error(msg.text))
                is BookingMessage.OpenUrl -> openUrl(msg.url)
            }
        }
    }
    val goBack = { if (!viewModel.back()) onNavigateBack() }
    // System back steps through the flow like the top bar does.
    SystemBackHandler(onBack = goBack)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(title = state.business?.title ?: "رزرو نوبت", showDivider = true, onBack = goBack)
            val business = state.business
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                business == null -> Column(
                    Modifier.fillMaxSize().padding(Space.page),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.loadError ?: "اطلاعات مرکز در دسترس نیست", textAlign = TextAlign.Center)
                    Spacer(Modifier.height(Space.md))
                    OutlinedButton(onClick = { viewModel.load(businessId) }) { Text("تلاش دوباره") }
                }
                else -> when (state.step) {
                    BookingStep.SERVICES -> ServicesStep(business.services, state, viewModel)
                    BookingStep.SLOT -> SlotStep(state, viewModel)
                    BookingStep.PHONE -> PhoneStep(state, viewModel)
                    BookingStep.CODE -> CodeStep(state, viewModel)
                    BookingStep.NAME -> NameStep(state, viewModel)
                    BookingStep.CONFIRM -> ConfirmStep(state, viewModel)
                    BookingStep.PAYMENT -> PaymentStep(state, viewModel)
                    BookingStep.DONE -> DoneStep(state, onOpenMyAppointments)
                }
            }
        }
        ToastyHost(snackbar, Modifier.align(Alignment.TopCenter))
    }
}

/** A scrolling step body with its primary action pinned under the thumb. */
@Composable
private fun StepScaffold(
    action: String?,
    actionEnabled: Boolean = true,
    busy: Boolean = false,
    onAction: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(Space.page),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) { content() }
        if (action != null) {
            Hairline()
            Button(
                onClick = onAction,
                enabled = actionEnabled && !busy,
                shape = Radius.md,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = Space.page, vertical = Space.md)
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(action, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StepTitle(title: String, subtitle: String? = null) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Spacer(Modifier.height(Space.xs))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServicesStep(services: List<String>, state: BookingState, vm: BookingViewModel) {
    StepScaffold(action = if (state.selectedServices.isEmpty()) "ادامه بدون انتخاب" else "ادامه", onAction = vm::continueFromServices) {
        StepTitle("چه خدمتی می‌خواهید؟", "می‌توانید چند خدمت انتخاب کنید")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            services.forEach { service ->
                FilterChip(
                    selected = service in state.selectedServices,
                    onClick = { vm.toggleService(service) },
                    label = { Text(service) }
                )
            }
        }
    }
}

private val weekdayNames = mapOf(
    1 to "دوشنبه", 2 to "سه‌شنبه", 3 to "چهارشنبه", 4 to "پنجشنبه", 5 to "جمعه", 6 to "شنبه", 7 to "یکشنبه",
)
private val jalaliMonths = listOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
)

internal fun BookingDay.weekdayLabel(index: Int): String = when (index) {
    0 -> "امروز"
    1 -> "فردا"
    else -> weekdayNames.getValue(date.dayOfWeek.isoDayNumber)
}

internal fun BookingDay.jalaliLabel(): String {
    val j = DateTimeUtils.getJalaliDateParts(startMillis)
    return "${j.dayOfMonth.toString().toPersianDigits()} ${jalaliMonths[j.month - 1]}"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlotStep(state: BookingState, vm: BookingViewModel) {
    StepScaffold(action = "ادامه", actionEnabled = state.selectedSlot != null, onAction = vm::continueFromSlot) {
        StepTitle("روز و ساعت")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            state.days.forEachIndexed { index, day ->
                val selected = index == state.selectedDay
                Surface(
                    onClick = { vm.selectDay(index) },
                    shape = Radius.md,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(76.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, Radius.md)
                ) {
                    Column(Modifier.padding(vertical = Space.sm), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(day.weekdayLabel(index), style = MaterialTheme.typography.labelMedium)
                        Text(day.jalaliLabel(), style = MaterialTheme.typography.bodySmall)
                        if (day.closed) Text("تعطیل", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        when {
            state.slotsLoading -> Box(Modifier.fillMaxWidth().padding(Space.xl), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.dayClosed -> Text("این روز تعطیل است؛ روز دیگری انتخاب کنید", color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.slots.none { it.available } -> Text("برای این روز نوبت خالی نمانده است", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                state.slots.forEach { slot -> SlotChip(slot, selected = slot == state.selectedSlot) { vm.selectSlot(slot) } }
            }
        }
    }
}

@Composable
private fun SlotChip(slot: TimeSlotDto, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        enabled = slot.available,
        onClick = onClick,
        label = { Text(slot.time.toPersianDigits()) }
    )
}

@Composable
private fun PhoneStep(state: BookingState, vm: BookingViewModel) {
    StepScaffold(action = "دریافت کد", actionEnabled = vm.isPhoneValid, busy = state.busy, onAction = vm::sendOtp) {
        StepTitle("شمارهٔ موبایل", "برای ثبت نوبت یک بار وارد می‌شوید؛ کد تأیید پیامک می‌شود")
        OutlinedTextField(
            value = state.phone,
            onValueChange = vm::setPhone,
            label = { Text("۰۹۱۲…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = Radius.md,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CodeStep(state: BookingState, vm: BookingViewModel) {
    StepScaffold(action = "تأیید", actionEnabled = state.code.length >= 4, busy = state.busy, onAction = vm::verifyOtp) {
        StepTitle("کد تأیید", "کدی که به ${state.phone.toPersianDigits()} پیامک شد")
        OutlinedTextField(
            value = state.code,
            onValueChange = vm::setCode,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            shape = Radius.md,
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(onClick = vm::sendOtp, enabled = !state.busy) { Text("ارسال دوبارهٔ کد") }
    }
}

@Composable
private fun NameStep(state: BookingState, vm: BookingViewModel) {
    StepScaffold(action = "ادامه", actionEnabled = state.name.isNotBlank(), busy = state.busy, onAction = vm::register) {
        StepTitle("نام شما", "مرکز با این نام نوبت را می‌بیند")
        OutlinedTextField(
            value = state.name,
            onValueChange = vm::setName,
            singleLine = true,
            shape = Radius.md,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Space.md))
        Text(value, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
    }
}

@Composable
private fun ConfirmStep(state: BookingState, vm: BookingViewModel) {
    val business = state.business ?: return
    val day = state.days.getOrNull(state.selectedDay)
    StepScaffold(action = "ثبت نوبت", busy = state.busy, onAction = vm::confirm) {
        StepTitle("تأیید نوبت")
        SummaryRow("مرکز", business.title)
        if (day != null) SummaryRow("روز", "${day.weekdayLabel(state.selectedDay)} ${day.jalaliLabel()}")
        state.selectedSlot?.let { SummaryRow("ساعت", it.time.toPersianDigits()) }
        if (state.selectedServices.isNotEmpty()) SummaryRow("خدمات", state.selectedServices.joinToString("، "))
        val deposit = business.depositAmount
        if (business.depositMode == "REQUIRED" && deposit != null && deposit > 0) {
            SummaryRow("بیعانه", "${deposit.toString().toPersianDigits()} تومان")
        }
        OutlinedTextField(
            value = state.description,
            onValueChange = vm::setDescription,
            label = { Text("توضیح برای مرکز (اختیاری)") },
            minLines = 2,
            shape = Radius.md,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PaymentStep(state: BookingState, vm: BookingViewModel) {
    var reference by remember { mutableStateOf("") }
    StepScaffold(action = null) {
        StepTitle("پرداخت بیعانه", "نوبت برای شما نگه داشته شده؛ با پرداخت بیعانه قطعی می‌شود")
        val methods = state.paymentMethods
        if ("ONLINE" in methods) {
            Button(onClick = vm::payOnline, enabled = !state.busy, shape = Radius.md, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("پرداخت آنلاین", fontWeight = FontWeight.Bold)
            }
        }
        if ("CARD" in methods && state.cardNumber.isNotBlank()) {
            Hairline()
            Text("کارت به کارت", fontWeight = FontWeight.Bold)
            SummaryRow("شماره کارت", state.cardNumber.chunked(4).joinToString(" ").toPersianDigits())
            if (state.cardOwnerName.isNotBlank()) SummaryRow("به نام", state.cardOwnerName)
            OutlinedTextField(
                value = reference,
                onValueChange = { reference = it.take(64) },
                label = { Text("شمارهٔ پیگیری واریز") },
                singleLine = true,
                shape = Radius.md,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                onClick = { vm.payByCard(reference) },
                enabled = reference.isNotBlank() && !state.busy,
                shape = Radius.md,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("ثبت واریز") }
        }
        if ("CASH" in methods) {
            Hairline()
            OutlinedButton(onClick = vm::payCash, enabled = !state.busy, shape = Radius.md, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("پرداخت حضوری")
            }
        }
    }
}

@Composable
private fun DoneStep(state: BookingState, onOpenMyAppointments: () -> Unit) {
    StepScaffold(action = "نوبت‌های من", onAction = onOpenMyAppointments) {
        Column(Modifier.fillMaxWidth().padding(top = Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(Space.md))
            Text("نوبت شما ثبت شد", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Space.sm))
            Text(
                if (state.paidOnline) "بعد از پرداخت در درگاه، نتیجه در «نوبت‌های من» نمایش داده می‌شود"
                else "وضعیت نوبت را در «نوبت‌های من» ببینید",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
