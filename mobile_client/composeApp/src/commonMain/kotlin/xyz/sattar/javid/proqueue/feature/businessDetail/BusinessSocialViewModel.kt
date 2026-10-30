package xyz.sattar.javid.proqueue.feature.businessDetail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.prefs.ClientPreferences
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.BusinessSocialApi
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.model.CertificateDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.model.MyReviewDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.model.PortfolioItemDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.model.ReviewDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.social.model.ReviewSummaryDto
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits

const val MAX_REVIEW_TEXT = 1000

/** Reasons for reporting a review — the server's ContentReport.REASON_CHOICES. */
val reviewReportReasons = listOf(
    "SPAM" to "تبلیغ یا هرزنامه",
    "INAPPROPRIATE" to "محتوای نامناسب یا توهین‌آمیز",
    "MISLEADING" to "اطلاعات نادرست یا گمراه‌کننده",
    "IMPERSONATION" to "جعل هویت",
    "ILLEGAL" to "محتوای غیرقانونی",
    "OTHER" to "سایر",
)

@Immutable
data class BusinessSocialState(
    val portfolio: List<PortfolioItemDto> = emptyList(),
    val certificates: List<CertificateDto> = emptyList(),
    /** Null until loaded, and again if the call failed — the section is hidden. */
    val summary: ReviewSummaryDto? = null,
    val reviews: List<ReviewDto> = emptyList(),
    val hasMoreReviews: Boolean = false,
    val isLoadingMoreReviews: Boolean = false,
    val isSignedIn: Boolean = false,
    val myReview: MyReviewDto? = null,
    val isSavingReview: Boolean = false,
    val isReportingReview: Boolean = false,
)

/**
 * The public showcase and the reviews of one business. Every read is
 * independent and best-effort: a failed call leaves its part empty so the
 * screen simply hides that section.
 */
