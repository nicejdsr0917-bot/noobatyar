package xyz.sattar.javid.proqueue.data.remoteDataSource.business

import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.BusinessDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.InsuranceDto
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.domain.model.business.BusinessCategory
import xyz.sattar.javid.proqueue.domain.model.business.Insurance
import xyz.sattar.javid.proqueue.domain.model.business.ModerationStatus
import xyz.sattar.javid.proqueue.domain.model.business.ReminderDelivery

fun InsuranceDto.toDomain() = Insurance(
    id = id,
    name = name,
    slug = slug,
    insuranceType = insuranceType
)

fun BusinessDto.toDomain(): Business {
    val epochMillis = xyz.sattar.javid.proqueue.core.utils.DateTimeUtils.parseIsoToEpochMillis(this.createdAt)

    return Business(
        id = id,
        title = title,
        category = category ?: BusinessCategory.OTHER.value,
        categoryLabel = categoryDisplay
            ?: BusinessCategory.fromString(category).persianName,
        uniqueCode = uniqueCode,
        phone = phone ?: "",
        extraPhones = extraPhones,
        address = address ?: "",
        logoPath = logo ?: "",
        themeColor = themeColor ?: "",
        defaultServiceDuration = defaultServiceDuration,
        workStartHour = workStartHour,
        workEndHour = workEndHour,
        workStartMinute = workStartMinute,
        workEndMinute = workEndMinute,
        closedWeekdays = closedWeekdays,
        notificationEnabled = notificationEnabled,
        notificationTypes = notificationTypes,
        notificationMinutesBefore = notificationMinutesBefore,
        createdAt = epochMillis,
        allowAnonymousView = allowAnonymousView,
        notifyOwnerBySms = notifyOwnerBySms,
        paymentMethod = paymentMethod,
        acceptedPaymentMethods = acceptedPaymentMethods,
        maxAppointmentsPerHour = maxAppointmentsPerHour,
        depositMode = depositMode,
        depositAmount = depositAmount,
        merchantId = merchantId ?: "",
        paymentLink = paymentLink ?: "",
        cardNumber = cardNumber ?: "",
        cardOwnerName = cardOwnerName ?: "",
        bio = bio ?: "",
        moderationStatus = ModerationStatus.fromString(moderationStatus),
        moderationStatusDisplay = moderationStatusDisplay ?: "",
        moderationNote = moderationNote ?: "",
        moderationSubmittedAt = moderationSubmittedAt
            ?.let { xyz.sattar.javid.proqueue.core.utils.DateTimeUtils.parseIsoToEpochMillis(it) }
            ?: 0L,
        isLocked = isLocked,
        bookingEnabled = bookingEnabled,
        noticeEnabled = noticeEnabled,
        noticeMessage = noticeMessage ?: "",
        enableReminderSms = enableReminderSms,
        reminderDelivery = reminderDelivery ?: ReminderDelivery.MANUAL.value,
        services = services,
        allowClientAddService = allowClientAddService,
        latitude = latitude,
        longitude = longitude,
        province = province ?: "",
        city = city ?: "",
        neighborhood = neighborhood ?: "",
        isClaimed = isClaimed,
        distanceKm = distanceKm,
        onlineBookingRequestsCount = onlineBookingRequestsCount,
        insurances = insurances.map { it.toDomain() },
        firstFreeSlot = firstFreeSlot
    )
}
