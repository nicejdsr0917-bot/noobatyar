package xyz.sattar.javid.proqueue.feature.createAppointment

import xyz.sattar.javid.proqueue.core.ui.components.ForwardChevron
import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.core.ui.components.showToasty
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.ui.LocalWindowSize
import xyz.sattar.javid.proqueue.core.ui.WindowSize
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.core.ui.components.AppButton
import xyz.sattar.javid.proqueue.core.ui.components.AppScaffold
import xyz.sattar.javid.proqueue.core.ui.components.AppTextField
import xyz.sattar.javid.proqueue.core.ui.components.AppointmentsListBottomSheet
import xyz.sattar.javid.proqueue.core.ui.components.ContentWidth
import xyz.sattar.javid.proqueue.core.ui.components.SelectedServiceChipsRow
import xyz.sattar.javid.proqueue.core.ui.components.ServiceCatalogBottomSheet
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import xyz.sattar.javid.proqueue.ui.theme.AppTheme
import kotlin.time.ExperimentalTime
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost


@Composable
fun CreateAppointmentScreen(
    visitorId: Long? = null,
    appointmentId: Long? = null,
    initialDate: Long? = null,
    initialTime: String? = null,
    viewModel: CreateAppointmentViewModel = koinViewModel<CreateAppointmentViewModel>(),
    onNavigateBack: () -> Unit = {},
    onNavigateToCalendar: () -> Unit = {},
    onAppointmentCreated: () -> Unit = {},
    onNavigateToVisitorSelection: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(appointmentId, visitorId) {
        if (appointmentId != null) {
            viewModel.sendIntent(CreateAppointmentIntent.LoadAppointment(appointmentId))
        } else if (visitorId != null) {
            viewModel.sendIntent(CreateAppointmentIntent.SelectVisitor(visitorId))
        }
    }

    HandleEvents(
        events = viewModel.events,
        onNavigateBack = onNavigateBack,
        onAppointmentCreated = onAppointmentCreated
    )

    CreateAppointmentScreenContent(
        uiState = uiState,
        onIntent = viewModel::sendIntent,
        initialVisitorId = visitorId,
        initialDate = initialDate,
        initialTime = initialTime,
        onNavigateToCalendar = onNavigateToCalendar,
        onNavigateToVisitorSelection = onNavigateToVisitorSelection,
    )
}

/**
 * Holds all shared state/effects (unchanged from before the web layout split)
 * and dispatches to [CreateAppointmentPhoneContent] or [CreateAppointmentWebContent]
 * by [LocalWindowSize]. Compact keeps the exact original screen; Medium/Expanded
 * get a two-panel desktop layout — see [CreateAppointmentWebContent].
 */
