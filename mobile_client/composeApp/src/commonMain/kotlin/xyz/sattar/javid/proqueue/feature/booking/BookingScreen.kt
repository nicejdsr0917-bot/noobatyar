package xyz.sattar.javid.proqueue.feature.booking

import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Megaphone
import com.composables.icons.lucide.Plus
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.delay
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.feature.businessDetail.DEPOSIT_OPTIONAL
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.isoDayNumber
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.ui.components.ShimmerBox
import xyz.sattar.javid.proqueue.core.ui.components.PersianDigits
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost
import xyz.sattar.javid.proqueue.core.ui.components.UiMessage
import xyz.sattar.javid.proqueue.core.ui.components.showToasty
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.core.utils.SystemBackHandler
import xyz.sattar.javid.proqueue.core.utils.openUrl
import xyz.sattar.javid.proqueue.core.utils.rememberReducedMotion
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits
import xyz.sattar.javid.proqueue.data.remoteDataSource.booking.model.TimeSlotDto
import xyz.sattar.javid.proqueue.feature.businessDetail.TopBar
import xyz.sattar.javid.proqueue.feature.businessDetail.formatToman
import xyz.sattar.javid.proqueue.feature.discovery.BusinessAvatar
import xyz.sattar.javid.proqueue.feature.discovery.Hairline
import xyz.sattar.javid.proqueue.feature.discovery.Radius
import xyz.sattar.javid.proqueue.feature.discovery.Space
import xyz.sattar.javid.proqueue.feature.discovery.areaLine

@Composable
fun BookingScreen(
    businessId: Long,
    onNavigateBack: () -> Unit,
    onOpenMyAppointments: () -> Unit,
    resumeAppointmentId: Long = 0,
    viewModel: BookingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(businessId) { viewModel.load(businessId, resumeAppointmentId) }
    LaunchedEffect(state.released) { if (state.released) onNavigateBack() }
    var confirmRelease by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            when (msg) {
                is BookingMessage.Error -> {
                    haptic.performHapticFeedback(HapticFeedbackType.Reject)
                    snackbar.showToasty(UiMessage.error(msg.text))
                }
                is BookingMessage.OpenUrl -> openUrl(msg.url)
            }
        }
    }
    // Leaving the payment step gives the held slot back, as the web checkout's
    // close does; asked first, since it cannot be undone.
    val goBack = {
        if (state.step == BookingStep.PAYMENT) confirmRelease = true
        else if (!viewModel.back()) onNavigateBack()
    }
    // System back steps through the flow like the top bar does.
    SystemBackHandler(onBack = goBack)

    val reduced = rememberReducedMotion()
    CompositionLocalProvider(LocalReducedMotion provides reduced) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(Modifier.fillMaxSize()) {
                TopBar(title = "رزرو نوبت", showDivider = false, onBack = goBack)
                val business = state.business
                when {
                    state.isLoading -> LoadingSkeleton()
                    business == null -> Column(
                        Modifier.fillMaxSize().padding(Space.page),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(state.loadError ?: "اطلاعات مرکز در دسترس نیست", textAlign = TextAlign.Center)
                        Spacer(Modifier.height(Space.md))
                        OutlinedButton(onClick = { viewModel.load(businessId) }, shape = Radius.md) { Text("تلاش دوباره") }
                    }
                    !business.bookingEnabled -> BookingDisabled(business, onNavigateBack)
                    else -> {
                        AnimatedVisibility(
                            visible = state.step != BookingStep.DONE,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) { BusinessHeader(business) }
                        // Remembered, so the payment stage stays on the indicator while it fades out.
                        var paymentRequested by remember { mutableStateOf(false) }
                        if (state.step == BookingStep.PAYMENT) paymentRequested = true
                        val phases = bookingPhases(business, state.step, paymentRequested)
                        AnimatedVisibility(
                            visible = state.step != BookingStep.DONE,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Column {
                                StepIndicator(phases, currentPhaseIndex(phases, state.step))
                                Hairline()
                            }
                        }
                        val layoutDirection = LocalLayoutDirection.current
                        AnimatedContent(
                            targetState = state.step,
                            transitionSpec = { stepTransition(layoutDirection, reduced) },
                            modifier = Modifier.weight(1f),
                            label = "bookingStep",
                        ) { step ->
                            when (step) {
                                BookingStep.SERVICES -> ServicesStep(business, state, viewModel)
                                BookingStep.SLOT -> SlotStep(state, viewModel)
                                BookingStep.PHONE -> PhoneStep(state, viewModel)
                                BookingStep.CODE -> CodeStep(state, viewModel)
                                BookingStep.NAME -> NameStep(state, viewModel)
                                BookingStep.CONFIRM -> ConfirmStep(state, viewModel)
                                BookingStep.PAYMENT -> PaymentStep(state, viewModel)
                                BookingStep.DONE -> DoneStep(state, onOpenMyAppointments, onNavigateBack)
                            }
                        }
                    }
                }
            }
            ToastyHost(snackbar, Modifier.align(Alignment.TopCenter))
        }
    }

    if (confirmRelease) {
        AlertDialog(
            onDismissRequest = { confirmRelease = false },
            title = { Text("انصراف از این نوبت؟") },
            text = { Text("نوبت تا پرداخت بیعانه نگه داشته می‌شود؛ با خروج آزاد می‌شود.") },
            confirmButton = {
                TextButton(onClick = { confirmRelease = false; viewModel.releaseHold() }) {
                    Text("آزاد کردن نوبت", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRelease = false }) { Text("ادامه پرداخت") } }
        )
    }
}

