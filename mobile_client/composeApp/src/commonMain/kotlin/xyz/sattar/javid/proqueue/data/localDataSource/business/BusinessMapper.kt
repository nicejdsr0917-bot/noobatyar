package xyz.sattar.javid.proqueue.data.localDataSource.business

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.BusinessDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.CreateBusinessRequestDto
import xyz.sattar.javid.proqueue.data.remoteDataSource.business.model.InsuranceDto
import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.domain.model.business.BusinessCategory
import xyz.sattar.javid.proqueue.domain.model.business.Insurance
import xyz.sattar.javid.proqueue.domain.model.business.ModerationStatus
import xyz.sattar.javid.proqueue.domain.model.business.ReminderDelivery

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun InsuranceDto.toDomain() = Insurance(
    id = id,
    name = name,
    slug = slug,
    insuranceType = insuranceType
)

fun Insurance.toDto() = InsuranceDto(
    id = id,
    name = name,
    slug = slug,
    insuranceType = insuranceType
)

fun BusinessEntity.toDomain() = Business(
    id = id,
    title = title,
    category = category ?: BusinessCategory.OTHER.value,
    // Stored server label wins; the enum is the fallback for rows written
    // before this column existed, or by a server that does not send it.
    categoryLabel = categoryLabel
        ?: BusinessCategory.fromString(category).persianName,
    uniqueCode = uniqueCode,
    phone = phone,
    extraPhones = extraPhones.split(",").map { it.trim() }.filter { it.isNotEmpty() },
    address = address,
    logoPath = logoPath,
    defaultServiceDuration = defaultServiceDuration,
    workStartHour = workStartHour,
    workEndHour = workEndHour,
    workStartMinute = workStartMinute,
    workEndMinute = workEndMinute,
    closedWeekdays = closedWeekdays.split(",").mapNotNull { it.trim().toIntOrNull() },
    notificationEnabled = notificationEnabled,
    notificationTypes = notificationTypes,
    notificationMinutesBefore = notificationMinutesBefore,
    createdAt = createdAt,
    allowAnonymousView = allowAnonymousView,
    notifyOwnerBySms = notifyOwnerBySms,
    paymentMethod = paymentMethod,
    acceptedPaymentMethods = acceptedPaymentMethods.split(",").filter { it.isNotEmpty() },
    maxAppointmentsPerHour = maxAppointmentsPerHour,
    depositAmount = depositAmount,
    merchantId = merchantId,
    paymentLink = paymentLink,
    cardNumber = cardNumber,
    cardOwnerName = cardOwnerName,
    bio = bio,
    moderationStatus = ModerationStatus.fromString(moderationStatus),
    moderationStatusDisplay = moderationStatusDisplay,
    moderationNote = moderationNote,
    moderationSubmittedAt = moderationSubmittedAt,
    isLocked = isLocked,
    noticeEnabled = noticeEnabled,
    noticeMessage = noticeMessage,
    enableReminderSms = enableReminderSms,
    reminderDelivery = reminderDelivery,
    services = services.split(",").map { it.trim() }.filter { it.isNotEmpty() },
    allowClientAddService = allowClientAddService,
    latitude = latitude,
    longitude = longitude,
    province = province,
    city = city,
    neighborhood = neighborhood,
    isClaimed = isClaimed,
    distanceKm = distanceKm,
    onlineBookingRequestsCount = onlineBookingRequestsCount,
    insurances = parseInsurances(insurancesJson)
)

private fun parseInsurances(jsonStr: String): List<Insurance> {
    if (jsonStr.isBlank()) return emptyList()
    return try {
        json.decodeFromString<List<InsuranceDto>>(jsonStr).map { it.toDomain() }
    } catch (_: Exception) {
        emptyList()
    }
}

fun Business.toEntity() = BusinessEntity(
    id = id,
    title = title,
    category = category,
    categoryLabel = categoryLabel,
    uniqueCode = uniqueCode,
    phone = phone,
    extraPhones = extraPhones.joinToString(","),
    address = address,
    logoPath = logoPath,
    defaultServiceDuration = defaultServiceDuration,
    workStartHour = workStartHour,
    workEndHour = workEndHour,
    workStartMinute = workStartMinute,
    workEndMinute = workEndMinute,
    closedWeekdays = closedWeekdays.joinToString(","),
    notificationEnabled = notificationEnabled,
    notificationTypes = notificationTypes,
    notificationMinutesBefore = notificationMinutesBefore,
    createdAt = createdAt,
    allowAnonymousView = allowAnonymousView,
    notifyOwnerBySms = notifyOwnerBySms,
    paymentMethod = paymentMethod,
    acceptedPaymentMethods = acceptedPaymentMethods?.joinToString(",") ?: "",
    maxAppointmentsPerHour = maxAppointmentsPerHour,
    depositAmount = depositAmount,
    merchantId = merchantId,
    paymentLink = paymentLink,
    cardNumber = cardNumber,
    cardOwnerName = cardOwnerName,
    bio = bio,
    moderationStatus = moderationStatus?.value,
    moderationStatusDisplay = moderationStatusDisplay,
    moderationNote = moderationNote,
    moderationSubmittedAt = moderationSubmittedAt,
    isLocked = isLocked,
    noticeEnabled = noticeEnabled,
    noticeMessage = noticeMessage,
    enableReminderSms = enableReminderSms,
    reminderDelivery = reminderDelivery,
    services = services.joinToString(","),
    allowClientAddService = allowClientAddService,
    latitude = latitude,
    longitude = longitude,
    province = province,
    city = city,
    neighborhood = neighborhood,
    isClaimed = isClaimed,
    distanceKm = distanceKm,
    onlineBookingRequestsCount = onlineBookingRequestsCount,
    insurancesJson = if (insurances.isEmpty()) "" else json.encodeToString(insurances.map { it.toDto() })
)

