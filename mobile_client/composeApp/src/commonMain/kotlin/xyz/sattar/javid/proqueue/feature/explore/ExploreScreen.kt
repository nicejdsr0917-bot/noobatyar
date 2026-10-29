package xyz.sattar.javid.proqueue.feature.explore

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.location.LocationResult
import xyz.sattar.javid.proqueue.core.prefs.ALL_IRAN
import xyz.sattar.javid.proqueue.core.location.rememberLocationRequester
import xyz.sattar.javid.proqueue.core.ui.components.PullToRefreshBox
import xyz.sattar.javid.proqueue.core.ui.components.ShimmerBox
import xyz.sattar.javid.proqueue.core.ui.components.map.mapBackground
import xyz.sattar.javid.proqueue.ui.theme.LocalIsDarkTheme
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

/** Share of the map area the collapsed sheet covers: enough for ~2 rows, the rest stays map. */
private const val SHEET_PEEK_FRACTION = 0.44f

private val SHEET_CORNER = 24.dp

/** How far below the toolbar the sheet starts merging into it. */
private val MERGE_DISTANCE = 56.dp

/**
 * Off gives a plain list instead: no map, no location picker and no GPS — the
 * area stays [SearchArea.Anywhere], so every public business is listed, newest
 * first, with pull to refresh.
 */
