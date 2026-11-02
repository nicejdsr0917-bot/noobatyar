package xyz.sattar.javid.proqueue.feature.promo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.promo.PromoApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.promo.PromoDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.promo.PromoPlacement

/**
 * The promo strip for one spot: the server's first active promo, until the
 * user closes it. Closed ones stay closed for the rest of the app session
 * ([dismissedIds] is shared), not forever: a new promo still shows.
 */
class PromoViewModel(
    private val api: PromoApi,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {

    private val promos = MutableStateFlow<List<PromoDto>>(emptyList())
    private val _current = MutableStateFlow<PromoDto?>(null)
    /** What to show; null for nothing (none active, failed to load, or all closed). */
    val current: StateFlow<PromoDto?> = _current.asStateFlow()

    private var loaded: PromoPlacement? = null

    fun load(placement: PromoPlacement) {
        if (loaded == placement) return
        loaded = placement
        viewModelScope.launch(dispatcher) {
            // A failed load just shows no strip: it is never worth an error.
            promos.value = (api.promos(placement) as? ApiResponse.Success)?.data?.promos.orEmpty()
            pick()
        }
    }

    fun dismiss() {
        _current.value?.let { dismissedIds.value = dismissedIds.value + it.id }
        pick()
    }

    private fun pick() {
        _current.value = promos.value.firstOrNull { it.id !in dismissedIds.value }
    }

    companion object {
        private val dismissedIds = MutableStateFlow<Set<Long>>(emptySet())

        /** For tests: forget closed promos. */
        internal fun resetDismissed() { dismissedIds.value = emptySet() }
    }
}
