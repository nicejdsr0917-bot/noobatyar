package xyz.sattar.javid.proqueue.feature.explore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import xyz.sattar.javid.proqueue.feature.discovery.bookingUrl
import xyz.sattar.javid.proqueue.feature.discovery.tehranNow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.location.LocationResult
import xyz.sattar.javid.proqueue.core.location.rememberLocationRequester
import xyz.sattar.javid.proqueue.core.ui.components.PullToRefreshBox
import xyz.sattar.javid.proqueue.core.ui.components.ShimmerBox
import xyz.sattar.javid.proqueue.core.utils.openUrl
import xyz.sattar.javid.proqueue.domain.model.business.Insurance
import xyz.sattar.javid.proqueue.feature.discovery.Brand
import xyz.sattar.javid.proqueue.feature.discovery.BusinessListCard
import xyz.sattar.javid.proqueue.feature.discovery.CategoryGroup
import xyz.sattar.javid.proqueue.feature.discovery.FeaturedBusinessCard
import xyz.sattar.javid.proqueue.feature.discovery.OWNER_LANDING_URL
import xyz.sattar.javid.proqueue.feature.discovery.OwnerInviteBanner
import xyz.sattar.javid.proqueue.feature.discovery.SectionHeader
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits

/** Where in the result list the "register your business" banner is slotted. */
private const val OWNER_BANNER_INDEX = 4

@Composable
fun ExploreScreen(
    onNavigateToDetail: (Long) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
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

    // Already-permitted users get "near me" straight away; everyone else sees the
    // prompt card first instead of a cold permission dialog on launch.
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

    val heroScrolledAway by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    Box(Modifier.fillMaxSize()) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = bottomPadding + 24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "hero") {
                Hero(
                    state = state,
                    onQueryChange = viewModel::updateQuery,
                    onLocationClick = { showLocationSheet = true },
                    onSubmit = submit,
                    onFocusChange = { searchFocused = it },
                )
            }

            if (searchFocused && state.query.isBlank()) {
                item(key = "suggestions") {
                    SearchSuggestions(
                        recent = state.recentSearches,
                        onPick = submit,
                        onClearRecent = viewModel::clearRecentSearches,
                    )
                }
            }

            if (!state.hasLocation) {
                item(key = "location-prompt") {
                    LocationPrompt(
                        status = state.locationStatus,
                        onLocate = locate,
                        onPickCity = { showLocationSheet = true },
                        onOpenSettings = locator::openSettings,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            item(key = "categories") {
                CategoryRow(selected = state.category, onSelect = viewModel::selectCategory)
            }

            item(key = "filters") {
                FilterRow(
                    state = state,
                    onInsuranceClick = { showInsuranceSheet = true },
                    onToggleOnline = viewModel::toggleOnlyOnline,
                    onSort = viewModel::selectSort,
                )
            }

            if (state.featured.isNotEmpty() && state.query.isBlank() && !state.onlyOnline) {
                item(key = "featured-header") {
                    SectionHeader(
                        title = "منتخب نوبت‌یار",
                        subtitle = "رزرو آنی، بدون تماس و انتظار",
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp)
                    )
                }
                item(key = "featured") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.featured, key = { it.id }) { b ->
                            FeaturedBusinessCard(b, onClick = { openDetail(b.id) })
                        }
                    }
                }
            }

            item(key = "results-header") {
                SectionHeader(
                    title = resultsTitle(state),
                    subtitle = if (!state.isLoading && state.errorMessage == null)
                        "${state.totalCount.toPersianDigits()} مرکز پیدا شد" else null,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 10.dp),
                    action = if (state.hasActiveFilters) {
                        { TextButton(onClick = viewModel::clearFilters) { Text("حذف فیلترها") } }
                    } else null
                )
            }

            when {
                state.isLoading -> items(5, key = { "shimmer-$it" }) { CardShimmer() }

                state.errorMessage != null && state.results.isEmpty() -> item(key = "error") {
                    MessageBlock(
                        icon = Icons.Rounded.CloudOff,
                        title = "ارتباط برقرار نشد",
                        subtitle = state.errorMessage ?: "",
                        actionLabel = "تلاش دوباره",
                        onAction = viewModel::refresh
                    )
                }

                state.results.isEmpty() -> item(key = "empty") {
                    MessageBlock(
                        icon = Icons.Rounded.SearchOff,
                        title = "مرکزی پیدا نشد",
                        subtitle = if (state.hasLocation && state.radiusKm < radiusOptionsKm.last())
                            "محدوده جستجو را بزرگ‌تر کنید یا فیلترها را تغییر دهید."
                        else "عبارت دیگری را امتحان کنید یا فیلترها را بردارید.",
                        actionLabel = if (state.hasLocation && state.radiusKm < radiusOptionsKm.last())
                            "جستجو تا ${radiusOptionsKm.last().toInt().toPersianDigits()} کیلومتر" else "حذف فیلترها",
                        onAction = {
                            if (state.hasLocation && state.radiusKm < radiusOptionsKm.last()) {
                                viewModel.selectRadius(radiusOptionsKm.last())
                            } else viewModel.clearFilters()
                        }
                    )
                    OwnerInviteBanner(
                        onClick = { openUrl(OWNER_LANDING_URL) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                else -> {
                    itemsIndexed(state.results, key = { _, b -> b.id }) { index, business ->
                        Column(
                            modifier = Modifier
                                .animateItem()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            BusinessListCard(
                                business = business,
                                saved = business.id in state.savedIds,
                                onToggleSave = { viewModel.toggleSaved(business.id) },
                                onClick = { openDetail(business.id) },
                                onBook = bookingUrl(business)?.let { url -> { openUrl(url) } }
                            )
                            if (index == OWNER_BANNER_INDEX ||
                                (index == state.results.lastIndex && state.results.size <= OWNER_BANNER_INDEX)
                            ) {
                                Spacer(Modifier.height(12.dp))
                                OwnerInviteBanner(onClick = { openUrl(OWNER_LANDING_URL) })
                            }
                        }
                    }
                    if (state.isLoadingMore) {
                        item(key = "more") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                            }
                        }
                    }
                }
            }
        }
    }

    PinnedSearchBar(
        visible = heroScrolledAway,
        query = state.query,
        onQueryChange = viewModel::updateQuery,
        onSubmit = submit,
    )
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

