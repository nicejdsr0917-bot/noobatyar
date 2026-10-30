package xyz.sattar.javid.proqueue.feature.explore

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.location.LocationResult
import xyz.sattar.javid.proqueue.core.prefs.ALL_IRAN
import xyz.sattar.javid.proqueue.core.location.rememberLocationRequester
import xyz.sattar.javid.proqueue.core.ui.components.PullToRefreshBox
import xyz.sattar.javid.proqueue.core.ui.components.map.mapBackground
import xyz.sattar.javid.proqueue.ui.theme.LocalIsDarkTheme
import xyz.sattar.javid.proqueue.core.utils.openUrl
import xyz.sattar.javid.proqueue.feature.discovery.BookableCard
import xyz.sattar.javid.proqueue.feature.discovery.BusinessRow
import xyz.sattar.javid.proqueue.feature.discovery.CategoryGroup
import xyz.sattar.javid.proqueue.feature.discovery.Hairline
import xyz.sattar.javid.proqueue.core.config.OWNER_LANDING_URL
import xyz.sattar.javid.proqueue.feature.discovery.OwnerInviteRow
import xyz.sattar.javid.proqueue.feature.discovery.SectionTitle
import xyz.sattar.javid.proqueue.feature.discovery.Space
import xyz.sattar.javid.proqueue.feature.discovery.StateMessage
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits



/** Where in the result list the owner invitation is slotted. */
internal const val OWNER_ROW_INDEX = 4

/** Avatar (56) + gap (12) + page margin (16): dividers start under the text, not the avatar. */
internal val ROW_DIVIDER_INSET = 84.dp

/** Share of the map area the collapsed sheet covers: enough for ~2 rows, the rest stays map. */
internal const val SHEET_PEEK_FRACTION = 0.44f

internal val SHEET_CORNER = 24.dp

/** How far below the toolbar the sheet starts merging into it. */
internal val MERGE_DISTANCE = 56.dp

/**
 * Off gives a plain list instead: no map, no location picker and no GPS — the
 * area stays [SearchArea.Anywhere], so every public business is listed, newest
 * first, with pull to refresh.
 */
internal const val MAP_ENABLED = true

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

internal fun resultsTitle(state: ExploreState): String {
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
internal fun ExploreState.extraFilterCount(): Int =
    listOf(insuranceSlug != null, onlyOnline).count { it }

// ── Sheet: the results ───────────────────────────────────────────────────────


@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ResultsList(
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