@OptIn(ExperimentalTime::class)
@Composable
fun CreateAppointmentScreenContent(
    modifier: Modifier = Modifier,
    uiState: CreateAppointmentState,
    onIntent: (CreateAppointmentIntent) -> Unit,
    initialVisitorId: Long? = null,
    initialDate: Long? = null,
    initialTime: String? = null,
    onNavigateToCalendar: () -> Unit,
    onNavigateToVisitorSelection: () -> Unit,
) {
    var selectedVisitorId by remember { mutableStateOf(initialVisitorId) }
    var selectedDate by remember { mutableStateOf(initialDate ?: DateTimeUtils.systemCurrentMilliseconds()) }
    var selectedTime by remember { mutableStateOf(initialTime ?: "09:00") }
    var serviceDuration by remember { mutableStateOf(uiState.serviceDuration?.toString() ?: "30") }
    var serviceDurationError by remember { mutableStateOf<String?>(null) }
    var showAppointmentsList by remember { mutableStateOf(false) }
    var description by remember { mutableStateOf(uiState.description ?: "") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showServiceCatalogSheet by remember { mutableStateOf(false) }

    // Loaded once per screen visit, scoped to the current business's
    // category — see ServiceCatalogBottomSheet's doc for why this list is
    // shared across every business in that category, not just this one.
    LaunchedEffect(Unit) {
        onIntent(CreateAppointmentIntent.LoadServiceCatalog)
    }

    LaunchedEffect(initialDate, initialTime) {
        onIntent(CreateAppointmentIntent.UpdateDateTime(initialDate, initialTime))
        if (initialDate != null) selectedDate = initialDate
        if (initialTime != null) selectedTime = initialTime
    }

    LaunchedEffect(uiState.selectedDate, uiState.selectedTime) {
        if (uiState.selectedDate != null) selectedDate = uiState.selectedDate
        if (uiState.selectedTime != null) selectedTime = uiState.selectedTime
    }

    LaunchedEffect(selectedDate) {
        onIntent(CreateAppointmentIntent.LoadDailyAppointments(selectedDate))
    }

    LaunchedEffect(uiState.selectedVisitorId) {
        if (uiState.selectedVisitorId != null) {
            selectedVisitorId = uiState.selectedVisitorId
        }
    }

    LaunchedEffect(uiState.serviceDuration) {
        if (uiState.serviceDuration != null) {
            serviceDuration = uiState.serviceDuration.toString()
        }
    }

    LaunchedEffect(uiState.description) {
        if (uiState.description != null) {
            description = uiState.description ?: ""
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        val msg = uiState.message
        if (msg != null) {
            snackbarHostState.showToasty(msg)
        }
    }

    // Shared submit logic for both the primary action and the conflict
    // dialog's "force create" confirm — kept in one place so the two
    // call sites (bottomBar button on phone, inline action card on web,
    // and the conflict dialog) can never drift apart on validation or on
    // which fields get sent.
    val serviceDurationErrorMsg = Str.service_duration_error
    val onSubmit: (Boolean) -> Unit = submit@{ force ->
        val visitorId = selectedVisitorId ?: return@submit
        val duration = serviceDuration.trim().toIntOrNull()
        if (!force) {
            serviceDurationError = if (duration == null) serviceDurationErrorMsg else null
        }
        onIntent(
            CreateAppointmentIntent.CreateAppointment(
                visitorId = visitorId,
                appointmentDate = DateTimeUtils.combineDateAndTime(selectedDate, selectedTime),
                serviceDuration = duration,
                description = description.ifEmpty { null },
                selectedServices = uiState.selectedServices,
                force = force
            )
        )
    }

    if (LocalWindowSize.current == WindowSize.Compact) {
        CreateAppointmentPhoneContent(
            modifier = modifier,
            uiState = uiState,
            snackbarHostState = snackbarHostState,
            selectedVisitorId = selectedVisitorId,
            selectedDate = selectedDate,
            selectedTime = selectedTime,
            serviceDuration = serviceDuration,
            serviceDurationError = serviceDurationError,
            description = description,
            onIntent = onIntent,
            onServiceDurationChange = { serviceDuration = it; serviceDurationError = null },
            onDescriptionChange = { description = it },
            onSubmit = { onSubmit(false) },
            onShowAppointmentsList = { showAppointmentsList = true },
            onShowServiceCatalogSheet = { showServiceCatalogSheet = true },
            onShowDeleteDialog = { showDeleteDialog = true },
            onNavigateToCalendar = onNavigateToCalendar,
            onNavigateToVisitorSelection = onNavigateToVisitorSelection,
        )
    } else {
        CreateAppointmentWebContent(
            modifier = modifier,
            uiState = uiState,
            snackbarHostState = snackbarHostState,
            selectedVisitorId = selectedVisitorId,
            selectedDate = selectedDate,
            selectedTime = selectedTime,
            serviceDuration = serviceDuration,
            serviceDurationError = serviceDurationError,
            description = description,
            onIntent = onIntent,
            onServiceDurationChange = { serviceDuration = it; serviceDurationError = null },
            onDescriptionChange = { description = it },
            onSubmit = { onSubmit(false) },
            onShowAppointmentsList = { showAppointmentsList = true },
            onShowServiceCatalogSheet = { showServiceCatalogSheet = true },
            onShowDeleteDialog = { showDeleteDialog = true },
            onNavigateToCalendar = onNavigateToCalendar,
            onNavigateToVisitorSelection = onNavigateToVisitorSelection,
        )
    }

    // Overlays: sheets and dialogs render through Dialog/ModalBottomSheet,
    // which position themselves relative to the window, not to whichever
    // content tree they're declared under — safe to keep as one shared block
    // for both layouts instead of duplicating per variant.
    CreateAppointmentOverlays(
        uiState = uiState,
        showAppointmentsList = showAppointmentsList,
        onDismissAppointmentsList = { showAppointmentsList = false },
        showServiceCatalogSheet = showServiceCatalogSheet,
        onDismissServiceCatalogSheet = { showServiceCatalogSheet = false },
        showDeleteDialog = showDeleteDialog,
        onDismissDeleteDialog = { showDeleteDialog = false },
        onConfirmDelete = {
            showDeleteDialog = false
            uiState.editingAppointmentId?.let { id ->
                onIntent(CreateAppointmentIntent.DeleteAppointment(id))
            }
        },
        onIntent = onIntent,
        onForceCreate = { onSubmit(true) }
    )
}

/**
 * Phone layout — unchanged. Scaffold with a bottomBar pinning the submit
 * button to the bottom of the viewport, which is the right pattern at phone
 * width (thumb reach). See [CreateAppointmentWebContent] for the desktop
 * layout, where a pinned bar would just float at the bottom of an otherwise
 * empty page.
 */
@Composable
private fun CreateAppointmentPhoneContent(
    modifier: Modifier = Modifier,
    uiState: CreateAppointmentState,
    snackbarHostState: SnackbarHostState,
    selectedVisitorId: Long?,
    selectedDate: Long,
    selectedTime: String,
    serviceDuration: String,
    serviceDurationError: String?,
    description: String,
    onIntent: (CreateAppointmentIntent) -> Unit,
    onServiceDurationChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onShowAppointmentsList: () -> Unit,
    onShowServiceCatalogSheet: () -> Unit,
    onShowDeleteDialog: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToVisitorSelection: () -> Unit,
) {
    Scaffold(
        snackbarHost = {
            ToastyHost(hostState = snackbarHostState)
        },
        topBar = {
            CreateAppointmentTopBar(
                uiState = uiState,
                onBackPress = { onIntent(CreateAppointmentIntent.BackPress) },
                onDeleteClick = onShowDeleteDialog
            )
        },
        bottomBar = {
            if (!uiState.isLoading && !uiState.appointmentCreated) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.background,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BookingSummaryLine(
                            selectedDate = selectedDate,
                            selectedTime = selectedTime,
                            durationMinutes = serviceDuration.trim().toIntOrNull()
                        )
                        AppButton(
                            text = if (uiState.editingAppointmentId != null) Str.edit_appointment else Str.appointment_create_action,
                            onClick = onSubmit,
                            isLoading = uiState.isLoading
                        )
                    }
                }
            }
        }
    ) { paddingValues ->

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            if (uiState.appointmentCreated) {
                onIntent(CreateAppointmentIntent.AppointmentCreated)
            } else if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                // Who and when first (the decisions everything else hangs
                // on), then what is being done and for how long, then notes.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    AppointmentHeroCard(
                        selectedVisitorId = selectedVisitorId,
                        visitorName = uiState.visitor?.fullName,
                        visitorPhone = uiState.visitor?.phoneNumber,
                        selectedDate = selectedDate,
                        selectedTime = selectedTime,
                        dailyCount = uiState.dailyAppointmentsCount,
                        onVisitorClick = onNavigateToVisitorSelection,
                        onDateTimeClick = onNavigateToCalendar,
                        onShowDayList = onShowAppointmentsList
                    )

                    ServicesSection(
                        selectedServices = uiState.selectedServices,
                        onOpenCatalog = onShowServiceCatalogSheet,
                        onRemove = { name ->
                            onIntent(
                                CreateAppointmentIntent.UpdateSelectedServices(
                                    uiState.selectedServices - name
                                )
                            )
                        }
                    )

                    DurationSection(
                        value = serviceDuration,
                        onValueChange = onServiceDurationChange,
                        isError = serviceDurationError != null,
                        errorMessage = serviceDurationError,
                        enabled = !uiState.isLoading
                    )

                    NotesSection(
                        value = description,
                        onValueChange = onDescriptionChange,
                        enabled = !uiState.isLoading
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

/**
 * Desktop layout (Medium/Expanded). A single scrolling column of stacked
 * cards is a phone pattern that just reads as a narrow ribbon on a wide
 * monitor, so the fields are grouped into two panels side by side — the
 * "who and when" decisions (visitor, date/time, today's count) on one side,
 * "what and how" (duration, description, service catalog) on the other —
 * with the submit action as its own full-width card below both, still part
 * of the normal scroll rather than pinned to the viewport bottom the way the
 * phone bottomBar is.
 *
 * RTL note: the app forces RTL, so the first child of the panels [Row]
 * lands on the *right*. The visitor/date panel goes first so it reads as
 * the natural starting point; the service/notes panel follows to its left.
 */
@Composable
private fun CreateAppointmentWebContent(
    modifier: Modifier = Modifier,
    uiState: CreateAppointmentState,
    snackbarHostState: SnackbarHostState,
    selectedVisitorId: Long?,
    selectedDate: Long,
    selectedTime: String,
    serviceDuration: String,
    serviceDurationError: String?,
    description: String,
    onIntent: (CreateAppointmentIntent) -> Unit,
    onServiceDurationChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onShowAppointmentsList: () -> Unit,
    onShowServiceCatalogSheet: () -> Unit,
    onShowDeleteDialog: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToVisitorSelection: () -> Unit,
) {
    val isExpanded = LocalWindowSize.current == WindowSize.Expanded

    Scaffold(
        snackbarHost = {
            ToastyHost(hostState = snackbarHostState)
        },
        topBar = {
            CreateAppointmentTopBar(
                uiState = uiState,
                onBackPress = { onIntent(CreateAppointmentIntent.BackPress) },
                onDeleteClick = onShowDeleteDialog
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            if (uiState.appointmentCreated) {
                onIntent(CreateAppointmentIntent.AppointmentCreated)
            } else if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                AppScaffold(
                    modifier = Modifier.fillMaxSize(),
                    maxWidth = if (isExpanded) ContentWidth.Wide else ContentWidth.List
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        val hero: @Composable () -> Unit = {
                            AppointmentHeroCard(
                                selectedVisitorId = selectedVisitorId,
                                visitorName = uiState.visitor?.fullName,
                                visitorPhone = uiState.visitor?.phoneNumber,
                                selectedDate = selectedDate,
                                selectedTime = selectedTime,
                                dailyCount = uiState.dailyAppointmentsCount,
                                onVisitorClick = onNavigateToVisitorSelection,
                                onDateTimeClick = onNavigateToCalendar,
                                onShowDayList = onShowAppointmentsList
                            )
                        }
                        val services: @Composable () -> Unit = {
                            ServicesSection(
                                selectedServices = uiState.selectedServices,
                                onOpenCatalog = onShowServiceCatalogSheet,
                                onRemove = { name ->
                                    onIntent(
                                        CreateAppointmentIntent.UpdateSelectedServices(
                                            uiState.selectedServices - name
                                        )
                                    )
                                }
                            )
                        }
                        val duration: @Composable () -> Unit = {
                            DurationSection(
                                value = serviceDuration,
                                onValueChange = onServiceDurationChange,
                                isError = serviceDurationError != null,
                                errorMessage = serviceDurationError,
                                enabled = !uiState.isLoading
                            )
                        }
                        val notes: @Composable () -> Unit = {
                            NotesSection(
                                value = description,
                                onValueChange = onDescriptionChange,
                                enabled = !uiState.isLoading
                            )
                        }

                        if (isExpanded) {
                            // Who and when on the reading-start side (the
                            // right, under RTL); what, how long and notes
                            // beside it.
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    hero()
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(24.dp)
                                ) {
                                    services()
                                    duration()
                                    notes()
                                }
                            }
                        } else {
                            // Medium: one column, same order as the phone.
                            hero()
                            services()
                            duration()
                            notes()
                        }

                        // Submit action — its own full-width card at the end of
                        // the scrolling content. Deliberately not a Scaffold
                        // bottomBar: pinning it to the viewport bottom is a
                        // phone (thumb-reach) pattern that on a tall desktop
                        // window would leave it floating below an empty page.
                        if (!uiState.isLoading && !uiState.appointmentCreated) {
                            SubmitActionCard(
                                text = if (uiState.editingAppointmentId != null) Str.edit_appointment else Str.appointment_create_action,
                                isLoading = uiState.isLoading,
                                onClick = onSubmit,
                                summary = {
                                    BookingSummaryLine(
                                        selectedDate = selectedDate,
                                        selectedTime = selectedTime,
                                        durationMinutes = serviceDuration.trim().toIntOrNull()
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Shared top bar: back button, title (create vs edit), delete action while editing. */
@Composable
private fun CreateAppointmentTopBar(
    uiState: CreateAppointmentState,
    onBackPress: () -> Unit,
    onDeleteClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = if (uiState.editingAppointmentId != null) Str.edit_appointment else Str.create_appointment_title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackPress) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = Str.back
                )
            }
        },
        actions = {
            if (uiState.editingAppointmentId != null) {
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = Str.delete,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

/** The two decisions everything else hangs on — who and when — read
 *  together at the top, on the brand gradient (the same treatment as the
 *  profile header in Settings). Each half opens its own picker. */
@Composable
private fun AppointmentHeroCard(
    selectedVisitorId: Long?,
    visitorName: String?,
    visitorPhone: String?,
    selectedDate: Long,
    selectedTime: String,
    dailyCount: Int,
    onVisitorClick: () -> Unit,
    onDateTimeClick: () -> Unit,
    onShowDayList: () -> Unit,
) {
    val gradient = Brush.linearGradient(
        listOf(Color(0xFFA78BFA), Color(0xFF8B5CF6), Color(0xFF7C3AED))
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().background(gradient)) {
            // Decorative oversized glyph in the corner.
            Icon(
                imageVector = Icons.Rounded.EventAvailable,
                contentDescription = null,
                modifier = Modifier
                    .size(160.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 40.dp, y = (-36).dp),
                tint = Color.White.copy(alpha = 0.10f)
            )

            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Who. A visitor already chosen stays fixed, as before.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = selectedVisitorId == null, onClick = onVisitorClick),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        val initial = visitorName?.trim()?.firstOrNull()?.uppercaseChar()?.toString()
                        if (selectedVisitorId != null && initial != null) {
                            Text(
                                text = initial,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.PersonAdd,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Str.client,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                        Text(
                            text = if (selectedVisitorId != null) visitorName ?: "--" else Str.visitor_selection_title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (selectedVisitorId != null && !visitorPhone.isNullOrBlank()) {
                            Text(
                                text = visitorPhone,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                    if (selectedVisitorId == null) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }

                // When: a frosted panel inside the card, tapping it opens the
                // calendar.
                Surface(
                    onClick = onDateTimeClick,
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.16f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = Str.appointment_time,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                            Text(
                                text = appointmentDateLabel(selectedDate),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White
                        ) {
                            Text(
                                text = selectedTime.toPersianDigits(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7C3AED),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                if (dailyCount > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onShowDayList)
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.EventNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = Str.appointments_booked_on_this_day(dailyCount.toString().toPersianDigits()),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = Str.view,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Icon(
                            imageVector = ForwardChevron,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Title row shared by the plain sections under the hero card. */
@Composable
private fun FormSection(
    title: String,
    optional: Boolean = false,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (optional) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = Str.optional,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            action?.invoke()
        }
        content()
    }
}

/**
 * Service catalog chips — additive to the free-text notes below, not a
 * replacement for them. The list offered here is scoped to the current
 * business's category and shared with every other business in that category
 * (see ServiceCatalogBottomSheet's doc).
 */
@Composable
private fun ServicesSection(
    selectedServices: List<String>,
    onOpenCatalog: () -> Unit,
    onRemove: (String) -> Unit
) {
    FormSection(
        title = Str.service_catalog_title,
        action = if (selectedServices.isEmpty()) null else {
            {
                TextButton(onClick = onOpenCatalog) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(Str.add)
                }
            }
        }
    ) {
        if (selectedServices.isEmpty()) {
            OutlinedCard(
                onClick = onOpenCatalog,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Checklist,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Str.choose_services_from_list,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = Str.services_done_in_this_appointment,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else {
            SelectedServiceChipsRow(
                selected = selectedServices,
                onRemove = onRemove
            )
        }
    }
}

private val DurationPresets = listOf(15, 30, 45, 60, 90, 120)

/**
 * Service duration as one-tap presets, with the free number field behind
 * "مدت دلخواه" in the section header for anything else. A loaded value that isn't a preset (editing an
 * existing appointment) opens the field on its own, and so does a
 * validation error, so the message is never hidden.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurationSection(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    errorMessage: String?,
    enabled: Boolean
) {
    val current = value.trim().toIntOrNull()
    var custom by remember { mutableStateOf(current != null && current !in DurationPresets) }
    LaunchedEffect(current) {
        if (current != null && current !in DurationPresets) custom = true
    }

    FormSection(
        title = Str.appointment_length,
        action = {
            TextButton(onClick = { custom = true }, enabled = enabled) {
                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = Str.custom_length,
                    fontWeight = if (custom) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    ) {
        // An even 3×2 grid: free wrapping left a ragged split on a phone,
        // and four columns were too narrow for "۱۵ دقیقه".
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 3,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DurationPresets.forEach { minutes ->
                DurationChip(
                    label = durationLabel(minutes),
                    selected = !custom && current == minutes,
                    enabled = enabled,
                    onClick = {
                        custom = false
                        onValueChange(minutes.toString())
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        AnimatedVisibility(visible = custom || isError) {
            AppTextField(
                value = value,
                maxLength = 3,
                keyboardType = KeyboardType.Number,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = Str.length_in_minutes,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                isError = isError,
                errorMessage = errorMessage,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun DurationChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        label = {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
        },
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

/** Free-text notes — shared by phone and desktop layouts. */
@Composable
private fun NotesSection(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean
) {
    FormSection(title = Str.notes, optional = true) {
        AppTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            maxLine = 3,
            maxLength = 200,
            showCounter = value.isNotEmpty(),
            placeholder = Str.e_g_clients_special_request,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            enabled = enabled,
        )
    }
}

/**
 * One line restating the booking right above its submit button, so the
 * owner confirms what they are about to save without scrolling back up:
 * "چهارشنبه ۹ مهر · ۰۹:۰۰ تا ۱۰:۰۰".
 */
@Composable
private fun BookingSummaryLine(
    selectedDate: Long,
    selectedTime: String,
    durationMinutes: Int?
) {
    val end = durationMinutes?.let { endTime(selectedTime, it) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = buildString {
                append(appointmentDateLabel(selectedDate))
                append(" · ")
                append(selectedTime.toPersianDigits())
                if (end != null) append(Str.to).append(end.toPersianDigits())
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** "چهارشنبه ۹ مهر", with the year only when it isn't the current one. */
private fun appointmentDateLabel(millis: Long): String {
    val date = DateTimeUtils.getJalaliDateParts(millis)
    val thisYear = DateTimeUtils.getJalaliDateParts(DateTimeUtils.systemCurrentMilliseconds()).year
    val label = buildString {
        append(DateTimeUtils.getDayOfWeekName(millis))
        append(' ')
        append(date.dayOfMonth)
        append(' ')
        append(DateTimeUtils.jalaliMonthName(date.month - 1))
        if (date.year != thisYear) append(' ').append(date.year)
    }
    return label.toPersianDigits()
}

/** Whole hours read as hours ("۱ ساعت"), anything else in minutes. */
private fun durationLabel(minutes: Int): String =
    (if (minutes >= 60 && minutes % 60 == 0) Str.h(minutes / 60) else Str.min(minutes)).toPersianDigits()

/** "HH:mm" plus [minutes], wrapping past midnight; null if [start] won't parse. */
private fun endTime(start: String, minutes: Int): String? {
    val parts = start.split(":")
    val h = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
    val m = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return null
    val total = ((h * 60 + m + minutes) % (24 * 60) + 24 * 60) % (24 * 60)
    return "${(total / 60).toString().padStart(2, '0')}:${(total % 60).toString().padStart(2, '0')}"
}

/**
 * Desktop-only submit action, part of the normal scroll instead of a pinned
 * bottomBar (see [CreateAppointmentWebContent]).
 */
@Composable
private fun SubmitActionCard(
    text: String,
    isLoading: Boolean,
    onClick: () -> Unit,
    summary: @Composable () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            summary()
            AppButton(
                text = text,
                onClick = onClick,
                isLoading = isLoading
            )
        }
    }
}

/**
 * Sheets and dialogs shared by both layouts. [AppointmentsListBottomSheet]
 * and [ServiceCatalogBottomSheet] already route through AdaptiveSheet
 * (bottom sheet on Compact, centered dialog on Medium/Expanded) and neither
 * calls `sheetState.hide()` from here — the "done" action just calls
 * [onDismiss] directly, so there's no anchor-less hide() to guard in this
 * file. AlertDialog is unaffected by window size.
 */
@Composable
private fun CreateAppointmentOverlays(
    uiState: CreateAppointmentState,
    showAppointmentsList: Boolean,
    onDismissAppointmentsList: () -> Unit,
    showServiceCatalogSheet: Boolean,
    onDismissServiceCatalogSheet: () -> Unit,
    showDeleteDialog: Boolean,
    onDismissDeleteDialog: () -> Unit,
    onConfirmDelete: () -> Unit,
    onIntent: (CreateAppointmentIntent) -> Unit,
    onForceCreate: () -> Unit
) {
    if (showAppointmentsList) {
        AppointmentsListBottomSheet(
            appointments = uiState.dailyAppointments,
            onDismiss = onDismissAppointmentsList
        )
    }

    if (showServiceCatalogSheet) {
        ServiceCatalogBottomSheet(
            catalog = uiState.serviceCatalog,
            selected = uiState.selectedServices,
            isLoading = uiState.isServiceCatalogLoading,
            onToggle = { name ->
                val updated = if (uiState.selectedServices.contains(name)) {
                    uiState.selectedServices - name
                } else {
                    uiState.selectedServices + name
                }
                onIntent(CreateAppointmentIntent.UpdateSelectedServices(updated))
            },
            onAddNew = { name ->
                onIntent(CreateAppointmentIntent.AddServiceCatalogItem(name))
            },
            onDismiss = onDismissServiceCatalogSheet
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = onDismissDeleteDialog,
            title = { Text(Str.delete_appointment) },
            text = { Text(Str.are_you_sure_you_want) },
            confirmButton = {
                TextButton(
                    onClick = onConfirmDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(Str.delete)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeleteDialog) {
                    Text(Str.cancel)
                }
            }
        )
    }

    // Conflict Dialog
    if (uiState.showConflictDialog) {
        val prefix = Str.conflict_dialog_message_prefix
        val suffix = Str.conflict_dialog_message_suffix
        AlertDialog(
            onDismissRequest = { onIntent(CreateAppointmentIntent.DismissConflictDialog) },
            title = { Text(Str.conflict_dialog_title) },
            text = {
                Text(
                    text = buildAnnotatedString {
                        append(prefix)
                        withStyle(
                            style = SpanStyle(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        ) {
                            append(uiState.conflictingVisitorName ?: "")
                        }
                        append(suffix)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = onForceCreate) {
                    Text(Str.yes_force_create)
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(CreateAppointmentIntent.DismissConflictDialog) }) {
                    Text(Str.no)
                }
            }
        )
    }

    // Quota / capacity reached dialog — explains what happened and what to do.
    if (uiState.quotaDialogMessage != null) {
        AlertDialog(
            onDismissRequest = { onIntent(CreateAppointmentIntent.DismissQuotaDialog) },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(Str.cant_book_appointment) },
            text = {
                Column {
                    Text(uiState.quotaDialogMessage ?: "")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        Str.to_book_more_appointments_upgrade,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onIntent(CreateAppointmentIntent.DismissQuotaDialog) }) {
                    Text(Str.got_it)
                }
            }
        )
    }
}


@Composable
fun HandleEvents(
    events: Flow<CreateAppointmentEvent>,
    onNavigateBack: () -> Unit,
    onAppointmentCreated: () -> Unit
) {
    events.collectWithLifecycleAware {
        when (it) {
            CreateAppointmentEvent.NavigateBack -> {
                onNavigateBack()

            }

            CreateAppointmentEvent.AppointmentCreated,
            CreateAppointmentEvent.AppointmentDeleted -> {
                onAppointmentCreated()
            }
            else -> {}
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CreateAppointmentScreenPreview() {
    AppTheme {
//        CreateAppointmentScreenContent(
//            uiState = CreateAppointmentState(),
//            onIntent = {}
//        )
    }
}