// ── Hero ─────────────────────────────────────────────────────────────────────

@Composable
private fun Hero(
    state: ExploreState,
    onQueryChange: (String) -> Unit,
    onLocationClick: () -> Unit,
    onSubmit: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Brand.HeroGradient)
    ) {
        // Soft decorative glows.
        Box(
            Modifier
                .padding(start = 220.dp, top = 10.dp)
                .size(180.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
        )
        Box(
            Modifier
                .padding(top = 120.dp)
                .size(120.dp)
                .clip(CircleShape)
                .background(Color(0xFFEC4899).copy(alpha = 0.12f))
        )

        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        greeting(),
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "امروز دنبال چه می‌گردی؟",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            Surface(
                onClick = onLocationClick,
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.16f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val area = state.area
                    val (icon, label) = when {
                        state.locationStatus == LocationStatus.Locating -> Icons.Rounded.MyLocation to "در حال یافتن موقعیت…"
                        area is SearchArea.NearMe -> Icons.Rounded.MyLocation to
                            "نزدیک من · تا ${state.radiusKm.toInt().toPersianDigits()} کیلومتر"
                        area is SearchArea.City -> Icons.Rounded.LocationOn to area.label
                        else -> Icons.Rounded.Public to "همه شهرها"
                    }
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(2.dp))
                    Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(14.dp))
            SearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                onSubmit = onSubmit,
                onFocusChange = onFocusChange,
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit = {},
    elevation: androidx.compose.ui.unit.Dp = 6.dp,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = elevation,
        modifier = Modifier.fillMaxWidth().height(54.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = Brand.Violet500)
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        "نام پزشک، سالن، تخصص یا محله…",
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
                    cursorBrush = SolidColor(Brand.Violet500),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit(query) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { onFocusChange(it.isFocused) }
                )
            }
            AnimatedVisibility(visible = query.isNotEmpty()) {
                Surface(
                    onClick = { onQueryChange("") },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, contentDescription = "پاک کردن", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

// ── Location prompt ──────────────────────────────────────────────────────────

@Composable
private fun LocationPrompt(
    status: LocationStatus,
    onLocate: () -> Unit,
    onPickCity: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (title, body) = when (status) {
        LocationStatus.Denied -> "دسترسی به موقعیت رد شد" to
            "بدون موقعیت هم می‌توانید شهرتان را انتخاب کنید، یا دسترسی را از تنظیمات فعال کنید."
        LocationStatus.Disabled -> "موقعیت‌یاب گوشی خاموش است" to
            "برای دیدن نزدیک‌ترین مراکز، GPS را روشن کنید."
        LocationStatus.Unavailable -> "موقعیت شما پیدا نشد" to
            "کمی بعد دوباره امتحان کنید یا شهرتان را انتخاب کنید."
        else -> "نزدیک‌ترین‌ها را ببینید" to
            "با اجازه دسترسی به موقعیت، مراکز اطرافتان به ترتیب فاصله نمایش داده می‌شوند."
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Brand.Violet500.copy(alpha = 0.08f),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Brand.Violet500.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Brand.Violet500.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (status == LocationStatus.Idle || status == LocationStatus.Locating) Icons.Rounded.MyLocation
                        else Icons.Rounded.LocationOff,
                        contentDescription = null,
                        tint = Brand.Violet500
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (status) {
                    LocationStatus.Denied, LocationStatus.Disabled -> Button(
                        onClick = onOpenSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = Brand.Violet500)
                    ) { Text("باز کردن تنظیمات") }
                    else -> Button(
                        onClick = onLocate,
                        enabled = status != LocationStatus.Locating,
                        colors = ButtonDefaults.buttonColors(containerColor = Brand.Violet500)
                    ) {
                        if (status == LocationStatus.Locating) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("استفاده از موقعیت من")
                    }
                }
                OutlinedButton(onClick = onPickCity) { Text("انتخاب شهر") }
            }
        }
    }
}

