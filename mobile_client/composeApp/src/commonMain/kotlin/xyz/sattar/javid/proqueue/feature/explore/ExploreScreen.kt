package xyz.sattar.javid.proqueue.feature.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.location.LocationResult
import xyz.sattar.javid.proqueue.core.location.rememberLocationRequester
import xyz.sattar.javid.proqueue.core.ui.components.ShimmerBox
import xyz.sattar.javid.proqueue.core.utils.openUrl
import xyz.sattar.javid.proqueue.domain.model.business.Insurance
import xyz.sattar.javid.proqueue.feature.discovery.BookableCard
import xyz.sattar.javid.proqueue.feature.discovery.BusinessRow
import xyz.sattar.javid.proqueue.feature.discovery.CategoryGroup
import xyz.sattar.javid.proqueue.feature.discovery.FilterChip
import xyz.sattar.javid.proqueue.feature.discovery.Hairline
import xyz.sattar.javid.proqueue.feature.discovery.OWNER_LANDING_URL
import xyz.sattar.javid.proqueue.feature.discovery.OwnerInviteRow
import xyz.sattar.javid.proqueue.feature.discovery.Radius
import xyz.sattar.javid.proqueue.feature.discovery.SectionTitle
import xyz.sattar.javid.proqueue.feature.discovery.Space
import xyz.sattar.javid.proqueue.feature.discovery.StateMessage
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits

/** Where in the result list the owner invitation is slotted. */
private const val OWNER_ROW_INDEX = 4