/** The business's logo and name over the flow, as on the web's booking page, with its notice. */
@Composable
private fun BusinessHeader(business: Business) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.page, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BusinessAvatar(business, 40.dp)
            Spacer(Modifier.width(Space.md))
            Text(business.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        if (business.noticeEnabled && business.noticeMessage.isNotBlank()) {
            NoticeLine(business.noticeMessage, Modifier.padding(horizontal = Space.page).padding(bottom = Space.sm))
        }
        Hairline()
    }
}

@Composable
private fun NoticeLine(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, Radius.md)
            .padding(Space.md),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Lucide.Megaphone, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Space.sm))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

/** The web's «نوبت‌دهی غیرفعال است» screen, with the owner's own words under it. */
@Composable
private fun BookingDisabled(business: Business, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(Space.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BusinessAvatar(business, 64.dp)
        Spacer(Modifier.height(Space.lg))
        Text("نوبت‌دهی فعلا غیرفعال است", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (business.noticeEnabled && business.noticeMessage.isNotBlank()) {
            Spacer(Modifier.height(Space.md))
            NoticeLine(business.noticeMessage)
        }
        Spacer(Modifier.height(Space.xl))
        OutlinedButton(onClick = onBack, shape = Radius.md) { Text("بازگشت") }
    }
}

@Composable
private fun LoadingSkeleton() {
    Column(Modifier.fillMaxSize().padding(Space.page), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        ShimmerBox(Modifier.fillMaxWidth().height(44.dp), Radius.md)
        ShimmerBox(Modifier.width(140.dp).height(20.dp), Radius.sm)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            repeat(5) { ShimmerBox(Modifier.width(64.dp).height(84.dp), Radius.md) }
        }
        SlotGridShimmer()
    }
}

/**
 * A scrolling step body with its primary action pinned under the thumb.
 * [footer] sits just above the button, for a live summary of the pick.
 */
@Composable
internal fun StepScaffold(
    action: String?,
    actionEnabled: Boolean = true,
    busy: Boolean = false,
    onAction: () -> Unit = {},
    footer: (@Composable () -> Unit)? = null,
    scrollPadding: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = Space.lg, horizontal = if (scrollPadding) Space.page else 0.dp),
            verticalArrangement = Arrangement.spacedBy(Space.lg)
        ) { content() }
        if (action != null || footer != null) {
            Hairline()
            Column(
                Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = Space.page, vertical = Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                footer?.invoke()
                if (action != null) PrimaryAction(action, actionEnabled, busy, onAction)
            }
        }
    }
}

