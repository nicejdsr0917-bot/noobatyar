package xyz.sattar.javid.proqueue.feature.businessList

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import xyz.sattar.javid.proqueue.core.ui.components.AppButton
import xyz.sattar.javid.proqueue.core.ui.components.BrandAmber
import xyz.sattar.javid.proqueue.core.ui.components.BrandIllustration
import xyz.sattar.javid.proqueue.core.ui.components.BrandIllustrationGradient
import xyz.sattar.javid.proqueue.core.ui.components.FloatingChip
import xyz.sattar.javid.proqueue.core.ui.components.IllustrationStage
import xyz.sattar.javid.proqueue.core.ui.components.ModerationBadge
import xyz.sattar.javid.proqueue.core.ui.components.OrbitDots
import xyz.sattar.javid.proqueue.core.utils.DateTimeUtils
import xyz.sattar.javid.proqueue.core.utils.toPersianDigits
import xyz.sattar.javid.proqueue.ui.theme.SuccessColor
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import xyz.sattar.javid.proqueue.core.ui.components.adaptivePagePadding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import proqueue.composeapp.generated.resources.Res
import proqueue.composeapp.generated.resources.business_list
import proqueue.composeapp.generated.resources.cancel
import proqueue.composeapp.generated.resources.create_first_business
import proqueue.composeapp.generated.resources.delete
import proqueue.composeapp.generated.resources.delete_business
import proqueue.composeapp.generated.resources.delete_business_confirmation
import proqueue.composeapp.generated.resources.no_business_found
import xyz.sattar.javid.proqueue.core.ui.collectWithLifecycleAware
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.ui.theme.AppTheme
import xyz.sattar.javid.proqueue.core.ui.components.ToastyHost


@Composable
fun BusinessListScreen(
    viewModel: BusinessListViewModel = koinViewModel<BusinessListViewModel>(),
    onNavigateToMain: (Business) -> Unit,
    onNavigateToCreateBusiness: () -> Unit,
    onNavigateToEditBusiness: (Long) -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    var businessToDelete by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        viewModel.sendIntent(BusinessListIntent.ObserveBusinesses)
    }

    HandleEvents(
        events = viewModel.events,
        onNavigateToMain = onNavigateToMain,
        onNavigateToCreateBusiness = onNavigateToCreateBusiness,
        onNavigateToEditBusiness = onNavigateToEditBusiness,
        onNavigateToLogin = onNavigateToLogin,
        snackbarHostState = snackbarHostState,
        onRetry = { viewModel.sendIntent(BusinessListIntent.RetryFetch) }
    )

    if (businessToDelete != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { businessToDelete = null },
            title = { Text(stringResource(Res.string.delete_business)) },
            text = { Text(stringResource(Res.string.delete_business_confirmation)) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        businessToDelete?.let {
                            viewModel.sendIntent(BusinessListIntent.OnDeleteBusinessClick(it))
                        }
                        businessToDelete = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(Res.string.delete))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { businessToDelete = null }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }

    BusinessListScreenContent(
        uiState = uiState,
        onIntent = viewModel::sendIntent,
        onNavigateToLogin = onNavigateToLogin,
        snackbarHostState = snackbarHostState,
        onDeleteRequest = { businessToDelete = it }
    )
}

