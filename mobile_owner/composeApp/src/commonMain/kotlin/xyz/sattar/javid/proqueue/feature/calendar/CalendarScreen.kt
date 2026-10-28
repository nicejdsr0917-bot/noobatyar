package xyz.sattar.javid.proqueue.feature.calendar

import xyz.sattar.javid.proqueue.core.i18n.Str
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import xyz.sattar.javid.proqueue.core.ui.components.isWideLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.state.BusinessStateHolder
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.domain.model.appointment.AppointmentWithDetails

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = koinViewModel(),
    isPicker: Boolean = false,
    excludeAppointmentId: Long? = null,
    onNavigateBack: () -> Unit,
    onNavigateToCreateAppointment: (Long, String) -> Unit,
    onNavigateToAppointmentDetails: (Long) -> Unit,
    onSlotSelected: (Long, String) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()

    // حذف LaunchedEffect تکراری — ViewModel.init با BusinessStateHolder بار اول را مدیریت می‌کند

    viewModel.events.collectWithLifecycleAware { event ->
        when (event) {
            CalendarEvent.NavigateBack -> onNavigateBack()
            is CalendarEvent.NavigateToCreateAppointment -> onNavigateToCreateAppointment(event.date, event.time)
            is CalendarEvent.NavigateToAppointmentDetails -> onNavigateToAppointmentDetails(event.appointmentId)
        }
    }

    CalendarScreenContent(
        uiState = uiState,
        isPicker = isPicker,
        excludeAppointmentId = excludeAppointmentId,
        onIntent = viewModel::sendIntent,
        onSlotSelected = onSlotSelected
    )
}

@Composable
fun CalendarScreenContent(
    uiState: CalendarState,
    isPicker: Boolean,
    excludeAppointmentId: Long? = null,
    onIntent: (CalendarIntent) -> Unit,
    onSlotSelected: (Long, String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = Str.booking_calendar,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onIntent(CalendarIntent.BackPress) }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, Str.back)
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (uiState.isLoading)
                                    MaterialTheme.colorScheme.surfaceVariant
                                else
                                    MaterialTheme.colorScheme.primaryContainer
                            )
                            .clickable(enabled = !uiState.isLoading) { onIntent(CalendarIntent.LoadData) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = Str.refresh,
                            tint = if (uiState.isLoading)
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        // The appointment being edited (if any) is dropped from the occupancy
        // check, otherwise its own slot reads as "occupied" and the picker
        // refuses to let it be reselected.
        val visibleAppointments = if (excludeAppointmentId != null) {
            uiState.appointments.filter { it.appointment.id != excludeAppointmentId }
        } else {
            uiState.appointments
        }
        val onSlotClick: (String) -> Unit = { time ->
            if (isPicker) {
                onSlotSelected(uiState.selectedDate, time)
            } else {
                onIntent(CalendarIntent.OnTimeSlotClick(time))
            }
        }
        val onAppointmentClick: (Long) -> Unit = {
            if (!isPicker) {
                onIntent(CalendarIntent.OnAppointmentClick(it))
            }
        }
        val business = BusinessStateHolder.selectedBusiness.value
        val isClosedDay = business != null &&
            DateTimeUtils.getWeekday(uiState.selectedDate) in business.closedWeekdays

        if (isWideLayout()) {
            // Desktop: the month and the day's numbers on one side, the day's
            // slots on the other, so picking a date never scrolls the slots
            // out of view.
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .width(360.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    MonthGridBlock(
                        selectedDate = uiState.selectedDate,
                        onDateSelected = { onIntent(CalendarIntent.SelectDate(it)) }
                    )
                    DaySummaryBlock(
                        selectedDate = uiState.selectedDate,
                        appointments = visibleAppointments
                    )
                }
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (isClosedDay) ClosedDayBanner()
                    if (uiState.isLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        TimeSlotsList(
                            appointments = visibleAppointments,
                            selectedDate = uiState.selectedDate,
                            onSlotClick = onSlotClick,
                            onAppointmentClick = onAppointmentClick
                        )
                    }
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            // Days Row
            DaysHeader(
                selectedDate = uiState.selectedDate,
                onDateSelected = { onIntent(CalendarIntent.SelectDate(it)) }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // Non-blocking: a day off does not stop the owner from adding a
                // walk-in or a one-off exception themselves, it only tells them
                // the calendar disagrees with what they're about to do.
                if (isClosedDay) ClosedDayBanner()

                TimeSlotsList(
                    appointments = visibleAppointments,
                    selectedDate = uiState.selectedDate,
                    onSlotClick = onSlotClick,
                    onAppointmentClick = onAppointmentClick
                )
            }
        }
    }
}

