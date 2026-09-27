package xyz.sattar.javid.proqueue.domain.usecase

import xyz.sattar.javid.proqueue.domain.BusinessRepository

/** The four calls behind the promo-SMS campaign screen. */
class PromoSmsUseCase(private val repository: BusinessRepository) {
    suspend fun overview(businessId: Long) = repository.getPromoSms(businessId)

    suspend fun preview(businessId: Long, audience: String, message: String) =
        repository.previewPromoSms(businessId, audience, message)

    suspend fun send(businessId: Long, audience: String, message: String) =
        repository.sendPromoSms(businessId, audience, message)

    suspend fun campaign(businessId: Long, campaignId: Long) =
        repository.getPromoCampaign(businessId, campaignId)
}
