package xyz.sattar.javid.proqueue.data.remoteDataSource.business

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import xyz.sattar.javid.proqueue.core.network.ApiResponse
import xyz.sattar.javid.proqueue.core.network.PaginatedResponseDto
import xyz.sattar.javid.proqueue.core.network.toApiResponse
import xyz.sattar.javid.proqueue.core.network.toDirectApiResponse
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.BusinessCategoriesDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.BusinessDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.CreateBusinessRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.AddServiceCatalogItemRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.ServiceCatalogItemDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.PushLogPageDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.PushLogSummaryDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.SmsLogPageDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.SmsLogSummaryDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.PromoCampaignDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.PromoPreviewDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.PromoSmsOverviewDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.PromoSmsRequestDto
import xyz.sattar.javid.proqueue.domain.model.business.Business

class BusinessApiService(private val httpClient: HttpClient) {

    suspend fun getBusinesses(page: Int, pageSize: Int): ApiResponse<PaginatedResponseDto<BusinessDto>> {
        return httpClient.get("business/") {
            contentType(ContentType.Application.Json)
            parameter("page", page)
            parameter("page_size", pageSize)
        }.toApiResponse()
    }

    suspend fun createBusiness(business: Business): ApiResponse<BusinessDto> {
        return httpClient.post("business/") {
            setBody(buildMultipart(business))
        }.toApiResponse()
    }

    suspend fun updateBusiness(id: Long, business: Business): ApiResponse<BusinessDto> {
        return httpClient.put("business/$id/") {
            setBody(buildMultipart(business))
        }.toApiResponse()
    }

    private fun buildMultipart(business: Business): io.ktor.client.request.forms.MultiPartFormDataContent {
        return io.ktor.client.request.forms.MultiPartFormDataContent(
            io.ktor.client.request.forms.formData {
                append("title", business.title)
                append("category", business.category)
                append("phone", business.phone)
                append("address", business.address)
                // Always sent, blank when unset: DRF reads a blank multipart
                // value as null, so removing the pin actually clears it.
                append("latitude", business.latitude?.toString() ?: "")
                append("longitude", business.longitude?.toString() ?: "")
                append("default_service_duration", business.defaultServiceDuration.toString())
                append("work_start_hour", business.workStartHour.toString())
                append("work_end_hour", business.workEndHour.toString())
                // These two were the whole point of the "19:30" work-hours
                // request — but this function builds the multipart that
                // actually goes on the wire, separate from Business.toRequestDto()
                // (used for the local Room cache), and got missed when the
                // minute fields were added there. Without these, the server's
                // partial update never sees them and silently keeps whatever
                // minute it already had (usually 0), no matter what the owner
                // picked in WorkHoursField.
                append("work_start_minute", business.workStartMinute.toString())
                append("work_end_minute", business.workEndMinute.toString())
                append("notification_enabled", business.notificationEnabled.toString())
                append("notification_types", business.notificationTypes)
                append("notification_minutes_before", business.notificationMinutesBefore.toString())
                append("allow_anonymous_view", business.allowAnonymousView.toString())
                append("notify_owner_by_sms", business.notifyOwnerBySms.toString())
                if (business.paymentMethod != null) append("payment_method", business.paymentMethod)
                if (business.acceptedPaymentMethods != null) {
                    val jsonArrayStr = if (business.acceptedPaymentMethods.isEmpty()) "[]" 
                        else business.acceptedPaymentMethods.joinToString(separator = "\",\"", prefix = "[\"", postfix = "\"]")
                    append("accepted_payment_methods", jsonArrayStr)
                }
                if (business.maxAppointmentsPerHour != null) append("max_appointments_per_hour", business.maxAppointmentsPerHour.toString())
                if (business.depositMode != null) append("deposit_mode", business.depositMode)
                if (business.depositAmount != null) append("deposit_amount", business.depositAmount.toString())
                append("merchant_id", business.merchantId)
                append("payment_link", business.paymentLink)
                append("card_number", business.cardNumber)
                append("card_owner_name", business.cardOwnerName)
                append("bio", business.bio)
                // Same JSON-array-in-a-multipart-field trick as
                // accepted_payment_methods above: DRF's JSONField parses a
                // string value when the request came in as form data.
                append("services", business.services.toJsonArrayString())
                // Same trick, but the bug this field's addition fixes was
                // simpler than any escaping issue: this line was missing
                // entirely, so no matter what the owner chose in the closed-
                // weekdays screen, the save request never carried it — every
                // update silently kept the server's existing value (usually
                // empty), which read as "I marked Friday closed but it's
                // still bookable" with no error anywhere to explain why.
                append("closed_weekdays", business.closedWeekdays.sorted().joinToString(prefix = "[", postfix = "]"))
                // Same "missing from this function, not from toRequestDto()"
                // gap as the minute fields above: ExtraPhonesField could add
                // and remove numbers all day and the save would still only
                // carry the single `phone` field, silently dropping the rest.
                append("extra_phones", business.extraPhones.toJsonArrayString())
                append("allow_client_add_service", business.allowClientAddService.toString())
                append("notice_enabled", business.noticeEnabled.toString())
                append("notice_message", business.noticeMessage)
                append("enable_reminder_sms", business.enableReminderSms.toString())
                append("reminder_delivery", business.reminderDelivery)
                append("reminder_template", business.reminderTemplate)


                if (business.logoBytes != null) {
                    append("logo", business.logoBytes, io.ktor.http.Headers.build {
                        append(io.ktor.http.HttpHeaders.ContentType, "image/jpeg")
                        append(io.ktor.http.HttpHeaders.ContentDisposition, "filename=\"logo.jpg\"")
                    })
                }
            }
        )
    }

