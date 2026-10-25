package xyz.sattar.javid.proqueue.feature.explore

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.ui.components.BottomBarSpacer
import xyz.sattar.javid.proqueue.core.ui.components.PullToRefreshBox
import xyz.sattar.javid.proqueue.core.utils.AppConfig
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.domain.model.business.Insurance
import xyz.sattar.javid.proqueue.ui.theme.PrimaryPurple
import xyz.sattar.javid.proqueue.ui.theme.PrimaryPurpleDark

private val AmberAccent = Color(0xFFFBBF24)
private val GradientStart = Color(0xFF8B5CF6)
private val GradientEnd = Color(0xFF4C1D95)

private fun resolveLogoUrl(logoPath: String): String =
    if (logoPath.startsWith("http")) logoPath else "${AppConfig.baseUrl}$logoPath"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel = koinViewModel(),
    onNavigateToDetail: (businessId: Long) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    var showLocationSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ExploreEvent.NavigateToDetail -> onNavigateToDetail(event.businessId)
                is ExploreEvent.ShowToast -> Unit
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.sendIntent(ExploreIntent.Refresh) },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    // Header Brand & Title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "نوبت‌یار",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        brush = Brush.horizontalGradient(listOf(GradientStart, GradientEnd))
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(AmberAccent, CircleShape)
                                )
                            }
                            Text(
                                text = "جستجو و رزرو نوبت پزشکان، دندان‌پزشکان و سالن‌های زیبایی",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 1. Location Selector Bar
                item {
                    LocationSelectorBar(
                        selectedLocation = state.selectedLocation,
                        onClick = { showLocationSheet = true }
                    )
                }

                // 2. Supplementary Insurance Filter Bar («بیمه تکمیلی من»)
                item {
                    InsuranceFilterSection(
                        insurances = state.insurances,
                        selectedSlug = state.selectedInsuranceSlug,
                        onSelectInsurance = { slug ->
                            viewModel.sendIntent(ExploreIntent.SelectInsurance(slug))
                        }
                    )
                }

                // 3. Search Bar
                item {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { viewModel.sendIntent(ExploreIntent.UpdateSearchQuery(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = "جستجوی پزشک، سالن، نام متخصص یا تخصص...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "جستجو",
                                tint = PrimaryPurple
                            )
                        },
                        trailingIcon = {
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.sendIntent(ExploreIntent.UpdateSearchQuery("")) }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "پاک کردن")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryPurple,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                    )
                }

                // 4. Category Quick Filter Chips
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(defaultCategoryFilters) { cat ->
                            val isSelected = state.selectedCategory == cat.key
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.sendIntent(ExploreIntent.SelectCategory(cat.key)) },
                                label = {
                                    Text(
                                        text = cat.label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryPurple.copy(alpha = 0.15f),
                                    selectedLabelColor = PrimaryPurple,
                                    selectedLeadingIconColor = PrimaryPurple
                                ),
                                border = if (isSelected) {
                                    BorderStroke(1.5.dp, PrimaryPurple)
                                } else {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                }
                            )
                        }
                    }
                }

                // 5. Toggle «فقط دارای نوبت آنلاین نوبت‌یار ⚡»
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                viewModel.sendIntent(ExploreIntent.ToggleOnlyClaimed(!state.onlyClaimed))
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (state.onlyClaimed) {
                                PrimaryPurple.copy(alpha = 0.1f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            }
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (state.onlyClaimed) PrimaryPurple.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "⚡ فقط مراکز دارای نوبت آنلاین نوبت‌یار",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (state.onlyClaimed) PrimaryPurple else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = "امکان مشاهده تقویم کاری، انتخاب ساعت و رزرو آنی بدون معطلی",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.onlyClaimed,
                                onCheckedChange = { viewModel.sendIntent(ExploreIntent.ToggleOnlyClaimed(it)) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = PrimaryPurple
                                )
                            )
                        }
                    }
                }

                // Loading State
                if (state.isLoading && state.businesses.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = PrimaryPurple)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "در حال جستجوی مراکز اطراف شما...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // VIP Featured Section («✨ مراکز منتخب نوبت‌یار — رزرو آنی»)
                if (state.vipBusinesses.isNotEmpty()) {
                    item {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "✨ مراکز منتخب نوبت‌یار — رزرو آنی",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = PrimaryPurpleDark
                                    )
                                }
                                Text(
                                    text = "${state.vipBusinesses.size} مرکز",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp)
                            ) {
                                items(state.vipBusinesses, key = { it.id }) { business ->
                                    VipBusinessCard(
                                        business = business,
                                        selectedInsuranceSlug = state.selectedInsuranceSlug,
                                        onClick = { viewModel.sendIntent(ExploreIntent.BusinessClicked(business.id)) }
                                    )
                                }
                            }
                        }
                    }
                }

                // All Nearby Centres List Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📍 نزدیک‌ترین مراکز به شما",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (state.businesses.isNotEmpty()) {
                            Text(
                                text = "${state.businesses.size} مرکز یافت شد",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Empty State
                if (!state.isLoading && state.businesses.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = "🔍", fontSize = 36.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "مرکزی با این مشخصات یافت نشد",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "لطفاً فیلترهای جستجو، نام یا موقعیت مکانی را تغییر دهید.",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                OutlinedButton(
                                    onClick = {
                                        viewModel.sendIntent(ExploreIntent.ClearFilters)
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryPurple)
                                ) {
                                    Text("حذف همه فیلترها")
                                }
                            }
                        }
                    }
                }

                // All Centres Cards List
                items(state.businesses, key = { it.id }) { business ->
                    ClientBusinessRowCard(
                        business = business,
                        selectedInsuranceSlug = state.selectedInsuranceSlug,
                        onClick = { viewModel.sendIntent(ExploreIntent.BusinessClicked(business.id)) }
                    )
                }

                item {
                    BottomBarSpacer()
                }
            }
        }

        // Location Bottom Sheet Picker
        if (showLocationSheet) {
            ModalBottomSheet(
                onDismissRequest = { showLocationSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "📍 انتخاب محدوده و موقعیت مکانی",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "نزدیک‌ترین پزشکان و سالن‌های زیبایی بر اساس موقعیت انتخابی مرتب می‌شوند.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        defaultLocationPresets.forEach { preset ->
                            val isSelected = state.selectedLocation == preset
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        viewModel.sendIntent(ExploreIntent.SelectLocation(preset))
                                        showLocationSheet = false
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) {
                                        PrimaryPurple.copy(alpha = 0.12f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    }
                                ),
                                border = if (isSelected) BorderStroke(1.5.dp, PrimaryPurple) else null
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.LocationOn,
                                            contentDescription = null,
                                            tint = if (isSelected) PrimaryPurple else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = preset.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) PrimaryPurple else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "انتخاب شده",
                                            tint = PrimaryPurple,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationSelectorBar(
    selectedLocation: LocationPreset,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = PrimaryPurple.copy(alpha = 0.08f)
        ),
        border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(PrimaryPurple.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = "موقعیت",
                        tint = PrimaryPurple,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "موقعیت جستجو:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = selectedLocation.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "تغییر",
                    style = MaterialTheme.typography.labelMedium,
                    color = PrimaryPurple,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "تغییر موقعیت",
                    tint = PrimaryPurple,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun InsuranceFilterSection(
    insurances: List<Insurance>,
    selectedSlug: String?,
    onSelectInsurance: (String?) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = PrimaryPurple,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "بیمه تکمیلی من:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "(نمایش مراکز طرف قرارداد)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    val isAllSelected = selectedSlug == null
                    FilterChip(
                        selected = isAllSelected,
                        onClick = { onSelectInsurance(null) },
                        label = { Text("همه بیمه‌ها", fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal) },
                        shape = RoundedCornerShape(14.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryPurple,
                            selectedLabelColor = Color.White
                        )
                    )
                }
                items(insurances) { ins ->
                    val isSelected = selectedSlug == ins.slug
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectInsurance(if (isSelected) null else ins.slug) },
                        label = {
                            Text(
                                text = ins.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null,
                        shape = RoundedCornerShape(14.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryPurple,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VipBusinessCard(
    business: Business,
    selectedInsuranceSlug: String?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(280.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(GradientStart, GradientEnd))),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: VIP Badge + Distance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PrimaryPurple.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "⚡", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "رزرو آنی نوبت‌یار",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryPurple
                        )
                    }
                }

                if (business.distanceKm != null) {
                    Text(
                        text = "${formatDistance(business.distanceKm)} تا شما",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Logo & Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (business.logoPath.isNotEmpty()) {
                    AsyncImage(
                        model = resolveLogoUrl(business.logoPath),
                        contentDescription = business.title,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                Brush.linearGradient(listOf(GradientStart, GradientEnd)),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = business.title.take(1),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = business.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = business.categoryLabel.ifEmpty { "مرکز تخصصی" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Insurances Chips (if any)
            if (business.insurances.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    maxLines = 1
                ) {
                    business.insurances.take(3).forEach { ins ->
                        val isMatched = selectedInsuranceSlug != null && selectedInsuranceSlug == ins.slug
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isMatched) AmberAccent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isMatched) BorderStroke(1.dp, AmberAccent) else null
                        ) {
                            Text(
                                text = "🛡️ ${ins.name}",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = if (isMatched) Color(0xFFB45309) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // CTA Button
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = "مشاهده و رزرو نوبت",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClientBusinessRowCard(
    business: Business,
    selectedInsuranceSlug: String?,
    onClick: () -> Unit
) {
    val isOfficial = business.isClaimed

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = if (isOfficial) {
            BorderStroke(1.5.dp, PrimaryPurple.copy(alpha = 0.45f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isOfficial) 2.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Row: Logo, Title, Official/Directory Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                if (business.logoPath.isNotEmpty()) {
                    AsyncImage(
                        model = resolveLogoUrl(business.logoPath),
                        contentDescription = business.title,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(
                                if (isOfficial) Brush.linearGradient(listOf(GradientStart, GradientEnd))
                                else Brush.linearGradient(listOf(Color(0xFF6B7280), Color(0xFF4B5563))),
                                RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = business.title.take(1),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = business.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (isOfficial) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Verified,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "عضو رسمی",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF047857)
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "بانک اطلاعات عمومی",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = business.categoryLabel.ifEmpty { "مرکز تخصصی" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (business.distanceKm != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = null,
                                tint = PrimaryPurple,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${formatDistance(business.distanceKm)} تا شما",
                                style = MaterialTheme.typography.labelSmall,
                                color = PrimaryPurple,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Address & Neighborhood
            if (business.address.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                val addressText = buildString {
                    if (business.neighborhood.isNotEmpty()) {
                        append(business.neighborhood)
                        append("، ")
                    }
                    append(business.address)
                }
                Text(
                    text = addressText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Working hours if official
            if (isOfficial && business.workStartHour > 0 && business.workEndHour > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    val startMin = business.workStartMinute.toString().padStart(2, '0')
                    val endMin = business.workEndMinute.toString().padStart(2, '0')
                    Text(
                        text = "ساعت کاری: ${business.workStartHour}:$startMin الی ${business.workEndHour}:$endMin",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Insurances Chips
            if (business.insurances.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    business.insurances.take(4).forEach { ins ->
                        val isMatched = selectedInsuranceSlug != null && selectedInsuranceSlug == ins.slug
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isMatched) AmberAccent.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isMatched) BorderStroke(1.dp, AmberAccent) else null
                        ) {
                            Text(
                                text = "🛡️ ${ins.name}",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = if (isMatched) Color(0xFFB45309) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (business.insurances.size > 4) {
                        Text(
                            text = "+${business.insurances.size - 4} بیمه دیگر",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isOfficial) {
                    Text(
                        text = "⚡ رزرو آنی فعال",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF059669)
                    )
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(text = "مشاهده و رزرو نوبت", style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Text(
                        text = "${business.onlineBookingRequestsCount} درخواست رزرو آنلاین",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(text = "مشاهده اطلاعات و تماس", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

private fun formatDistance(km: Double): String {
    return if (km < 1.0) {
        val meters = (km * 1000).toInt()
        "$meters متر"
    } else {
        val rounded = (km * 10).toInt() / 10.0
        "$rounded کیلومتر"
    }
}