fun Business.toRequestDto() = CreateBusinessRequestDto(
    title = title,
    category = category,
    phone = phone,
    extraPhones = extraPhones,
    address = address,
    defaultServiceDuration = defaultServiceDuration,
    workStartHour = workStartHour,
    workEndHour = workEndHour,
    workStartMinute = workStartMinute,
    workEndMinute = workEndMinute,
    closedWeekdays = closedWeekdays,
    notificationEnabled = notificationEnabled,
    notificationTypes = notificationTypes,
    notificationMinutesBefore = notificationMinutesBefore,
    allowAnonymousView = allowAnonymousView,
    notifyOwnerBySms = notifyOwnerBySms,
    paymentMethod = paymentMethod,
    acceptedPaymentMethods = acceptedPaymentMethods,
    maxAppointmentsPerHour = maxAppointmentsPerHour,
    depositAmount = depositAmount,
    merchantId = merchantId,
    paymentLink = paymentLink,
    cardNumber = cardNumber,
    cardOwnerName = cardOwnerName,
    bio = bio,
    noticeEnabled = noticeEnabled,
    noticeMessage = noticeMessage,
    enableReminderSms = enableReminderSms,
    reminderDelivery = reminderDelivery,
    services = services,
    allowClientAddService = allowClientAddService
)

fun BusinessDto.toEntity(): BusinessEntity {
    val epochMillis = xyz.sattar.javid.proqueue.core.utils.DateTimeUtils.parseIsoToEpochMillis(this.createdAt)

    return BusinessEntity(
        id = id,
        title = title,
        category = category,
        categoryLabel = categoryDisplay,
        uniqueCode = uniqueCode,
        phone = phone ?: "",
        extraPhones = extraPhones.joinToString(","),
        address = address ?: "",
        logoPath = logo ?: "",
        defaultServiceDuration = defaultServiceDuration,
        workStartHour = workStartHour,
        workEndHour = workEndHour,
        workStartMinute = workStartMinute,
        workEndMinute = workEndMinute,
        closedWeekdays = closedWeekdays.joinToString(","),
        notificationEnabled = notificationEnabled,
        notificationTypes = notificationTypes,
        notificationMinutesBefore = notificationMinutesBefore,
        createdAt = epochMillis,
        allowAnonymousView = allowAnonymousView,
        notifyOwnerBySms = notifyOwnerBySms,
        paymentMethod = paymentMethod,
        acceptedPaymentMethods = acceptedPaymentMethods?.joinToString(",") ?: "",
        maxAppointmentsPerHour = maxAppointmentsPerHour,
        depositAmount = depositAmount,
        merchantId = merchantId ?: "",
        paymentLink = paymentLink ?: "",
        cardNumber = cardNumber ?: "",
        cardOwnerName = cardOwnerName ?: "",
        bio = bio ?: "",
        moderationStatus = moderationStatus,
        moderationStatusDisplay = moderationStatusDisplay ?: "",
        moderationNote = moderationNote ?: "",
        // 0 when absent or unparsable — treated as "unknown" by the UI.
        moderationSubmittedAt = moderationSubmittedAt
            ?.let { xyz.sattar.javid.proqueue.core.utils.DateTimeUtils.parseIsoToEpochMillis(it) }
            ?: 0L,
        isLocked = isLocked,
        noticeEnabled = noticeEnabled,
        noticeMessage = noticeMessage ?: "",
        enableReminderSms = enableReminderSms,
        reminderDelivery = reminderDelivery ?: ReminderDelivery.MANUAL.value,
        services = services.joinToString(","),
        allowClientAddService = allowClientAddService,
        latitude = latitude,
        longitude = longitude,
        province = province ?: "",
        city = city ?: "",
        neighborhood = neighborhood ?: "",
        isClaimed = isClaimed,
        distanceKm = distanceKm,
        onlineBookingRequestsCount = onlineBookingRequestsCount,
        insurancesJson = if (insurances.isEmpty()) "" else json.encodeToString(insurances)
    )
}

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
        insurances = insurances.map { it.toDomain() }
    )
}
