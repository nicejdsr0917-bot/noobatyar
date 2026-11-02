package xyz.sattar.javid.proqueue.feature.claimBusiness

import xyz.sattar.javid.proqueue.core.i18n.Str
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.ui.components.AppButton
import xyz.sattar.javid.proqueue.core.ui.components.AppTextField
import xyz.sattar.javid.proqueue.core.ui.components.OTPTextField
import xyz.sattar.javid.proqueue.core.utils.toEnglishDigits
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.BusinessApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.ClaimableBusinessDto

/**
 * "Is my clinic already listed?" — finds an imported directory listing and
 * transfers it to this owner (backend business/claim_views.py), so they keep
 * its public page and the booking requests clients already left there instead
 * of creating a duplicate.
 */
sealed interface ClaimStep {
    data object Search : ClaimStep
    data class Confirm(val business: ClaimableBusinessDto) : ClaimStep
    data class EnterCode(val business: ClaimableBusinessDto, val maskedPhone: String) : ClaimStep
    data class ManualPending(val business: ClaimableBusinessDto) : ClaimStep
    data class Done(val business: ClaimableBusinessDto) : ClaimStep
}

@Immutable
data class ClaimState(
    val step: ClaimStep = ClaimStep.Search,
    val query: String = "",
    val results: List<ClaimableBusinessDto> = emptyList(),
    val searched: Boolean = false,
    val note: String = "",
    val code: String = "",
    val isBusy: Boolean = false,
    val error: String? = null,
)

class ClaimBusinessViewModel(private val api: BusinessApi) : ViewModel() {
    private val _state = MutableStateFlow(ClaimState())
    val state: StateFlow<ClaimState> = _state.asStateFlow()
    private var searchJob: Job? = null

    fun updateQuery(q: String) {
        _state.update { it.copy(query = q, error = null) }
        searchJob?.cancel()
        if (q.trim().length < 2) {
            _state.update { it.copy(results = emptyList(), searched = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            _state.update { it.copy(isBusy = true) }
            when (val res = api.searchClaimable(q.trim())) {
                is ApiResponse.Success -> _state.update {
                    it.copy(results = res.data.results, searched = true, isBusy = false)
                }
                is ApiResponse.Error -> _state.update { it.copy(error = res.message, isBusy = false) }
            }
        }
    }

    fun select(b: ClaimableBusinessDto) = _state.update { it.copy(step = ClaimStep.Confirm(b), error = null) }
    fun updateNote(n: String) = _state.update { it.copy(note = n) }
    fun updateCode(c: String) = _state.update { it.copy(code = c.toEnglishDigits(), error = null) }
    fun back() = _state.update { it.copy(step = ClaimStep.Search, code = "", error = null) }

    fun start() {
        val step = _state.value.step as? ClaimStep.Confirm ?: return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, error = null) }
            when (val res = api.startClaim(step.business.id, _state.value.note.trim())) {
                is ApiResponse.Success -> _state.update {
                    it.copy(
                        isBusy = false,
                        step = if (res.data.method == "OTP") ClaimStep.EnterCode(step.business, res.data.maskedPhone)
                        else ClaimStep.ManualPending(step.business),
                    )
                }
                is ApiResponse.Error -> _state.update { it.copy(isBusy = false, error = res.message) }
            }
        }
    }

    fun verify() {
        val step = _state.value.step as? ClaimStep.EnterCode ?: return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, error = null) }
            when (val res = api.verifyClaim(step.business.id, _state.value.code)) {
                is ApiResponse.Success -> _state.update { it.copy(isBusy = false, step = ClaimStep.Done(step.business)) }
                is ApiResponse.Error -> _state.update { it.copy(isBusy = false, error = res.message) }
            }
        }
    }
}

@Composable
fun ClaimBusinessScreen(
    onNavigateBack: () -> Unit,
    onFinished: () -> Unit,
    viewModel: ClaimBusinessViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val step = state.step

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Str.claim_title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { if (step is ClaimStep.Confirm || step is ClaimStep.EnterCode) viewModel.back() else onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = Str.back)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            when (step) {
                ClaimStep.Search -> SearchStep(state, viewModel)
                is ClaimStep.Confirm -> FormStep {
                    BusinessSummary(step.business)
                    Text(
                        Str.claim_confirm_hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AppTextField(
                        value = state.note,
                        onValueChange = viewModel::updateNote,
                        label = Str.claim_note_label,
                        maxLine = 3,
                        maxLength = 500,
                    )
                    ErrorText(state.error)
                    AppButton(text = Str.claim_continue, onClick = viewModel::start, isLoading = state.isBusy, modifier = Modifier.fillMaxWidth())
                }
                is ClaimStep.EnterCode -> FormStep {
                    BusinessSummary(step.business)
                    Text(
                        Str.claim_code_sent(step.maskedPhone),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OTPTextField(otp = state.code, onOTPChange = viewModel::updateCode, isError = state.error != null, errorMessage = state.error)
                    AppButton(
                        text = Str.claim_verify_transfer,
                        onClick = viewModel::verify,
                        isLoading = state.isBusy,
                        enabled = state.code.length >= 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is ClaimStep.ManualPending -> ResultStep(
                    icon = Icons.Rounded.HourglassTop,
                    title = Str.claim_pending_title,
                    body = Str.claim_pending_body(step.business.title),
                    action = Str.back,
                    onAction = onNavigateBack,
                )
                is ClaimStep.Done -> ResultStep(
                    icon = Icons.Rounded.CheckCircle,
                    title = Str.claim_done_title,
                    body = Str.claim_done_body(step.business.title),
                    action = Str.claim_go_to_businesses,
                    onAction = onFinished,
                )
            }
        }
    }
}

@Composable
private fun SearchStep(state: ClaimState, viewModel: ClaimBusinessViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                Str.claim_search_hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            AppTextField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                label = Str.claim_search_label,
                keyboardType = KeyboardType.Text,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            )
            if (state.isBusy) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                }
            }
            ErrorText(state.error)
        }
        if (state.searched && state.results.isEmpty() && !state.isBusy) {
            item {
                Text(
                    Str.claim_not_found,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
        items(state.results, key = { it.id }) { b ->
            Surface(
                onClick = { viewModel.select(b) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) { BusinessSummary(b, Modifier.padding(14.dp)) }
        }
    }
}

@Composable
private fun BusinessSummary(b: ClaimableBusinessDto, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(b.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            b.categoryDisplay?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            val area = listOfNotNull(b.neighborhood, b.city).filter { it.isNotBlank() }.joinToString(Str.claim_area_separator)
            if (area.isNotBlank()) Text(area, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FormStep(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
}

@Composable
private fun ErrorText(error: String?) {
    if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ResultStep(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp)) }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        AppButton(text = action, onClick = onAction, modifier = Modifier.fillMaxWidth())
    }
}