private const val MAP_ENABLED = true

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onNavigateToDetail: (Long) -> Unit,
    bottomPadding: Dp,
    viewModel: ExploreViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var showLocationSheet by remember { mutableStateOf(false) }
    var showInsuranceSheet by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }
    val selectedId = remember { mutableStateOf<Long?>(null) }

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
    // A new search drops a selection that may no longer be in the list.
    LaunchedEffect(state.results) {
        if (state.results.none { it.id == selectedId.value }) selectedId.value = null
    }

    // The map already gives the overview, so the "book online" strip would only
    // push the first results out of the collapsed sheet. Kept behind a flag for now.
    val showFeatured = false
    val sheetState = rememberBottomSheetScaffoldState()
    var pickMode by remember { mutableStateOf(false) }

    // A pin tap highlights its row and brings it to the top of the sheet; a tap
    // on bare map (null) clears the selection.
    val selectFromMap: (Long?) -> Unit = { id ->
        selectedId.value = id
        val index = if (id == null) -1 else state.results.indexOfFirst { it.id == id }
        if (index >= 0) {
            val before = (if (!state.hasLocation) 1 else 0) + 1 + (if (showFeatured) 2 else 0)
            scope.launch {
                if (sheetState.bottomSheetState.currentValue != SheetValue.PartiallyExpanded) {
                    sheetState.bottomSheetState.partialExpand()
                }
                listState.animateScrollToItem(before + index)
            }
        }
    }
    val searchMapArea: (MapArea) -> Unit = { area ->
        pickMode = false
        selectedId.value = null
        viewModel.searchAround(area.center.latitude, area.center.longitude, area.radiusKm)
        scope.launch { listState.scrollToItem(0) }
    }

    // 0 while the sheet sits low, 1 once its top reaches the toolbar. Drives the
    // "merge": the corners flatten, the handle and shadow fade, and the toolbar
    // takes over the divider, so header and list read as one surface.
    val mergeDistancePx = with(LocalDensity.current) { MERGE_DISTANCE.toPx() }
    val merge by remember {
        derivedStateOf {
            val offset = runCatching { sheetState.bottomSheetState.requireOffset() }.getOrDefault(Float.MAX_VALUE)
            (1f - offset / mergeDistancePx).coerceIn(0f, 1f)
        }
    }
    val dark = LocalIsDarkTheme.current

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Header(
            state = state,
            merged = if (MAP_ENABLED) merge else 1f,
            showLocation = MAP_ENABLED,
            onLocationClick = { showLocationSheet = true },
            onQueryChange = viewModel::updateQuery,
            onSubmit = submit,
            onFocusChange = { searchFocused = it },
            onOpenFilters = { showFilterSheet = true },
            onSelectCategory = viewModel::selectCategory,
        )

        if (!MAP_ENABLED) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // Pull to refresh: the way to pick up a business just added in the owner app.
                PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = viewModel::refresh) {
                ResultsList(
                    state = state,
                    listState = listState,
                    showFeatured = false,
                    showLocationNotice = false,
                    selectedId = null,
                    bottomPadding = bottomPadding,
                    onLocate = locate,
                    onOpenSettings = locator::openSettings,
                    onOpenDetail = openDetail,
                    onClearFilters = viewModel::clearFilters,
                    onRetry = viewModel::refresh,
                    onWiden = {},
                    onToggleSaved = viewModel::toggleSaved,
                )
                }
                if (searchFocused && state.query.isBlank()) {
                    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            SearchSuggestions(state.recentSearches, onPick = submit, onClearRecent = viewModel::clearRecentSearches)
                        }
                    }
                }
            }
        } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val peek = maxHeight * SHEET_PEEK_FRACTION
            val hiddenSheetHeight = maxHeight - peek
            val corner = SHEET_CORNER * (1f - merge)
            BottomSheetScaffold(
                scaffoldState = sheetState,
                sheetPeekHeight = peek + bottomPadding,
                sheetContainerColor = MaterialTheme.colorScheme.background,
                sheetShadowElevation = 16.dp * (1f - merge),
                sheetShape = RoundedCornerShape(topStart = corner, topEnd = corner),
                sheetDragHandle = { SheetHandle(alpha = 1f - merge) },
                sheetContent = {
                    ResultsList(
                        state = state,
                        listState = listState,
                        showFeatured = showFeatured,
                        // City first: no GPS nudge above the list.
                        showLocationNotice = false,
                        selectedId = selectedId.value,
                        // The list is as tall as the expanded sheet but only `peek` of it
                        // shows when collapsed: the extra room lets a pin's row (even the
                        // last one) scroll up into that visible strip.
                        bottomPadding = bottomPadding + hiddenSheetHeight,
                        onLocate = locate,
                        onOpenSettings = locator::openSettings,
                        onOpenDetail = openDetail,
                        onClearFilters = viewModel::clearFilters,
                        onRetry = viewModel::refresh,
                        onWiden = { viewModel.selectRadius(radiusOptionsKm.last()) },
                        onToggleSaved = viewModel::toggleSaved,
                    )
                },
                containerColor = mapBackground(dark),
            ) {
                ExploreMap(
                    businesses = state.results,
                    area = state.area,
                    radiusKm = state.radiusKm,
                    selectedId = selectedId,
                    onSelect = selectFromMap,
                    onSearchArea = searchMapArea,
                    onLocate = locate,
                    locating = state.locationStatus == LocationStatus.Locating,
                    pickMode = pickMode,
                    onTogglePickMode = {
                        pickMode = !pickMode
                        if (pickMode) {
                            selectedId.value = null
                            scope.launch { sheetState.bottomSheetState.partialExpand() }
                        }
                    },
                    visibleFraction = 1f - SHEET_PEEK_FRACTION,
                    bottomInset = peek,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Search suggestions sit over the map rather than replacing it, so the
            // map (and its downloaded tiles) survive a trip into the search field.
            if (searchFocused && state.query.isBlank()) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        SearchSuggestions(state.recentSearches, onPick = submit, onClearRecent = viewModel::clearRecentSearches)
                    }
                }
            }
        }
    }

    // City first, like Divar: on first launch the picker opens by itself, and
    // closing it without a pick settles on "کل ایران" so it doesn't nag again.
    if (showLocationSheet || state.needsCityPick) {
        CityPickerSheet(
            selectedCode = (state.area as? SearchArea.City)?.code
                ?: ALL_IRAN.takeIf { state.area is SearchArea.Anywhere && !state.needsCityPick },
            recentCodes = state.recentCityCodes,
            onDismiss = {
                showLocationSheet = false
                if (state.needsCityPick) viewModel.selectCity(SearchArea.Anywhere)
            },
            onPick = {
                viewModel.selectCity(it)
                showLocationSheet = false
                scope.launch { listState.scrollToItem(0) }
            },
        )
    }
    if (showFilterSheet) {
        FilterSheet(
            state = state,
            onDismiss = { showFilterSheet = false },
            onSelectRadius = viewModel::selectRadius,
            onSelectSort = viewModel::selectSort,
            onToggleOnline = viewModel::toggleOnlyOnline,
            onOpenInsurance = { showInsuranceSheet = true },
            onClear = viewModel::clearFilters,
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
        is SearchArea.Picked -> "$what در این محدوده"
        SearchArea.Anywhere -> if (state.query.isNotBlank()) "نتایج جستجو" else "همه $what"
    }
}

