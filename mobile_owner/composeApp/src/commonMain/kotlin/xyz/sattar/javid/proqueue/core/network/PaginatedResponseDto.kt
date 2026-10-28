package xyz.sattar.javid.proqueue.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * [next]/[previous] are absolute URLs — what DRF's `PageNumberPagination`
 * (`StandardPagination.get_next_link()`/`get_previous_link()` in
 * backend/api/pagination.py) actually puts there, e.g.
 * "http://host/api/appointment/query?...&page=2". They used to be typed as
 * `Int?`, as if the server sent a page number: harmless for any response
 * that fit on one page (`next` staying null), but on a result spanning more
 * than one page this failed to parse a real string as a number, and
 * kotlinx.serialization rejects the *whole* response on one bad field — so
 * every row in that page vanished too, not just the pagination links. Used
 * as a plain non-null check ([VisitorRepositoryImpl.hasMore]) and to build
 * page links back up, never parsed as a page index — a String is what every
 * caller actually needed.
 */
@Serializable
data class PaginatedResponseDto<T>(
    @SerialName("count") val count: Int,
    @SerialName("total_pages") val totalPages: Int? = null,
    @SerialName("current_page") val currentPage: Int? = null,
    @SerialName("next") val next: String? = null,
    @SerialName("previous") val previous: String? = null,
    @SerialName("results") val results: List<T>
)