/** Avatar (56) + gap (12) + page margin (16): dividers start under the text, not the avatar. */
private val ROW_DIVIDER_INSET = 84.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExploreScreen(
    onNavigateToDetail: (Long) -> Unit,
    bottomPadding: Dp,
    viewModel: ExploreViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val focus = LocalFocusManager.current
    var showLocationSheet by remember { mutableStateOf(false) }
    var showInsuranceSheet by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }

    val openDetail: (Long) -> Unit = { id ->
        viewModel.rememberQuery()
        onNavigateToDetail(id)
    }
    val submit: (String) -> Unit = { q ->
        viewModel.submitSearch(q)
        focus.clearFocus()
    }

    val locator = rememberLocationRequester { result ->
        viewModel.onLocationResult(result)
        if (result is LocationResult.Success) showLocationSheet = false
    }
    val locate = {
        viewModel.onLocating()
        locator.request()
    }

    // Already-permitted users get "near me" straight away; others see the notice
    // row instead of a cold permission dialog.
    var autoLocated by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!autoLocated && locator.isPermissionGranted()) {
            autoLocated = true
            locate()
        }
    }

    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(shouldLoadMore, state.nextPage) {
        if (shouldLoadMore) viewModel.loadMore()
    }
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = bottomPadding + Space.xl),
            modifier = Modifier.fillMaxSize()
        ) {
            stickyHeader(key = "header") {
                Header(
                    state = state,
                    showDivider = scrolled,
                    onLocationClick = { showLocationSheet = true },
                    onQueryChange = viewModel::updateQuery,
                    onSubmit = submit,
                    onFocusChange = { searchFocused = it },
                )
            }

            if (searchFocused && state.query.isBlank()) {
                item(key = "suggestions") {
                    SearchSuggestions(state.recentSearches, onPick = submit, onClearRecent = viewModel::clearRecentSearches)
                }
            }

            if (!state.hasLocation) {
                item(key = "location-notice") {
                    LocationNotice(
                        status = state.locationStatus,
                        onLocate = locate,
                        onOpenSettings = locator::openSettings,
                    )
                }
            }

            item(key = "categories") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Space.page),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    modifier = Modifier.padding(top = Space.md)
                ) {
                    items(CategoryGroup.entries) { group ->
                        FilterChip(
                            label = group.label,
                            icon = group.icon,
                            selected = group == state.category,
                            onClick = { viewModel.selectCategory(group) }
                        )
                    }
                }
            }

            item(key = "filters") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Space.page),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    modifier = Modifier.padding(top = Space.sm)
                ) {
                    item {
                        FilterChip(
                            label = state.selectedInsurance?.name ?: "بیمه",
                            icon = Icons.Outlined.HealthAndSafety,
                            trailingIcon = Icons.Outlined.ExpandMore,
                            selected = state.insuranceSlug != null,
                            onClick = { showInsuranceSheet = true }
                        )
                    }
                    item {
                        FilterChip(
                            label = "نوبت آنلاین",
                            icon = Icons.Outlined.Bolt,
                            selected = state.onlyOnline,
                            onClick = viewModel::toggleOnlyOnline
                        )
                    }
                    if (state.hasLocation) {
                        items(SortMode.entries) { mode ->
                            FilterChip(
                                label = mode.label,
                                selected = state.sort == mode,
                                onClick = { viewModel.selectSort(mode) }
                            )
                        }
                    }
                }
            }

            if (state.featured.isNotEmpty() && state.query.isBlank() && !state.onlyOnline) {
                item(key = "featured-title") {
                    SectionTitle(
                        title = if (state.hasLocation) "نوبت آنلاین در نزدیکی شما" else "نوبت آنلاین",
                        subtitle = "بدون تماس، همین حالا رزرو کنید",
                        modifier = Modifier.padding(start = Space.page, end = Space.page, top = Space.xl, bottom = Space.md)
                    )
                }
                item(key = "featured") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Space.page),
                        horizontalArrangement = Arrangement.spacedBy(Space.md)
                    ) {
                        items(state.featured, key = { it.id }) { b -> BookableCard(b, onClick = { openDetail(b.id) }) }
                    }
                }
            }

            item(key = "results-title") {
                SectionTitle(
                    title = resultsTitle(state),
                    subtitle = if (!state.isLoading && state.errorMessage == null)
                        "${state.totalCount.toPersianDigits()} نتیجه" else null,
                    modifier = Modifier.padding(start = Space.page, end = Space.sm, top = Space.xl, bottom = Space.xs),
                    action = if (state.hasActiveFilters) {
                        { TextButton(onClick = viewModel::clearFilters) { Text("پاک کردن فیلترها") } }
                    } else null
                )
            }

            when {
                state.isLoading -> items(6, key = { "shimmer-$it" }) { RowShimmer() }

                state.errorMessage != null && state.results.isEmpty() -> item(key = "error") {
                    StateMessage(
                        icon = Icons.Outlined.CloudOff,
                        title = "ارتباط برقرار نشد",
                        body = state.errorMessage ?: "",
                        actionLabel = "تلاش دوباره",
                        onAction = viewModel::refresh
                    )
                }

                state.results.isEmpty() -> item(key = "empty") {
                    val canWiden = state.hasLocation && state.radiusKm < radiusOptionsKm.last()
                    StateMessage(
                        icon = Icons.Outlined.SearchOff,
                        title = "نتیجه‌ای پیدا نشد",
                        body = if (canWiden) "محدوده جستجو را بزرگ‌تر کنید یا فیلترها را تغییر دهید."
                        else "عبارت دیگری را امتحان کنید یا فیلترها را بردارید.",
                        actionLabel = if (canWiden) "جستجو تا ${radiusOptionsKm.last().toInt().toPersianDigits()} کیلومتر"
                        else "پاک کردن فیلترها",
                        onAction = {
                            if (canWiden) viewModel.selectRadius(radiusOptionsKm.last()) else viewModel.clearFilters()
                        }
                    )
                }

                else -> {
                    itemsIndexed(state.results, key = { _, b -> b.id }) { index, business ->
                        Column(Modifier.animateItem()) {
                            BusinessRow(
                                business = business,
                                saved = business.id in state.savedIds,
                                onToggleSave = { viewModel.toggleSaved(business.id) },
                                onClick = { openDetail(business.id) }
                            )
                            if (index != state.results.lastIndex) Hairline(Modifier.padding(start = ROW_DIVIDER_INSET))
                            if (index == OWNER_ROW_INDEX ||
                                (index == state.results.lastIndex && state.results.size <= OWNER_ROW_INDEX)
                            ) {
                                OwnerInviteRow(
                                    onClick = { openUrl(OWNER_LANDING_URL) },
                                    modifier = Modifier.padding(horizontal = Space.page, vertical = Space.md)
                                )
                            }
                        }
                    }
                    if (state.isLoadingMore) {
                        item(key = "more") {
                            Box(Modifier.fillMaxWidth().padding(Space.lg), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLocationSheet) {
        LocationSheet(
            state = state,
            onDismiss = { showLocationSheet = false },
            onUseMyLocation = locate,
            onSelectArea = {
                viewModel.selectArea(it)
                showLocationSheet = false
            },
            onSelectRadius = viewModel::selectRadius,
        )
    }
    if (showInsuranceSheet) {
        InsuranceSheet(
            insurances = state.insurances,
            selectedSlug = state.insuranceSlug,
            onDismiss = { showInsuranceSheet = false },
            onSelect = {
                viewModel.selectInsurance(it)
                showInsuranceSheet = false
            }
        )
    }
}

private fun resultsTitle(state: ExploreState): String {
    val what = if (state.category == CategoryGroup.ALL) "مراکز" else state.category.label
    return when (val area = state.area) {
        is SearchArea.NearMe -> "$what نزدیک شما"
        is SearchArea.City -> "$what در ${area.label}"
        SearchArea.Anywhere -> if (state.query.isNotBlank()) "نتایج جستجو" else "همه $what"
    }
}

// ── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun Header(
    state: ExploreState,
    showDivider: Boolean,
    onLocationClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Surface(
            onClick = onLocationClick,
            color = Color.Transparent,
            modifier = Modifier.padding(start = Space.sm, top = Space.sm)
        ) {
            Column(Modifier.padding(horizontal = Space.sm, vertical = Space.xs)) {
                Text("جستجو در", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val area = state.area
                    val label = when {
                        state.locationStatus == LocationStatus.Locating -> "در حال یافتن موقعیت…"
                        area is SearchArea.NearMe -> "نزدیک من · ${state.radiusKm.toInt().toPersianDigits()} کیلومتر"
                        else -> area.label
                    }
                    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Icon(Icons.Outlined.ExpandMore, contentDescription = "تغییر موقعیت", modifier = Modifier.size(20.dp))
                }
            }
        }
        SearchField(
            query = state.query,
            onQueryChange = onQueryChange,
            onSubmit = onSubmit,
            onFocusChange = onFocusChange,
            modifier = Modifier.padding(horizontal = Space.page, vertical = Space.sm)
        )
        if (showDivider) Hairline() else Spacer(Modifier.height(1.dp))
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, Radius.md)
            .padding(horizontal = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Space.sm))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    "پزشک، سالن، تخصص یا محله",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit(query) }),
                modifier = Modifier.fillMaxWidth().onFocusChanged { onFocusChange(it.isFocused) }
            )
        }
        AnimatedVisibility(visible = query.isNotEmpty()) {
            Surface(onClick = { onQueryChange("") }, shape = CircleShape, color = Color.Transparent) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "پاک کردن",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Space.xs).size(18.dp)
                )
            }
        }
    }
}

