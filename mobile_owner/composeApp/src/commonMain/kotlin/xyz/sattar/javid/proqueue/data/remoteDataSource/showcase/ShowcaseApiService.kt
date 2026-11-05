package xyz.sattar.javid.proqueue.data.remoteDataSource.showcase

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.network.extractErrorMessage
import xyz.sattar.javid.proqueue.core.network.toApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.BannersDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.OwnerReviewsPageDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.FollowersPageDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.StoryDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.StoryDraft
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.ReorderRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.ShowcaseItemDto
import xyz.sattar.javid.proqueue.domain.model.showcase.ShowcaseFields
import xyz.sattar.javid.proqueue.domain.model.showcase.ShowcaseKind

class ShowcaseApiService(private val httpClient: HttpClient) : ShowcaseApi {

    override suspend fun getBanners(businessId: Long): ApiResponse<BannersDto> =
        httpClient.get("owner/banners/") {
            parameter("business_id", businessId)
        }.toApiResponse()

    override suspend fun getOwnerReviews(
        businessId: Long,
        page: Int,
        pageSize: Int
    ): ApiResponse<OwnerReviewsPageDto> =
        httpClient.get("reviews/owner/") {
            parameter("business_id", businessId)
            parameter("page", page)
            parameter("page_size", pageSize)
        }.toApiResponse()

    override suspend fun getFollowers(businessId: Long, pageSize: Int): ApiResponse<FollowersPageDto> =
        httpClient.get("business/$businessId/followers/") {
            parameter("page_size", pageSize)
        }.toApiResponse()

    override suspend fun listItems(kind: ShowcaseKind, businessId: Long): ApiResponse<List<ShowcaseItemDto>> =
        httpClient.get("owner/showcase/${kind.path}/") {
            parameter("business_id", businessId)
        }.toApiResponse()

    override suspend fun createItem(
        kind: ShowcaseKind,
        businessId: Long,
        image: ByteArray,
        fields: ShowcaseFields
    ): ApiResponse<ShowcaseItemDto> =
        httpClient.post("owner/showcase/${kind.path}/") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("business_id", businessId.toString())
                        appendFields(kind, fields)
                        append("image", image, Headers.build {
                            append(HttpHeaders.ContentType, "image/jpeg")
                            append(HttpHeaders.ContentDisposition, "filename=\"${kind.path}.jpg\"")
                        })
                    }
                )
            )
        }.toApiResponse()

    override suspend fun updateItem(
        kind: ShowcaseKind,
        id: Long,
        fields: ShowcaseFields
    ): ApiResponse<ShowcaseItemDto> =
        httpClient.patch("owner/showcase/${kind.path}/$id/") {
            setBody(MultiPartFormDataContent(formData { appendFields(kind, fields) }))
        }.toApiResponse()

    // Not toApiResponse(): a delete may come back with no usable `data`, which
    // that helper reports as an error even though the item is gone.
    override suspend fun listStories(businessId: Long): ApiResponse<List<StoryDto>> =
        httpClient.get("owner/stories/") {
            parameter("business_id", businessId)
        }.toApiResponse()

    override suspend fun postStory(businessId: Long, draft: StoryDraft): ApiResponse<StoryDto> =
        httpClient.post("owner/stories/") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("business_id", businessId.toString())
                        append("caption", draft.caption)
                        if (draft.discountType != null && draft.discountValue != null) {
                            append("button", "discount")
                            append("discount_type", draft.discountType)
                            append("discount_value", draft.discountValue.toString())
                        }
                        append("image", draft.image, Headers.build {
                            append(HttpHeaders.ContentType, "image/jpeg")
                            append(HttpHeaders.ContentDisposition, "filename=\"story.jpg\"")
                        })
                    }
                )
            )
        }.toApiResponse()

    override suspend fun deleteStory(id: Long): ApiResponse<Unit> = deletePath("owner/stories/$id/")

    override suspend fun deleteItem(kind: ShowcaseKind, id: Long): ApiResponse<Unit> =
        deletePath("owner/showcase/${kind.path}/$id/")

    private suspend fun deletePath(path: String): ApiResponse<Unit> {
        return try {
            val response = httpClient.delete(path)
            if (response.status.value in 200..299) {
                ApiResponse.Success(Unit)
            } else {
                val raw = try { response.body<String>() } catch (e: Exception) { "" }
                ApiResponse.Error(extractErrorMessage(raw, response.status.value), response.status.value)
            }
        } catch (e: Exception) {
            ApiResponse.Error(extractErrorMessage("", 0), 0)
        }
    }

    override suspend fun reorder(
        kind: ShowcaseKind,
        businessId: Long,
        ids: List<Long>
    ): ApiResponse<List<ShowcaseItemDto>> =
        httpClient.post("owner/showcase/${kind.path}/reorder/") {
            contentType(ContentType.Application.Json)
            setBody(ReorderRequestDto(businessId, ids))
        }.toApiResponse()

    /** Only the fields of this [kind]; blank values are sent so they can be cleared. */
    private fun io.ktor.client.request.forms.FormBuilder.appendFields(kind: ShowcaseKind, fields: ShowcaseFields) {
        when (kind) {
            ShowcaseKind.PORTFOLIO -> append("caption", fields.caption.trim())
            ShowcaseKind.CERTIFICATES -> {
                append("title", fields.title.trim())
                append("issuer", fields.issuer.trim())
                append("year", fields.year?.toString() ?: "")
            }
        }
    }
}