@Composable
internal fun StepTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.enterStagger(0)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Spacer(Modifier.height(Space.xs))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ServicesStep(business: Business, state: BookingState, vm: BookingViewModel) {
    val count = state.selectedServices.size
    // The owner's menu, plus anything typed: a typed one stays as a row so it can be un-picked.
    val rows = business.services + state.selectedServices.filter { it !in business.services }
    var custom by remember { mutableStateOf("") }
    StepScaffold(
        action = if (count == 0) "ادامه بدون انتخاب" else "ادامه با ${count.toPersianDigits()} خدمت",
        onAction = vm::continueFromServices,
    ) {
        StepTitle(
            "چه خدمتی می‌خواهید؟",
            if (business.services.isEmpty()) "خدمت موردنظرتان را بنویسید" else "می‌توانید چند خدمت انتخاب کنید",
        )
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            rows.forEachIndexed { index, service ->
                key(service) {
                    ServiceRow(
                        service,
                        selected = service in state.selectedServices,
                        onToggle = { vm.toggleService(service) },
                        modifier = Modifier.enterStagger(index + 1).animateContentSize(),
                    )
                }
            }
        }
        if (business.allowClientAddService) {
            val add = { if (vm.addCustomService(custom)) custom = "" }
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it.take(CUSTOM_SERVICE_MAX) },
                placeholder = { Text("خدمت دیگری می‌خواهید؟ بنویسید") },
                singleLine = true,
                shape = Radius.md,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                trailingIcon = {
                    IconButton(onClick = add, enabled = customServiceName(custom) != null) {
                        Icon(Lucide.Plus, contentDescription = "افزودن خدمت")
                    }
                },
                modifier = Modifier.fillMaxWidth().enterStagger(rows.size + 1),
            )
        }
    }
}

private val weekdayNames = mapOf(
    1 to "دوشنبه", 2 to "سه‌شنبه", 3 to "چهارشنبه", 4 to "پنجشنبه", 5 to "جمعه", 6 to "شنبه", 7 to "یکشنبه",
)
internal val jalaliMonths = listOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
)

/**
 * Groups of four, isolated left-to-right: in an RTL line the bidi algorithm
 * otherwise lays the space-separated groups out last-to-first, and a visitor
 * copying the number would transfer to the wrong card.
 */
fun formatCardNumber(number: String): String =
    "⁦" + number.chunked(4).joinToString(" ").toPersianDigits() + "⁩"

internal fun BookingDay.weekdayLabel(index: Int): String = when (index) {
    0 -> "امروز"
    1 -> "فردا"
    else -> weekdayNames.getValue(date.dayOfWeek.isoDayNumber)
}

internal fun BookingDay.jalaliLabel(): String {
    val j = DateTimeUtils.getJalaliDateParts(startMillis)
    return "${j.dayOfMonth.toString().toPersianDigits()} ${jalaliMonths[j.month - 1]}"
}

/** What the slot area shows; a snapshot, so the outgoing panel keeps its own slots while it fades. */
private data class SlotPanel(val kind: Kind, val day: Int, val slots: List<TimeSlotDto>) {
    enum class Kind { LOADING, CLOSED, FULL, SLOTS }
}

private fun BookingState.slotPanel(): SlotPanel = SlotPanel(
    kind = when {
        slotsLoading -> SlotPanel.Kind.LOADING
        dayClosed -> SlotPanel.Kind.CLOSED
        slots.none { it.available } -> SlotPanel.Kind.FULL
        else -> SlotPanel.Kind.SLOTS
    },
    day = selectedDay,
    slots = slots,
)