// ── Categories & filters ─────────────────────────────────────────────────────

@Composable
private fun CategoryRow(selected: CategoryGroup, onSelect: (CategoryGroup) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(CategoryGroup.entries) { group ->
            val isSelected = group == selected
            val bg by animateColorAsState(if (isSelected) group.tint else group.tint.copy(alpha = 0.12f))
            val fg by animateColorAsState(if (isSelected) Color.White else group.tint)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(72.dp)
            ) {
                Surface(
                    onClick = { onSelect(group) },
                    shape = RoundedCornerShape(20.dp),
                    color = bg,
                    modifier = Modifier.size(60.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(group.icon, contentDescription = group.label, tint = fg, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    group.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun FilterRow(
    state: ExploreState,
    onInsuranceClick: () -> Unit,
    onToggleOnline: () -> Unit,
    onSort: (SortMode) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChipX(
                label = state.selectedInsurance?.name ?: "بیمه تکمیلی",
                icon = Icons.Rounded.HealthAndSafety,
                selected = state.insuranceSlug != null,
                trailing = Icons.Rounded.ExpandMore,
                onClick = onInsuranceClick
            )
        }
        item {
            FilterChipX(
                label = "فقط نوبت آنلاین",
                icon = Icons.Rounded.Bolt,
                selected = state.onlyOnline,
                onClick = onToggleOnline
            )
        }
        if (state.hasLocation) {
            items(SortMode.entries) { mode ->
                FilterChipX(
                    label = mode.label,
                    icon = if (mode == SortMode.DISTANCE) Icons.Rounded.MyLocation else Icons.Rounded.Tune,
                    selected = state.sort == mode,
                    onClick = { onSort(mode) }
                )
            }
        }
    }
}

@Composable
private fun FilterChipX(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    trailing: ImageVector? = null,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(if (selected) Brand.Violet500 else MaterialTheme.colorScheme.surface)
    val fg by animateColorAsState(if (selected) Color.White else MaterialTheme.colorScheme.onSurface)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = bg,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) Color.White else Brand.Violet500, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            if (trailing != null) {
                Spacer(Modifier.width(2.dp))
                Icon(trailing, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ── States ───────────────────────────────────────────────────────────────────

@Composable
private fun CardShimmer() {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        ShimmerBox(Modifier.size(64.dp), RoundedCornerShape(16.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            ShimmerBox(Modifier.fillMaxWidth(0.6f).height(16.dp))
            Spacer(Modifier.height(8.dp))
            ShimmerBox(Modifier.fillMaxWidth(0.35f).height(12.dp))
            Spacer(Modifier.height(10.dp))
            ShimmerBox(Modifier.fillMaxWidth(0.5f).height(20.dp), RoundedCornerShape(50))
        }
    }
}

@Composable
private fun MessageBlock(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(84.dp).clip(CircleShape).background(Brand.Violet500.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Brand.Violet500, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAction, colors = ButtonDefaults.buttonColors(containerColor = Brand.Violet500)) {
            Text(actionLabel)
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
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("کجا دنبالش بگردیم؟", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            SheetRow(
                icon = Icons.Rounded.MyLocation,
                title = "موقعیت فعلی من",
                subtitle = when (state.locationStatus) {
                    LocationStatus.Locating -> "در حال یافتن…"
                    LocationStatus.Denied -> "دسترسی داده نشده"
                    LocationStatus.Disabled -> "GPS خاموش است"
                    LocationStatus.Unavailable -> "موقعیت پیدا نشد؛ دوباره امتحان کنید"
                    else -> "مرتب‌سازی بر اساس فاصله از شما"
                },
                selected = state.area is SearchArea.NearMe,
                onClick = onUseMyLocation,
            )

            if (state.hasLocation) {
                Spacer(Modifier.height(16.dp))
                Text("محدوده جستجو", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    radiusOptionsKm.forEach { km ->
                        FilterChipX(
                            label = "${km.toInt().toPersianDigits()} کیلومتر",
                            icon = Icons.Rounded.LocationOn,
                            selected = state.radiusKm == km,
                            onClick = { onSelectRadius(km) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("یا یک شهر انتخاب کنید", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                majorCities.forEach { city ->
                    FilterChipX(
                        label = city.label,
                        icon = Icons.Rounded.LocationOn,
                        selected = state.area == city,
                        onClick = { onSelectArea(city) }
                    )
                }
                FilterChipX(
                    label = "همه شهرها",
                    icon = Icons.Rounded.Public,
                    selected = state.area is SearchArea.Anywhere,
                    onClick = { onSelectArea(SearchArea.Anywhere) }
                )
            }
        }
    }
}

@Composable
private fun SheetRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Brand.Violet500.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Brand.Violet500)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Brand.Violet500)
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
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            modifier = Modifier.navigationBarsPadding()
        ) {
            item {
                Text("بیمه شما", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "مراکزی نمایش داده می‌شوند که این بیمه را اعلام کرده‌اند. پیش از مراجعه با مرکز هماهنگ کنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                InsuranceRow("همه بیمه‌ها", selected = selectedSlug == null) { onSelect(null) }
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
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                    }
                    items(list, key = { it.slug }) { ins ->
                        InsuranceRow(ins.name, selected = ins.slug == selectedSlug) { onSelect(ins.slug) }
                    }
                }
            }
        }
    }
}

@Composable
private fun InsuranceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Brand.Violet500)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        }
    }
}

