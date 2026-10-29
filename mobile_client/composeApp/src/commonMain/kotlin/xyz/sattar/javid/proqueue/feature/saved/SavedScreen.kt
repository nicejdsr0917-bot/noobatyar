package xyz.sattar.javid.proqueue.feature.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.toDomain
import xyz.sattar.javid.proqueue.domain.BusinessRepository
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.feature.discovery.BusinessRow
import xyz.sattar.javid.proqueue.feature.discovery.Hairline
import xyz.sattar.javid.proqueue.feature.discovery.Radius
import xyz.sattar.javid.proqueue.feature.discovery.Space
import xyz.sattar.javid.proqueue.feature.discovery.StateMessage
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
        contentPadding = PaddingValues(bottom = bottomPadding + Space.xl),
    ) {
        item {
            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = Space.page, vertical = Space.lg)
            ) {
                Text("نشان‌شده‌ها", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (state.businesses.isNotEmpty()) {
                    Text(
                        "${state.businesses.size.toPersianDigits()} مرکز",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Hairline()
        }
        when {
            state.isLoading -> items(3) {
                ShimmerBox(
                    Modifier.padding(horizontal = Space.page, vertical = Space.md).fillMaxWidth().height(72.dp),
                    Radius.md
                )
            }
            state.businesses.isEmpty() -> item {
                StateMessage(
                    icon = Icons.Outlined.BookmarkBorder,
                    title = "هنوز مرکزی نشان نکرده‌اید",
                    body = "با زدن علامت نشان کنار هر مرکز، آن را اینجا نگه دارید تا همیشه در دسترس باشد.",
                    modifier = Modifier.padding(top = 60.dp)
                )
            }
            else -> itemsIndexed(state.businesses, key = { _, b -> b.id }) { index, b ->
                BusinessRow(
                    business = b,
                    saved = true,
                    onToggleSave = { viewModel.unsave(b.id) },
                    onClick = { onNavigateToDetail(b.id) }
                )
                if (index != state.businesses.lastIndex) Hairline(Modifier.padding(start = 84.dp))
            }
        }
    }
}
