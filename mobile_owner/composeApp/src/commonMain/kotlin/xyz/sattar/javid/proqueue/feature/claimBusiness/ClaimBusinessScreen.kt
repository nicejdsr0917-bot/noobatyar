package xyz.sattar.javid.proqueue.feature.claimBusiness

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
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.BusinessApiService
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

class ClaimBusinessViewModel(private val api: BusinessApiService) : ViewModel() {
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
                title = { Text("ثبت مالکیت مرکز", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = { if (step is ClaimStep.Confirm || step is ClaimStep.EnterCode) viewModel.back() else onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "بازگشت")
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
                        "اگر شماره موبایل ثبت‌شده برای این مرکز در اختیار شماست، کد تأیید به همان شماره ارسال می‌شود. در غیر این صورت درخواست شما توسط پشتیبانی بررسی می‌شود.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    AppTextField(
                        value = state.note,
                        onValueChange = viewModel::updateNote,
                        label = "توضیح برای پشتیبانی (مثلاً شماره نظام پزشکی یا پروانه)",
                        maxLine = 3,
                        maxLength = 500,
                    )
                    ErrorText(state.error)
                    AppButton(text = "ادامه", onClick = viewModel::start, isLoading = state.isBusy, modifier = Modifier.fillMaxWidth())
                }
                is ClaimStep.EnterCode -> FormStep {
                    BusinessSummary(step.business)
                    Text(
                        "کد تأیید به شماره ${step.maskedPhone} ارسال شد.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OTPTextField(otp = state.code, onOTPChange = viewModel::updateCode, isError = state.error != null, errorMessage = state.error)
                    AppButton(
                        text = "تأیید و انتقال مالکیت",
                        onClick = viewModel::verify,
                        isLoading = state.isBusy,
                        enabled = state.code.length >= 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is ClaimStep.ManualPending -> ResultStep(
                    icon = Icons.Rounded.HourglassTop,
                    title = "درخواست شما ثبت شد",
                    body = "پشتیبانی نوبت‌یار مالکیت «${step.business.title}» را بررسی می‌کند و پس از تأیید، مرکز به فهرست کسب‌وکارهای شما اضافه می‌شود.",
                    action = "بازگشت",
                    onAction = onNavigateBack,
                )
                is ClaimStep.Done -> ResultStep(
                    icon = Icons.Rounded.CheckCircle,
                    title = "مرکز به حساب شما منتقل شد",
                    body = "«${step.business.title}» حالا متعلق به شماست. اطلاعات را کامل کنید و نوبت‌دهی آنلاین را روشن کنید تا مشتریان مستقیم نوبت بگیرند.",
                    action = "رفتن به کسب‌وکارها",
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
                "مرکز شما ممکن است از قبل در فهرست نوبت‌یار باشد. نام مرکز یا شماره تلفن آن را جستجو کنید.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            AppTextField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                label = "نام مرکز یا شماره تلفن",
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
                    "مرکزی با این مشخصات پیدا نشد. می‌توانید کسب‌وکار جدید بسازید.",
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
            val area = listOfNotNull(b.neighborhood, b.city).filter { it.isNotBlank() }.joinToString("، ")
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