// ── Inline states ────────────────────────────────────────────────────────────

@Composable
private fun LocationNotice(status: LocationStatus, onLocate: () -> Unit, onOpenSettings: () -> Unit) {
    val blocked = status == LocationStatus.Denied || status == LocationStatus.Disabled
    val text = when (status) {
        LocationStatus.Denied -> "دسترسی به موقعیت داده نشده است."
        LocationStatus.Disabled -> "موقعیت‌یاب گوشی خاموش است."
        LocationStatus.Unavailable -> "موقعیت شما پیدا نشد."
        LocationStatus.Locating -> "در حال یافتن موقعیت…"
        else -> "برای دیدن نزدیک‌ترین مراکز، موقعیت را فعال کنید."
    }
    Row(
        Modifier
            .padding(horizontal = Space.page, vertical = Space.xs)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, Radius.md)
            .padding(start = Space.md, end = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.MyLocation, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.md))
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(vertical = Space.md))
        if (status == LocationStatus.Locating) {
            CircularProgressIndicator(Modifier.padding(Space.md).size(18.dp), strokeWidth = 2.dp)
        } else {
            TextButton(onClick = if (blocked) onOpenSettings else onLocate) {
                Text(if (blocked) "تنظیمات" else "فعال‌سازی", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RowShimmer() {
    Row(Modifier.padding(horizontal = Space.page, vertical = Space.md)) {
        ShimmerBox(Modifier.size(56.dp), Radius.md)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            ShimmerBox(Modifier.fillMaxWidth(0.55f).height(14.dp), Radius.sm)
            Spacer(Modifier.height(Space.sm))
            ShimmerBox(Modifier.fillMaxWidth(0.4f).height(12.dp), Radius.sm)
            Spacer(Modifier.height(Space.sm))
            ShimmerBox(Modifier.fillMaxWidth(0.3f).height(12.dp), Radius.sm)
        }
    }
}

/** Common searches shown before the user types — a nudge, not a taxonomy. */
private val popularSearches = listOf(
    "دندان‌پزشک", "متخصص پوست", "آرایشگاه زنانه", "ارتودنسی",
    "روان‌شناس", "فیزیوتراپی", "کاشت مو", "آزمایشگاه", "متخصص اطفال",
)

@Composable
private fun SearchSuggestions(recent: List<String>, onPick: (String) -> Unit, onClearRecent: () -> Unit) {
    Column(Modifier.padding(vertical = Space.sm)) {
        if (recent.isNotEmpty()) {
            Row(Modifier.padding(start = Space.page, end = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "جستجوهای اخیر",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClearRecent) { Text("پاک کردن") }
            }
            recent.forEach { q -> SuggestionRow(Icons.Outlined.History, q) { onPick(q) } }
            Spacer(Modifier.height(Space.md))
        }
        Text(
            "پرجستجوها",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Space.page, vertical = Space.sm)
        )
        popularSearches.forEach { q -> SuggestionRow(Icons.AutoMirrored.Outlined.TrendingUp, q) { onPick(q) } }
        Hairline(Modifier.padding(top = Space.sm))
    }
}

@Composable
private fun SuggestionRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.height(44.dp).padding(horizontal = Space.page), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.lg))
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// ── Sheets ───────────────────────────────────────────────────────────────────