private fun greeting(): String = when (tehranNow().hour) {
    in 5..11 -> "صبح بخیر ☀️"
    in 12..16 -> "ظهر بخیر 👋"
    in 17..20 -> "عصر بخیر 🌇"
    else -> "شب بخیر 🌙"
}

/** Common searches shown before the user types — a nudge, not a taxonomy. */
private val popularSearches = listOf(
    "دندان‌پزشک", "متخصص پوست", "آرایشگاه زنانه", "ارتودنسی",
    "روان‌شناس", "فیزیوتراپی", "کاشت مو", "آزمایشگاه", "متخصص اطفال", "ناخن",
)

@Composable
private fun SearchSuggestions(
    recent: List<String>,
    onPick: (String) -> Unit,
    onClearRecent: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (recent.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "جستجوهای اخیر",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClearRecent) { Text("پاک کردن") }
            }
            recent.forEach { q ->
                Surface(
                    onClick = { onPick(q) },
                    color = Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(q, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Text(
            "جستجوهای پرطرفدار",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            popularSearches.forEach { q ->
                FilterChipX(label = q, icon = Icons.AutoMirrored.Rounded.TrendingUp, selected = false, onClick = { onPick(q) })
            }
        }
    }
}

/** Compact search bar that slides in once the hero has scrolled off, so search is always one tap away. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.PinnedSearchBar(
    visible: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brand.HeroGradient)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            SearchField(query = query, onQueryChange = onQueryChange, onSubmit = onSubmit, elevation = 0.dp)
        }
    }
}