@Composable
private fun SlotStep(state: BookingState, vm: BookingViewModel) {
    val day = state.days.getOrNull(state.selectedDay)
    val slot = state.selectedSlot
    StepScaffold(
        action = "ادامه",
        actionEnabled = slot != null,
        onAction = vm::continueFromSlot,
        scrollPadding = false,
        footer = {
            AnimatedVisibility(
                visible = slot != null && day != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                if (slot != null && day != null) {
                    AnimatedContent(
                        targetState = "${day.weekdayLabel(state.selectedDay)} ${day.jalaliLabel()}، ساعت ${slot.time.toPersianDigits()}",
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "slotSummary",
                    ) { text ->
                        Text(
                            text,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
    ) {
        StepTitle("روز و ساعت", "زمان مناسب را انتخاب کنید", Modifier.padding(horizontal = Space.page))
        DayStrip(state.days, state.selectedDay, vm::selectDay)
        AnimatedContent(
            targetState = state.slotPanel(),
            contentKey = { it.kind to it.day },
            transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.page),
            label = "slotPanel",
        ) { panel ->
            when (panel.kind) {
                SlotPanel.Kind.LOADING -> SlotGridShimmer()
                SlotPanel.Kind.CLOSED -> EmptyNote("این روز تعطیل است", "روز دیگری را انتخاب کنید")
                SlotPanel.Kind.FULL -> EmptyNote("نوبت خالی نمانده است", "روزهای بعد را ببینید")
                SlotPanel.Kind.SLOTS -> SlotGrid(panel.slots, state.selectedSlot, vm::selectSlot, animationKey = panel.day)
            }
        }
    }
}

@Composable
private fun EmptyNote(title: String, subtitle: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, Radius.md)
            .padding(vertical = Space.xl, horizontal = Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Space.xs))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Focuses the step's field once it has slid in, so the keyboard comes up by itself. */
@Composable
internal fun rememberAutoFocus(): FocusRequester {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    return focus
}

internal val ltrInput = TextStyle(textDirection = TextDirection.Ltr, textAlign = TextAlign.Center, fontSize = 20.sp, letterSpacing = 2.sp)

@Composable
private fun PhoneStep(state: BookingState, vm: BookingViewModel) {
    val focus = rememberAutoFocus()
    val wrong = state.phone.length == 11 && !vm.isPhoneValid
    StepScaffold(action = "دریافت کد", actionEnabled = vm.isPhoneValid, busy = state.busy, onAction = vm::sendOtp) {
        StepTitle("شماره موبایل", "یک بار وارد شوید؛ کد تایید پیامک می‌شود")
        OutlinedTextField(
            value = state.phone,
            onValueChange = vm::setPhone,
            placeholder = { Text("۰۹۱۲ ۰۰۰ ۰۰۰۰", style = ltrInput, modifier = Modifier.fillMaxWidth()) },
            singleLine = true,
            isError = wrong,
            supportingText = if (wrong) ({ Text("شماره باید با ۰۹ شروع شود") }) else null,
            textStyle = ltrInput,
            visualTransformation = PersianDigits,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.sendOtp() }),
            shape = Radius.md,
            modifier = Modifier.fillMaxWidth().focusRequester(focus).enterStagger(1)
        )
    }
}

@Composable
private fun CodeStep(state: BookingState, vm: BookingViewModel) {
    val focus = rememberAutoFocus()
    StepScaffold(action = "تایید", actionEnabled = state.code.length >= 4, busy = state.busy, onAction = vm::verifyOtp) {
        StepTitle("کد تایید", "کد پیامک‌شده به ${state.phone.toPersianDigits()}")
        OutlinedTextField(
            value = state.code,
            onValueChange = vm::setCode,
            singleLine = true,
            textStyle = ltrInput.copy(fontSize = 26.sp, letterSpacing = 10.sp, fontWeight = FontWeight.Bold),
            visualTransformation = PersianDigits,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.verifyOtp() }),
            shape = Radius.md,
            modifier = Modifier.fillMaxWidth().focusRequester(focus).enterStagger(1)
        )
        Row(Modifier.enterStagger(2), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::sendOtp, enabled = !state.busy) { Text("ارسال دوباره") }
            TextButton(onClick = { vm.back() }, enabled = !state.busy) { Text("تغییر شماره") }
        }
    }
}