@Composable
private fun LocationSheet(
    state: ExploreState,
    onDismiss: () -> Unit,
    onUseMyLocation: () -> Unit,
    onSelectArea: (SearchArea) -> Unit,
    onSelectRadius: (Double) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = Space.lg)) {
            Text(
                "محدوده جستجو",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = Space.page, vertical = Space.sm)
            )
            SheetOption(
                icon = Icons.Outlined.MyLocation,
                title = "موقعیت فعلی من",
                subtitle = when (state.locationStatus) {
                    LocationStatus.Locating -> "در حال یافتن…"
                    LocationStatus.Denied -> "دسترسی داده نشده"
                    LocationStatus.Disabled -> "GPS خاموش است"
                    LocationStatus.Unavailable -> "پیدا نشد؛ دوباره امتحان کنید"
                    else -> "مرتب‌سازی بر اساس فاصله"
                },
                selected = state.area is SearchArea.NearMe,
                onClick = onUseMyLocation
            )
            if (state.hasLocation) {
                Text(
                    "شعاع",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Space.page, top = Space.lg, bottom = Space.sm)
                )
                Row(Modifier.padding(horizontal = Space.page), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    radiusOptionsKm.forEach { km ->
                        FilterChip(
                            label = "${km.toInt().toPersianDigits()} کیلومتر",
                            selected = state.radiusKm == km,
                            onClick = { onSelectRadius(km) }
                        )
                    }
                }
            }
            Text(
                "شهر",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.page, top = Space.xl, bottom = Space.sm)
            )
            FlowRow(
                Modifier.padding(horizontal = Space.page),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                majorCities.forEach { city ->
                    FilterChip(label = city.label, selected = state.area == city, onClick = { onSelectArea(city) })
                }
                FilterChip(
                    label = "همه شهرها",
                    icon = Icons.Outlined.Public,
                    selected = state.area is SearchArea.Anywhere,
                    onClick = { onSelectArea(SearchArea.Anywhere) }
                )
            }
        }
    }
}

@Composable
private fun SheetOption(icon: ImageVector, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = Space.page, vertical = Space.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Space.lg))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun InsuranceSheet(
    insurances: List<Insurance>,
    selectedSlug: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        LazyColumn(contentPadding = PaddingValues(bottom = Space.xl), modifier = Modifier.navigationBarsPadding()) {
            item {
                Column(Modifier.padding(horizontal = Space.page, vertical = Space.sm)) {
                    Text("بیمه", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "مراکزی که این بیمه را اعلام کرده‌اند. پیش از مراجعه با مرکز هماهنگ کنید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OptionRow("همه بیمه‌ها", selectedSlug == null) { onSelect(null) }
            }
            val groups = insurances.groupBy { it.insuranceType }
            listOf("SUPPLEMENTARY" to "بیمه‌های تکمیلی", "BASIC" to "بیمه‌های پایه").forEach { (type, title) ->
                val list = groups[type].orEmpty()
                if (list.isNotEmpty()) {
                    item(key = "h-$type") {
                        Text(
                            title,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = Space.page, top = Space.lg, bottom = Space.xs)
                        )
                    }
                    items(list, key = { it.slug }) { ins -> OptionRow(ins.name, ins.slug == selectedSlug) { onSelect(ins.slug) } }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.height(52.dp).padding(horizontal = Space.page), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
