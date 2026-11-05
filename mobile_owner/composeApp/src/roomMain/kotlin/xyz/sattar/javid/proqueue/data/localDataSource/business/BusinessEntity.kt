package xyz.sattar.javid.proqueue.data.localDataSource.business

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Business")
data class BusinessEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val category: String? = null,
    val categoryLabel: String? = null,
    val uniqueCode: String? = null,
    val phone: String,
    /** Comma-separated additional numbers, same encoding as [services]. */
    val extraPhones: String = "",
    /** [SocialLinks.encode]d JSON object. */
    val socialLinks: String = "",
    /** Customers following the page (count only; never who). */
    val followersCount: Int = 0,
    val address: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val logoPath: String = "",
    val defaultServiceDuration: Int,
    val workStartHour: Int, // 0-23
    val workEndHour: Int, // 0-23
    val workStartMinute: Int = 0, // 0-59
    val workEndMinute: Int = 0, // 0-59
    /** Comma-separated weekday ints (Mon=0..Sun=6), same encoding as [services]. */
    val closedWeekdays: String = "",
    val notificationEnabled: Boolean = true,
    val notificationTypes: String = "SMS", // "SMS,WHATSAPP,TELEGRAM"
    val notificationMinutesBefore: Int = 30,
    val createdAt: Long,
    val allowAnonymousView: Boolean = false,
    val notifyOwnerBySms: Boolean = true,
    val paymentMethod: String? = null,
    val acceptedPaymentMethods: String = "",
    val maxAppointmentsPerHour: Int? = null,
    val depositMode: String? = null,
    val depositAmount: Int? = null,
    val merchantId: String = "",
    val paymentLink: String = "",
    val cardNumber: String = "",
    val cardOwnerName: String = "",
    val bio: String = "",
    val moderationStatus: String? = null,
    val moderationStatusDisplay: String = "",
    val moderationNote: String = "",
    val moderationSubmittedAt: Long = 0,
    val isLocked: Boolean = false,
    val noticeEnabled: Boolean = false,
    val noticeMessage: String = "",
    val enableReminderSms: Boolean = true,
    val reminderDelivery: String = "MANUAL",
    val reminderTemplate: String = "classic",
    /** Comma-separated, same encoding as [acceptedPaymentMethods]. */
    val services: String = "",
    val allowClientAddService: Boolean = false
)