/** How many filters beyond the category row are on — the badge on the filter button.
 *  Sort isn't counted: "near me" switches it to distance on its own. */
private fun ExploreState.extraFilterCount(): Int =
    listOf(insuranceSlug != null, onlyOnline).count { it }

// ── Sheet: the results ───────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultsList(
    state: ExploreState,
    listState: LazyListState,
    showFeatured: Boolean,
    showLocationNotice: Boolean,
    selectedId: Long?,
    bottomPadding: Dp,
    onLocate: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onClearFilters: () -> Unit,
    onRetry: () -> Unit,
    onWiden: () -> Unit,
    onToggleSaved: (Long) -> Unit,
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = bottomPadding + Space.xl),
        modifier = Modifier.fillMaxSize()
    ) {
        // Keep this prefix in step with `before` in ExploreScreen's selectFromMap.
        if (showLocationNotice && !state.hasLocation) {
            item(key = "location-notice") {
                LocationNotice(status = state.locationStatus, onLocate = onLocate, onOpenSettings = onOpenSettings)
            }
        }

        item(key = "results-title") {
            SectionTitle(
                title = resultsTitle(state),
                subtitle = if (!state.isLoading && state.errorMessage == null)
                    "${state.totalCount.toPersianDigits()} نتیجه" else null,
                modifier = Modifier.padding(start = Space.page, end = Space.sm, top = Space.xs, bottom = Space.xs),
                action = if (state.hasActiveFilters) {
                    { TextButton(onClick = onClearFilters) { Text("پاک کردن فیلترها") } }
                } else null
            )
        }

        if (showFeatured) {
            item(key = "featured-title") {
                Text(
                    if (state.hasLocation) "نوبت آنلاین در نزدیکی شما" else "نوبت آنلاین",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Space.page, end = Space.page, top = Space.sm, bottom = Space.sm)
                )
            }
            item(key = "featured") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Space.page),
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                    modifier = Modifier.padding(bottom = Space.md)
                ) {
                    items(state.featured, key = { it.id }) { b -> BookableCard(b, onClick = { onOpenDetail(b.id) }) }
                }
            }
        }

        when {
            state.isLoading -> items(6, key = { "shimmer-$it" }) { RowShimmer() }

            state.errorMessage != null && state.results.isEmpty() -> item(key = "error") {
                StateMessage(
                    icon = Icons.Outlined.CloudOff,
                    title = "ارتباط برقرار نشد",
                    body = state.errorMessage ?: "",
                    actionLabel = "تلاش دوباره",
                    onAction = onRetry
                )
            }

            state.results.isEmpty() -> item(key = "empty") {
                // Only a radius search can be widened; a city is a city.
                val canWiden = (state.area is SearchArea.NearMe || state.area is SearchArea.Picked) &&
                    state.radiusKm < radiusOptionsKm.last()
                StateMessage(
                    icon = Icons.Outlined.SearchOff,
                    title = "نتیجه‌ای پیدا نشد",
                    body = if (canWiden) "محدوده جستجو را بزرگ‌تر کنید یا فیلترها را تغییر دهید."
                    else "عبارت دیگری را امتحان کنید یا فیلترها را بردارید.",
                    actionLabel = if (canWiden) "جستجو تا ${radiusOptionsKm.last().toInt().toPersianDigits()} کیلومتر"
                    else "پاک کردن فیلترها",
                    onAction = if (canWiden) onWiden else onClearFilters
                )
            }

            else -> {
                itemsIndexed(state.results, key = { _, b -> b.id }) { index, business ->
                    Column(Modifier.animateItem()) {
                        BusinessRow(
                            business = business,
                            saved = business.id in state.savedIds,
                            highlighted = business.id == selectedId,
                            onToggleSave = { onToggleSaved(business.id) },
                            onClick = { onOpenDetail(business.id) }
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

// ── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun Header(
    state: ExploreState,
    /** 0..1: how far the results sheet has merged into this toolbar. */
    merged: Float,
    showLocation: Boolean,
    onLocationClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onOpenFilters: () -> Unit,
    onSelectCategory: (CategoryGroup) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        if (showLocation) Surface(
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
                        area is SearchArea.Picked -> "روی نقشه · ${state.radiusKm.toInt().toPersianDigits()} کیلومتر"
                        else -> area.label
                    }
                    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Icon(Icons.Outlined.ExpandMore, contentDescription = "تغییر موقعیت", modifier = Modifier.size(20.dp))
                }
            }
        }
        Row(
            Modifier.padding(horizontal = Space.page, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                onSubmit = onSubmit,
                onFocusChange = onFocusChange,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(Space.sm))
            FilterButton(count = state.extraFilterCount(), onClick = onOpenFilters)
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = Space.page),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            modifier = Modifier.padding(bottom = Space.md)
        ) {
            items(CategoryGroup.entries) { group ->
                FilterChip(
                    label = group.label,
                    icon = group.icon,
                    selected = group == state.category,
                    onClick = { onSelectCategory(group) }
                )
            }
        }
        // Takes over from the sheet's shadow once the two have merged.
        Hairline(Modifier.alpha(merged))
    }
}