class BusinessSocialViewModel(
    private val api: BusinessSocialApi,
    private val preferences: ClientPreferences,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {

    private val _state = MutableStateFlow(BusinessSocialState())
    val state: StateFlow<BusinessSocialState> = _state.asStateFlow()

    private val _messages = Channel<DetailMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var businessId: Long = 0
    private var nextReviewPage = 1

    fun load(id: Long) {
        businessId = id
        loadShowcase()
        loadReviews()
        refreshAccount()
    }

    private fun loadShowcase() {
        viewModelScope.launch(dispatcher) {
            val res = api.showcase(businessId)
            if (res is ApiResponse.Success) {
                _state.update { it.copy(portfolio = res.data.portfolio, certificates = res.data.certificates) }
            }
        }
    }

    /** Summary plus the first page of reviews; also used to refresh after a write. */
    private fun loadReviews() {
        viewModelScope.launch(dispatcher) {
            val summary = api.reviewSummary(businessId)
            _state.update { it.copy(summary = (summary as? ApiResponse.Success)?.data) }
        }
        viewModelScope.launch(dispatcher) {
            nextReviewPage = 1
            val res = api.reviews(businessId, 1)
            if (res is ApiResponse.Success) {
                nextReviewPage = 2
                _state.update { it.copy(reviews = res.data.results, hasMoreReviews = res.data.next != null) }
            } else {
                _state.update { it.copy(reviews = emptyList(), hasMoreReviews = false) }
            }
        }
    }

    fun loadMoreReviews() {
        val s = _state.value
        if (!s.hasMoreReviews || s.isLoadingMoreReviews) return
        viewModelScope.launch(dispatcher) {
            _state.update { it.copy(isLoadingMoreReviews = true) }
            when (val res = api.reviews(businessId, nextReviewPage)) {
                is ApiResponse.Success -> {
                    nextReviewPage++
                    _state.update {
                        val known = it.reviews.map { r -> r.id }.toSet()
                        it.copy(
                            reviews = it.reviews + res.data.results.filter { r -> r.id !in known },
                            hasMoreReviews = res.data.next != null,
                            isLoadingMoreReviews = false,
                        )
                    }
                }
                is ApiResponse.Error -> {
                    _state.update { it.copy(isLoadingMoreReviews = false) }
                    _messages.send(DetailMessage.Error(res.message))
                }
            }
        }
    }

    /** Re-reads the sign-in state and the visitor's own review; call when the screen resumes. */
    fun refreshAccount() {
        viewModelScope.launch(dispatcher) {
            val token = preferences.visitorToken.first()
            if (token == null) {
                _state.update { it.copy(isSignedIn = false, myReview = null) }
                return@launch
            }
            _state.update { it.copy(isSignedIn = true) }
            when (val res = api.myReview(token, businessId)) {
                is ApiResponse.Success -> _state.update { it.copy(myReview = res.data) }
                is ApiResponse.Error -> {
                    if (res.code == 401) {
                        preferences.setVisitorToken(null)
                        _state.update { it.copy(isSignedIn = false, myReview = null) }
                    } else {
                        // 404 = not reviewed yet; anything else leaves the button as "write".
                        _state.update { it.copy(myReview = null) }
                    }
                }
            }
        }
    }

    /** Creates the visitor's review, or edits it when one exists. */
    fun saveReview(rating: Int, text: String, onDone: () -> Unit) {
        if (_state.value.isSavingReview) return
        val body = text.trim()
        if (rating !in 1..5 || body.length > MAX_REVIEW_TEXT) return
        viewModelScope.launch(dispatcher) {
            val token = preferences.visitorToken.first() ?: return@launch
            _state.update { it.copy(isSavingReview = true) }
            val editing = _state.value.myReview != null
            val res = if (editing) api.updateReview(token, businessId, rating, body)
            else api.createReview(token, businessId, rating, body)
            _state.update { it.copy(isSavingReview = false) }
            when (res) {
                is ApiResponse.Success -> {
                    _state.update { it.copy(myReview = res.data) }
                    _messages.send(DetailMessage.Success(if (editing) "نظر شما ویرایش شد" else "نظر شما ثبت شد"))
                    loadReviews()
                    onDone()
                }
                // 403 after the 7-day edit window and 409 on a duplicate carry the server's own text.
                is ApiResponse.Error -> _messages.send(DetailMessage.Error(res.message))
            }
        }
    }

    fun deleteReview(onDone: () -> Unit) {
        if (_state.value.isSavingReview) return
        viewModelScope.launch(dispatcher) {
            val token = preferences.visitorToken.first() ?: return@launch
            _state.update { it.copy(isSavingReview = true) }
            val res = api.deleteReview(token, businessId)
            _state.update { it.copy(isSavingReview = false) }
            when (res) {
                is ApiResponse.Success -> {
                    _state.update { it.copy(myReview = null) }
                    _messages.send(DetailMessage.Success("نظر شما حذف شد"))
                    loadReviews()
                    onDone()
                }
                is ApiResponse.Error -> _messages.send(DetailMessage.Error(res.message))
            }
        }
    }

    fun reportReview(reviewId: Long, reason: String, detail: String, onDone: () -> Unit) {
        if (_state.value.isReportingReview) return
        viewModelScope.launch(dispatcher) {
            _state.update { it.copy(isReportingReview = true) }
            val token = preferences.visitorToken.first()
            val res = api.reportReview(token, reviewId, reason, detail.trim())
            _state.update { it.copy(isReportingReview = false) }
            when (res) {
                is ApiResponse.Success -> {
                    _messages.send(DetailMessage.Success("گزارش شما ثبت شد؛ تیم نوبت‌یار بررسی می‌کند"))
                    onDone()
                }
                is ApiResponse.Error -> _messages.send(DetailMessage.Error(res.message))
            }
        }
    }
}

/** Average rounded to one decimal, Persian digits: 4.5 -> «۴٫۵». */
fun formatRatingAverage(avg: Double): String {
    val tenths = kotlin.math.round(avg * 10).toInt()
    return "${tenths / 10}٫${tenths % 10}".toPersianDigits()
}
