package xyz.sattar.javid.proqueue.feature.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.prefs.PreferencesManager
import xyz.sattar.javid.proqueue.core.ui.components.ShimmerBox
import xyz.sattar.javid.proqueue.data.localDataSource.business.toDomain
import xyz.sattar.javid.proqueue.domain.BusinessRepository
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.feature.discovery.Brand
import xyz.sattar.javid.proqueue.feature.discovery.BusinessListCard
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits

@Immutable
data class SavedState(
    val isLoading: Boolean = true,
    val businesses: List<Business> = emptyList(),
)

class SavedViewModel(private val businessRepository: BusinessRepository) : ViewModel() {
    private val _state = MutableStateFlow(SavedState())
    val state: StateFlow<SavedState> = _state.asStateFlow()

    // Previously loaded entries, so un-saving one doesn't re-fetch the rest.
    private val cache = mutableMapOf<Long, Business>()

    init {
        viewModelScope.launch {
            PreferencesManager.savedBusinessIds.collectLatest { ids ->
                val missing = ids.filter { it !in cache }
                if (missing.isNotEmpty()) {
                    _state.update { it.copy(isLoading = cache.isEmpty()) }
                    missing.map { id ->
                        async { (businessRepository.getClientBusinessDetail(id) as? ApiResponse.Success)?.data?.toDomain() }
                    }.awaitAll().filterNotNull().forEach { cache[it.id] = it }
                }
                _state.update { SavedState(isLoading = false, businesses = ids.mapNotNull { cache[it] }) }
            }
        }
    }

    fun unsave(id: Long) {
        viewModelScope.launch { PreferencesManager.toggleSavedBusiness(id) }
    }
}

@Composable
fun SavedScreen(
    onNavigateToDetail: (Long) -> Unit,
    bottomPadding: Dp,
    viewModel: SavedViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = bottomPadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text("نشان‌شده‌ها", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                if (state.businesses.isNotEmpty()) {
                    Text(
                        "${state.businesses.size.toPersianDigits()} مرکز",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        when {
            state.isLoading -> items(3) {
                ShimmerBox(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(96.dp))
            }
            state.businesses.isEmpty() -> item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 60.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(96.dp).clip(CircleShape).background(Brand.Violet500.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            Icons.Rounded.BookmarkBorder,
                            contentDescription = null,
                            tint = Brand.Violet500,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("هنوز چیزی نشان نکرده‌اید", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "روی علامت نشان کنار هر مرکز بزنید تا اینجا همیشه در دسترس باشد.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            else -> items(state.businesses, key = { it.id }) { b ->
                BusinessListCard(
                    business = b,
                    saved = true,
                    onToggleSave = { viewModel.unsave(b.id) },
                    onClick = { onNavigateToDetail(b.id) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}