/** Square accent button beside the search field, with a count of filters that are on. */
@Composable
private fun FilterButton(count: Int, onClick: () -> Unit) {
    Box {
        Surface(
            onClick = onClick,
            shape = Radius.md,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Tune, contentDescription = "فیلترها", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        if (count > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(18.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(count.toPersianDigits(), color = MaterialTheme.colorScheme.onError, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Drag handle that fades out as the sheet merges into the toolbar. */
@Composable
private fun SheetHandle(alpha: Float) {
    Box(Modifier.fillMaxWidth().height(22.dp * alpha.coerceAtLeast(0.4f)), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = 36.dp, height = 4.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f * alpha), CircleShape)
        )
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

/**
 * Everything beyond the category row. Each choice applies at once — the list and
 * the pins behind the sheet update live — so the button only closes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    state: ExploreState,
    onDismiss: () -> Unit,
    onSelectRadius: (Double) -> Unit,
    onSelectSort: (SortMode) -> Unit,
    onToggleOnline: () -> Unit,
    onOpenInsurance: () -> Unit,
    onClear: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = Space.lg)) {
            Row(Modifier.padding(start = Space.page, end = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                Text("فیلترها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onClear) { Text("پاک کردن") }
            }

            FilterSection("نوبت‌دهی")
            Row(Modifier.padding(horizontal = Space.page), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                FilterChip(label = "فقط نوبت آنلاین", icon = Icons.Outlined.Bolt, selected = state.onlyOnline, onClick = onToggleOnline)
            }

            FilterSection("بیمه")
            Row(Modifier.padding(horizontal = Space.page)) {
                FilterChip(
                    label = state.selectedInsurance?.name ?: "همه بیمه‌ها",
                    icon = Icons.Outlined.HealthAndSafety,
                    trailingIcon = Icons.Outlined.ExpandMore,
                    selected = state.insuranceSlug != null,
                    onClick = onOpenInsurance
                )
            }

            if (state.hasLocation) {
                FilterSection(if (state.area is SearchArea.NearMe) "فاصله تا من" else "فاصله تا مرکز ${state.area.label}")
                Row(Modifier.padding(horizontal = Space.page), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    radiusOptionsKm.forEach { km ->
                        FilterChip(
                            label = "${km.toInt().toPersianDigits()} کیلومتر",
                            selected = state.radiusKm == km,
                            onClick = { onSelectRadius(km) }
                        )
                    }
                }

                FilterSection("مرتب‌سازی")
                Row(Modifier.padding(horizontal = Space.page), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    SortMode.entries.forEach { mode ->
                        FilterChip(label = mode.label, selected = state.sort == mode, onClick = { onSelectSort(mode) })
                    }
                }
            }

            Button(
                onClick = onDismiss,
                shape = Radius.md,
                modifier = Modifier.fillMaxWidth().padding(start = Space.page, end = Space.page, top = Space.xl).height(52.dp)
            ) {
                Text(
                    if (state.isLoading) "نمایش نتایج" else "نمایش ${state.totalCount.toPersianDigits()} نتیجه",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FilterSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Space.page, top = Space.lg, bottom = Space.sm)
    )
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