/** Shown above the slot list for a day the business marked as a weekly day
 *  off. Deliberately not a hard block: the owner can still add a walk-in or a
 *  one-off exception here, this only flags that the calendar disagrees. */
@Composable
private fun ClosedDayBanner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Event,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = Str.this_day_is_one_of,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
fun DaysHeader(
    selectedDate: Long,
    onDateSelected: (Long) -> Unit
) {
    // A month back and a month forward, not just the current month: booking on
    // the 30th used to have nowhere to go, and past days were unreachable.
    val days = remember {
        DateTimeUtils.getDayWindow(monthsBefore = 1, monthsAfter = 1)
    }

    val listState = rememberLazyListState()

    // Keyed on selectedDate, not Unit: the window now spans three months, so
    // landing on the right day matters, and picking a date elsewhere (the
    // date sheet, a notification) has to bring the strip with it.
    LaunchedEffect(selectedDate) {
        val selectedDateFormatted = DateTimeUtils.formatDate(selectedDate)
        val index = days.indexOfFirst { DateTimeUtils.formatDate(it) == selectedDateFormatted }
        if (index >= 0) {
            // Offset so the selected day sits a little in from the edge with
            // its neighbours visible, instead of flush against the side.
            listState.scrollToItem(index, scrollOffset = 0)
        }
    }

    // The *selected* day's month, not the first day in the list. With a
    // three-month window a fixed "first day" header would label مهر as شهریور
    // for two thirds of the strip.
    val persianDate = DateTimeUtils.getJalaliDateParts(selectedDate)
    val monthName = DateTimeUtils.jalaliMonthName(persianDate.month - 1)

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Event,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$monthName ${persianDate.year}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            val closedWeekdays = BusinessStateHolder.selectedBusiness.value?.closedWeekdays.orEmpty()
            items(days) { dateMillis ->
                val isSelected = DateTimeUtils.formatDate(dateMillis) == DateTimeUtils.formatDate(selectedDate)

                val pDate = DateTimeUtils.getJalaliDateParts(dateMillis)
                val dayOfWeek = DateTimeUtils.getDayOfWeekName(dateMillis)
                val isClosed = DateTimeUtils.getWeekday(dateMillis) in closedWeekdays

                DayItem(
                    day = pDate.dayOfMonth.toString(),
                    dayOfWeek = dayOfWeek,
                    isSelected = isSelected,
                    isClosed = isClosed,
                    onClick = { onDateSelected(dateMillis) }
                )
            }
        }
    }
}