@Composable
private fun NameStep(state: BookingState, vm: BookingViewModel) {
    val focus = rememberAutoFocus()
    StepScaffold(action = "ادامه", actionEnabled = state.name.isNotBlank(), busy = state.busy, onAction = vm::register) {
        StepTitle("نام شما", "مرکز با این نام نوبت را می‌بیند")
        OutlinedTextField(
            value = state.name,
            onValueChange = vm::setName,
            singleLine = true,
            placeholder = { Text("نام و نام خانوادگی") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.register() }),
            shape = Radius.md,
            modifier = Modifier.fillMaxWidth().focusRequester(focus).enterStagger(1)
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

/** The booking at a glance: who, when, what, and any deposit due. */
@Composable
private fun SummaryCard(state: BookingState, modifier: Modifier = Modifier, showDeposit: Boolean = true) {
    val business = state.business ?: return
    val day = state.days.getOrNull(state.selectedDay)
    Surface(
        shape = Radius.md,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Row(Modifier.enterStagger(1), verticalAlignment = Alignment.CenterVertically) {
                BusinessAvatar(business, 44.dp)
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text(business.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                    val area = business.areaLine()
                    if (area.isNotBlank()) {
                        Text(area, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            val heldAt = state.heldAt
            if (state.selectedSlot == null && heldAt != null) {
                // A hold resumed from «نوبت‌های من»: its time comes from the appointment.
                Row(Modifier.enterStagger(2), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    SummaryTile("روز", DateTimeUtils.getJalaliDate(heldAt).toPersianDigits(), Modifier.weight(1.6f))
                    SummaryTile("ساعت", DateTimeUtils.formatTime(heldAt).toPersianDigits(), Modifier.weight(1f))
                }
            } else Row(Modifier.enterStagger(2), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                if (day != null) {
                    SummaryTile("روز", "${day.weekdayLabel(state.selectedDay)} ${day.jalaliLabel()}", Modifier.weight(1.6f))
                }
                state.selectedSlot?.let { SummaryTile("ساعت", it.time.toPersianDigits(), Modifier.weight(1f)) }
            }
            if (state.selectedServices.isNotEmpty()) {
                Column(Modifier.enterStagger(3)) {
                    Text("خدمات", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Space.xs))
                    Text(state.selectedServices.joinToString("، "), style = MaterialTheme.typography.bodyMedium)
                }
            }
            val deposit = business.depositAmount
            if (showDeposit && business.expectsDeposit() && deposit != null) {
                Hairline(Modifier.enterStagger(4))
                Column(Modifier.enterStagger(4)) {
                    SummaryRow("بیعانه", formatToman(deposit))
                    Text(
                        "با پرداخت بیعانه قطعی می‌شود",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmStep(state: BookingState, vm: BookingViewModel) {
    StepScaffold(action = "ثبت نوبت", busy = state.busy, onAction = vm::confirm) {
        StepTitle("تایید نوبت", "اطلاعات را بررسی کنید")
        SummaryCard(state)
        TextButton(onClick = { vm.back() }, enabled = !state.busy, modifier = Modifier.enterStagger(5)) { Text("تغییر روز یا ساعت") }
        OutlinedTextField(
            value = state.description,
            onValueChange = vm::setDescription,
            label = { Text("توضیح برای مرکز (اختیاری)") },
            minLines = 2,
            shape = Radius.md,
            modifier = Modifier.fillMaxWidth().enterStagger(6)
        )
    }
}

@Composable
private fun PaymentStep(state: BookingState, vm: BookingViewModel) {
    val business = state.business ?: return
    var reference by remember { mutableStateOf("") }
    var linkReference by remember { mutableStateOf("") }
    var receipt by remember { mutableStateOf<ByteArray?>(null) }
    val options = paymentOptions(state.paymentMethods, business.depositMode, business.onlineGatewayEnabled, business.paymentLink)
    var picked by remember(options) { mutableStateOf(options.firstOrNull()) }
    val linkOnly = !business.onlineGatewayEnabled
    val (action, enabled, onAction) = when (picked) {
        PaymentOption.ONLINE -> if (linkOnly) {
            Triple("ثبت پرداخت", linkReference.isNotBlank(), { vm.payByLink(linkReference) })
        } else {
            Triple("پرداخت با درگاه بانکی", true, vm::payOnline)
        }
        PaymentOption.CARD -> Triple(
            "ثبت واریز",
            state.cardNumber.isNotBlank() && canSubmitCardPayment(reference, receipt != null),
            { vm.payByCard(reference, receipt) },
        )
        PaymentOption.CASH -> Triple("ثبت نوبت با پرداخت حضوری", true, vm::payCash)
        null -> Triple(null, false, {})
    }
    StepScaffold(action = action, actionEnabled = enabled, busy = state.busy, onAction = onAction) {
        StepTitle("پرداخت بیعانه", "نوبت نگه داشته شد؛ با بیعانه قطعی می‌شود")
        business.depositAmount?.takeIf { it > 0 }?.let { amount ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .enterStagger(1)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, Radius.md)
                    .padding(Space.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("مبلغ بیعانه", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Space.xs))
                Text(formatToman(amount), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (business.depositMode == DEPOSIT_OPTIONAL) {
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        "بیعانه اختیاری است؛ حضوری هم می‌شود",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } ?: if (business.depositMode == DEPOSIT_OPTIONAL) {
            Text("مبلغ را با مرکز هماهنگ کنید", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else Unit
        if (options.isEmpty()) {
            EmptyNote("روش پرداختی ثبت نشده است", "وضعیت در «نوبت‌های من»")
        }
        options.forEachIndexed { index, option ->
            val selected = option == picked
            val onSelect = { picked = option }
            val mod = Modifier.enterStagger(index + 2)
            when (option) {
                PaymentOption.ONLINE -> if (linkOnly) {
                    PaymentOptionCard("پرداخت آنلاین", "از صفحه پرداخت مرکز، سپس شماره پیگیری", selected, onSelect, mod) {
                        val url = paymentLinkUrl(business.paymentLink)
                        if (url != null) {
                            OutlinedButton(onClick = { openUrl(url) }, shape = Radius.md, modifier = Modifier.fillMaxWidth()) {
                                Text("رفتن به صفحه پرداخت")
                            }
                        }
                        OutlinedTextField(
                            value = linkReference,
                            onValueChange = { linkReference = it.normalizeDigits().filter(Char::isDigit).take(64) },
                            label = { Text("شماره پیگیری پرداخت") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = Radius.md,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    PaymentOptionCard("پرداخت آنلاین", "درگاه بانکی؛ بعد از پرداخت قطعی می‌شود", selected, onSelect, mod)
                }
                PaymentOption.CASH -> PaymentOptionCard(
                    "پرداخت حضوری", "نوبت برای تایید مرکز ارسال می‌شود", selected, onSelect, mod,
                )
                PaymentOption.CARD -> PaymentOptionCard("کارت به کارت", "واریز، سپس شماره پیگیری یا عکس رسید", selected, onSelect, mod) {
                    if (state.cardNumber.isBlank()) {
                        Text(
                            "کارتی ثبت نشده؛ روش دیگری انتخاب کنید",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        CardNumberRow(state.cardNumber)
                        SummaryRow("به نام", state.cardOwnerName.ifBlank { "صاحب کسب‌وکار" })
                        OutlinedTextField(
                            value = reference,
                            onValueChange = { reference = it.take(64) },
                            label = { Text(if (receipt == null) "شماره پیگیری واریز" else "شماره پیگیری (اختیاری)") },
                            singleLine = true,
                            enabled = state.uploadProgress == null,
                            shape = Radius.md,
                            modifier = Modifier.fillMaxWidth()
                        )
                        ReceiptPicker(
                            receipt = receipt,
                            onPick = { receipt = it },
                            onRemove = { receipt = null },
                            progress = state.uploadProgress,
                        )
                    }
                }
            }
        }
    }
}

/** The destination card with a copy button that confirms itself, as on the web. */
@Suppress("DEPRECATION")
@Composable
private fun CardNumberRow(number: String) {
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1800); copied = false } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("شماره کارت", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatCardNumber(number), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = {
            clipboard.setText(AnnotatedString(number))
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            copied = true
        }) {
            AnimatedContent(targetState = copied, label = "copyCard") { done ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (done) Lucide.Check else Lucide.Copy, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (done) "کپی شد" else "کپی")
                }
            }
        }
    }
}

@Composable
private fun DoneStep(state: BookingState, onOpenMyAppointments: () -> Unit, onClose: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var settled by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(Space.page),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Space.xl))
            AnimatedCheck(onSettled = {
                if (!settled) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                settled = true
            })
            Text(
                "نوبت شما ثبت شد",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.enterStagger(6, stepMillis = 60),
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                when {
                    state.paidOnline -> "نتیجه پرداخت در «نوبت‌های من» نمایش داده می‌شود"
                    state.proofSent -> "پرداخت ثبت شد؛ با تایید مرکز قطعی می‌شود"
                    else -> "درخواست ارسال شد؛ وضعیت در «نوبت‌های من»"
                },
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.enterStagger(7, stepMillis = 60),
            )
            Spacer(Modifier.height(Space.xl))
            SummaryCard(state, Modifier.enterStagger(8, stepMillis = 60), showDeposit = false)
        }
        Hairline()
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = Space.page, vertical = Space.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PrimaryAction("نوبت‌های من", enabled = true, busy = false, onClick = onOpenMyAppointments)
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("بازگشت به صفحه مرکز") }
        }
    }
}