@Composable
fun BusinessListScreenContent(
    modifier: Modifier = Modifier,
    uiState: BusinessListState,
    onIntent: (BusinessListIntent) -> Unit,
    onNavigateToLogin: () -> Unit,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    onDeleteRequest: (Long) -> Unit
) {
    Scaffold(
        snackbarHost = { ToastyHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(Res.string.business_list),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onIntent(BusinessListIntent.OnCreateBusinessClick) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    ) { paddingValues ->

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        repeat(5) {
                            xyz.sattar.javid.proqueue.core.ui.components.ListItemShimmer(height = 88.dp)
                        }
                    }
                }

                uiState.businesses.isEmpty() -> {
                    EmptyBusinessState(
                        onCreate = { onIntent(BusinessListIntent.OnCreateBusinessClick) },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    val listState = rememberLazyGridState()
                    val shouldLoadMore by androidx.compose.runtime.remember {
                        androidx.compose.runtime.derivedStateOf {
                            val totalItems = listState.layoutInfo.totalItemsCount
                            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            totalItems > 0 && lastVisibleItem >= totalItems - 2
                        }
                    }

                    LaunchedEffect(shouldLoadMore) {
                        if (shouldLoadMore && !uiState.isLoading && !uiState.isPaginating) {
                            onIntent(BusinessListIntent.LoadNextPage)
                        }
                    }

                    // One column on a phone; side-by-side cards on a desktop,
                    // where an owner with several branches sees them all at once.
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 340.dp),
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(adaptivePagePadding()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(uiState.businesses) { index, business ->
                            BusinessItem(
                                business = business,
                                onClick = { onIntent(BusinessListIntent.OnBusinessClick(business)) },
                                onEdit = { onIntent(BusinessListIntent.OnEditBusinessClick(business.id)) },
                                onDelete = { onDeleteRequest(business.id) },
                                modifier = Modifier.staggeredEntrance(index)
                            )
                        }
                        if (uiState.isPaginating) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    xyz.sattar.javid.proqueue.core.ui.components.ListItemShimmer(height = 88.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One business as a card: who it is (logo, name, category), where and when it
 * works — with whether it is open right now — and its review status, ending
 * in an explicit "ورود به پنل" so it reads as the way into that panel.
 */
@Composable
fun BusinessItem(
    business: Business,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "business-press")
    val cardShape = RoundedCornerShape(24.dp)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(cardShape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                BusinessLogo(business = business)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = business.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (business.categoryLabel.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = business.categoryLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("ویرایش") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("حذف", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (business.address.isNotBlank()) {
                    BusinessInfoRow(icon = Icons.Rounded.LocationOn, text = business.address)
                }
                BusinessInfoRow(
                    icon = Icons.Rounded.Schedule,
                    text = "${hhmm(business.workStartHour, business.workStartMinute)} تا " +
                        hhmm(business.workEndHour, business.workEndMinute),
                    trailing = { OpenStatusPill(status = openStatusOf(business)) }
                )
                // Checks both moderation AND the billing lock — a business can be
                // APPROVED but still invisible to clients because the plan lapsed,
                // and moderationStatus.isPubliclyVisible alone would miss that.
                if (business.moderationStatus != null && !business.isPubliclyVisible) {
                    BusinessInfoRow(
                        icon = Icons.Rounded.VisibilityOff,
                        text = "برای مراجعین نمایش داده نمی‌شود"
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Moderation state, so the owner can tell at a glance which of
                // their businesses clients can actually see.
                ModerationBadge(business = business)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "ورود به پنل",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** The logo on the brand gradient ring, or a storefront glyph when there is none. */
@Composable
private fun BusinessLogo(business: Business) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .background(BrandIllustrationGradient, RoundedCornerShape(18.dp))
            .padding(2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        if (business.logoPath.isNotEmpty()) {
            val url = if (business.logoPath.startsWith("http")) business.logoPath
            else "${xyz.sattar.javid.proqueue.core.utils.AppConfig.baseUrl}${business.logoPath}"
            coil3.compose.AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Storefront,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun BusinessInfoRow(
    icon: ImageVector,
    text: String,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

private enum class OpenStatus { Open, Closed, ClosedToday }

/**
 * Open right now by the business's own hours and weekly days off — the same
 * fields the booking page uses. [Business.closedWeekdays] counts Monday as 0,
 * the same as [DateTimeUtils.getWeekday]. Hours that end before they start
 * run past midnight.
 */
private fun openStatusOf(
    business: Business,
    nowMillis: Long = DateTimeUtils.systemCurrentMilliseconds()
): OpenStatus {
    if (DateTimeUtils.getWeekday(nowMillis) in business.closedWeekdays) return OpenStatus.ClosedToday
    val parts = DateTimeUtils.getJalaliTime(nowMillis).split(":")
    val now = (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    val start = business.workStartHour * 60 + business.workStartMinute
    val end = business.workEndHour * 60 + business.workEndMinute
    val open = if (end > start) now in start until end else now >= start || now < end
    return if (open) OpenStatus.Open else OpenStatus.Closed
}

@Composable
private fun OpenStatusPill(status: OpenStatus) {
    val (label, color) = when (status) {
        OpenStatus.Open -> "باز است" to SuccessColor
        OpenStatus.Closed -> "بسته است" to MaterialTheme.colorScheme.onSurfaceVariant
        OpenStatus.ClosedToday -> "امروز تعطیل" to BrandAmber
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

private fun hhmm(hour: Int, minute: Int): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}".toPersianDigits()

/** Cards fade and rise in one after another the first time the list shows. */
@Composable
private fun Modifier.staggeredEntrance(index: Int): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(5) * 70L)
        progress.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 24.dp.toPx()
    }
}

/**
 * What a freshly registered owner lands on — so it says what to do next and
 * offers it as a button, rather than leaving it to the small + in the corner.
 */
@Composable
fun EmptyBusinessState(
    onCreate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .widthIn(max = 420.dp)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BrandIllustration(size = 190.dp) { NewBusinessIllustration() }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(Res.string.no_business_found),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.create_first_business),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        AppButton(text = "ساخت کسب‌وکار", onClick = onCreate)
    }
}

/**
 * A storefront held still on the brand disc — the motion is around it, the
 * same as the login header: orbiting dots, and the hours and booking link a
 * new business is about to get floating beside it.
 */
@Composable
private fun NewBusinessIllustration() {
    IllustrationStage {
        OrbitDots()
        Icon(
            imageVector = Icons.Rounded.Storefront,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(62.dp)
        )
        FloatingChip(
            icon = Icons.Rounded.Schedule,
            modifier = Modifier.align(Alignment.TopStart).offset(x = 22.dp, y = 30.dp)
        )
        FloatingChip(
            icon = Icons.Rounded.Link,
            phaseMillis = 900,
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-22).dp, y = (-30).dp)
        )
    }
}

@Composable
fun HandleEvents(
    events: Flow<BusinessListEvent>,
    onNavigateToMain: (Business) -> Unit,
    onNavigateToCreateBusiness: () -> Unit,
    onNavigateToEditBusiness: (Long) -> Unit,
    onNavigateToLogin: () -> Unit,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    onRetry: () -> Unit
) {
    val scope = rememberCoroutineScope()
    events.collectWithLifecycleAware {
        when (it) {
            is BusinessListEvent.NavigateToMain -> {
                scope.launch {
                    onNavigateToMain(it.business)
                }
            }

            BusinessListEvent.NavigateToCreateBusiness -> {
                scope.launch {
                    onNavigateToCreateBusiness()
                }
            }

            is BusinessListEvent.NavigateToEditBusiness -> {
                scope.launch {
                    onNavigateToEditBusiness(it.businessId)
                }
            }

            BusinessListEvent.NavigateToLogin -> {
                scope.launch {
                    onNavigateToLogin()
                }
            }

            is BusinessListEvent.ShowMessage -> {
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = it.message,
                        actionLabel = "تلاش مجدد",
                        duration = androidx.compose.material3.SnackbarDuration.Long
                    )
                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                        onRetry()
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewHomeScreen() {
    AppTheme {
        BusinessListScreenContent(
            uiState = BusinessListState(),
            onIntent = {},
            onNavigateToLogin = {},
            snackbarHostState = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() },
            onDeleteRequest = {}
        )
    }
}