@Composable
fun DayItem(
    day: String,
    dayOfWeek: String,
    isSelected: Boolean,
    isClosed: Boolean = false,
    onClick: () -> Unit
) {
    // Selection still wins when a closed day is tapped — closed is informational,
    // not a lock, so it must not fight the one state that shows what's picked.
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isClosed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isClosed -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .width(64.dp)
            .height(84.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = day,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = dayOfWeek,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun TimeSlotsList(
    appointments: List<AppointmentWithDetails>,
    selectedDate: Long,
    onSlotClick: (String) -> Unit,
    onAppointmentClick: (Long) -> Unit
) {
    // The business's own service length, not a fixed 10 minutes: a business
    // whose real appointments run 30 or 60 minutes was being offered a slot
    // every 10 minutes regardless, which is 3-6x denser than anything they
    // could actually book. Coerced to at least 5 so a 0-or-negative value
    // (shouldn't happen — the create-business form validates this — but this
    // is a UI loop, not a place to trust that) can never make `step` crash.
    val interval = (BusinessStateHolder.selectedBusiness.value?.defaultServiceDuration ?: 30)
        .coerceAtLeast(5)

    val slots = remember(interval) {
        val list = mutableListOf<String>()
        for (h in 0..23) {
            for (m in 0..59 step interval) {
                list.add("${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}")
            }
        }
        list
    }

    val listState = rememberLazyListState()

    LaunchedEffect(interval) {
        val now = DateTimeUtils.formatTimeNow()
        val parts = now.split(":")
        val h = parts[0].toInt()
        val m = parts[1].toInt()
        val slotsPerHour = 60 / interval
        val index = (h * slotsPerHour) + (m / interval)
        if (index in slots.indices) {
            listState.scrollToItem(index)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        items(slots) { time ->
            val slotTimeParts = time.split(":")
            val slotHour = slotTimeParts[0].toInt()
            val slotMinute = slotTimeParts[1].toInt()

            val startingAppointments = appointments.filter {
                val appTime = DateTimeUtils.formatTime(it.appointment.appointmentDate)
                val parts = appTime.split(":")
                val h = parts[0].toInt()
                val m = parts[1].toInt()
                h == slotHour && m >= slotMinute && m < slotMinute + interval
            }

            val isCovered = appointments.any { appointment ->
                val appStart = appointment.appointment.appointmentDate
                val duration = appointment.appointment.serviceDuration ?: 30
                val slotInMins = slotHour * 60 + slotMinute
                val appStartParts = DateTimeUtils.formatTime(appStart).split(":")
                val appStartInMins = appStartParts[0].toInt() * 60 + appStartParts[1].toInt()
                val appEndInMins = appStartInMins + duration
                slotInMins > appStartInMins && slotInMins < appEndInMins
            }

            if (!isCovered) {
                TimeSlotRow(
                    time = time,
                    appointments = startingAppointments,
                    onSlotClick = { onSlotClick(time) },
                    onAppointmentClick = onAppointmentClick
                )
            }
        }
    }
}

@Composable
fun TimeSlotRow(
    time: String,
    appointments: List<AppointmentWithDetails>,
    onSlotClick: () -> Unit,
    onAppointmentClick: (Long) -> Unit
) {
    val height = if (appointments.isNotEmpty()) {
        val maxDuration = appointments.maxOf { it.appointment.serviceDuration ?: 10 }
        (maxDuration * 6.0).dp.coerceAtLeast(60.dp)
    } else {
        60.dp
    }

    val isDark = !MaterialTheme.colorScheme.surface.let { color ->
        (color.red * 0.299 + color.green * 0.587 + color.blue * 0.114) > 0.5
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Time Label
        Box(
            modifier = Modifier
                .width(56.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onSlotClick),
            contentAlignment = Alignment.TopStart
        ) {
            Text(
                text = time,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        
        // Timeline Dot and Line
        Column(
            modifier = Modifier.fillMaxHeight().width(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(16.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Appointments Area
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = appointments.isEmpty(), onClick = onSlotClick),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (appointments.isNotEmpty()) {
                appointments.forEach { appointment ->
                    val appHeight = ((appointment.appointment.serviceDuration ?: 10) * 6.0).dp
                    
                    val isOverdue = remember(appointment) {
                        val endTime = appointment.appointment.appointmentDate + (appointment.appointment.serviceDuration ?: 30) * 60 * 1000L
                        DateTimeUtils.systemCurrentMilliseconds() > endTime && appointment.appointment.status == "WAITING"
                    }

                    // Adaptive Colors
                    val status = appointment.appointment.status
                    val (containerColor, contentColor) = when {
                        // Green = service finished. CONFIRMED only means the owner
                        // approved the booking, so it gets its own blue.
                        status == "COMPLETED" -> {
                            val bg = if (isDark) Color(0xFF1B5E20).copy(alpha = 0.4f) else Color(0xFFE8F5E9)
                            val fg = if (isDark) Color(0xFFA5D6A7) else Color(0xFF2E7D32)
                            bg to fg
                        }
                        status == "CONFIRMED" -> {
                            val bg = if (isDark) Color(0xFF0D47A1).copy(alpha = 0.4f) else Color(0xFFE3F2FD)
                            val fg = if (isDark) Color(0xFF90CAF9) else Color(0xFF1565C0)
                            bg to fg
                        }
                        status == "NO_SHOW" || status == "CANCELLED" || isOverdue -> {
                            val bg = if (isDark) Color(0xFFB71C1C).copy(alpha = 0.4f) else Color(0xFFFFEBEE)
                            val fg = if (isDark) Color(0xFFEF9A9A) else Color(0xFFC62828)
                            bg to fg
                        }
                        else -> {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (isDark) 0.5f else 0.8f) to MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(appHeight)
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onAppointmentClick(appointment.appointment.id) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = containerColor,
                            contentColor = contentColor
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = appointment.visitor.fullName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            if (appointment.appointment.description != null) {
                                Text(
                                    text = appointment.appointment.description,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    color = contentColor.copy(alpha = 0.8f)
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(contentColor.copy(alpha = 0.5f))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = Str.min(appointment.appointment.serviceDuration ?: 30),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else {
                // Empty slot visual hint
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                )
            }
        }
    }
}