    /**
     * Serialises a list of *owner-typed* strings as a JSON array literal for a
     * multipart field. Unlike the fixed enum values in
     * accepted_payment_methods, a service name is free text, so quotes and
     * backslashes have to be escaped or the whole payload stops being JSON and
     * the field is silently rejected.
     */
    private fun List<String>.toJsonArrayString(): String =
        joinToString(separator = ",", prefix = "[", postfix = "]") { name ->
            val escaped = name.replace("\\", "\\\\").replace("\"", "\\\"")
            "\"$escaped\""
        }

    // These two endpoints deliberately return DRF's bare pagination shape
    // ({count, next, previous, results}), not the app-wide {status, code,
    // message, data} envelope — so they go through toDirectApiResponse(),
    // not toApiResponse(). Using the wrapped parser here fails on the
    // missing "status" field and the report can never load.
    suspend fun getSmsLogs(
        businessId: Long,
        page: Int,
        pageSize: Int,
        status: String? = null,
        search: String? = null,
        dateFrom: String? = null,
        dateTo: String? = null
    ): ApiResponse<SmsLogPageDto> {
        return httpClient.get("business/$businessId/sms-logs/") {
            contentType(ContentType.Application.Json)
            parameter("page", page)
            parameter("page_size", pageSize)
            if (status != null) parameter("status", status)
            if (!search.isNullOrBlank()) parameter("search", search)
            if (dateFrom != null) parameter("date_from", dateFrom)
            if (dateTo != null) parameter("date_to", dateTo)
        }.toDirectApiResponse()
    }

    suspend fun getSmsLogSummary(businessId: Long): ApiResponse<SmsLogSummaryDto> {
        return httpClient.get("business/$businessId/sms-logs/summary/") {
            contentType(ContentType.Application.Json)
        }.toDirectApiResponse()
    }

    // Same bare-pagination-shape reasoning as getSmsLogs above.
    suspend fun getPushLogs(
        businessId: Long,
        page: Int,
        pageSize: Int,
        status: String? = null,
        search: String? = null,
        dateFrom: String? = null,
        dateTo: String? = null
    ): ApiResponse<PushLogPageDto> {
        return httpClient.get("business/$businessId/push-logs/") {
            contentType(ContentType.Application.Json)
            parameter("page", page)
            parameter("page_size", pageSize)
            if (status != null) parameter("status", status)
            if (!search.isNullOrBlank()) parameter("search", search)
            if (dateFrom != null) parameter("date_from", dateFrom)
            if (dateTo != null) parameter("date_to", dateTo)
        }.toDirectApiResponse()
    }

    suspend fun getPushLogSummary(businessId: Long): ApiResponse<PushLogSummaryDto> {
        return httpClient.get("business/$businessId/push-logs/summary/") {
            contentType(ContentType.Application.Json)
        }.toDirectApiResponse()
    }

    suspend fun deleteBusiness(id: Long): ApiResponse<Unit> {
        return httpClient.delete("business/$id/") {
            contentType(ContentType.Application.Json)
        }.toApiResponse()
    }

    // Category-scoped, not business-scoped: every business sharing a
    // category sees and contributes to the same list of service chips.
    suspend fun getServiceCatalog(category: String): ApiResponse<List<ServiceCatalogItemDto>> {
        return httpClient.get("business/service-catalog/") {
            contentType(ContentType.Application.Json)
            parameter("category", category)
        }.toApiResponse()
    }

    // ── Owner promo-SMS campaigns (plain JSON, like the SMS report) ──

    suspend fun getPromoSms(businessId: Long): ApiResponse<PromoSmsOverviewDto> {
        return httpClient.get("business/$businessId/promo-sms/") {
            contentType(ContentType.Application.Json)
        }.toDirectApiResponse()
    }

    suspend fun previewPromoSms(businessId: Long, audience: String, message: String): ApiResponse<PromoPreviewDto> {
        return httpClient.post("business/$businessId/promo-sms/preview/") {
            contentType(ContentType.Application.Json)
            setBody(PromoSmsRequestDto(audience = audience, message = message))
        }.toDirectApiResponse()
    }

    suspend fun sendPromoSms(businessId: Long, audience: String, message: String): ApiResponse<PromoCampaignDto> {
        return httpClient.post("business/$businessId/promo-sms/") {
            contentType(ContentType.Application.Json)
            setBody(PromoSmsRequestDto(audience = audience, message = message))
        }.toDirectApiResponse()
    }

    suspend fun getPromoCampaign(businessId: Long, campaignId: Long): ApiResponse<PromoCampaignDto> {
        return httpClient.get("business/$businessId/promo-sms/$campaignId/") {
            contentType(ContentType.Application.Json)
        }.toDirectApiResponse()
    }

    suspend fun addServiceCatalogItem(category: String, name: String): ApiResponse<ServiceCatalogItemDto> {
        return httpClient.post("business/service-catalog/") {
            contentType(ContentType.Application.Json)
            setBody(AddServiceCatalogItemRequestDto(category = category, name = name))
        }.toApiResponse()
    }

    /**
     * The category vocabulary. Unauthenticated on the server (it is a static
     * list with no user data), but sent through the same client as everything
     * else so it picks up the base URL and interceptors.
     */
    suspend fun getCategories(): ApiResponse<BusinessCategoriesDto> {
        return httpClient.get("business/categories/") {
            contentType(ContentType.Application.Json)
        }.toApiResponse()
    }
}
