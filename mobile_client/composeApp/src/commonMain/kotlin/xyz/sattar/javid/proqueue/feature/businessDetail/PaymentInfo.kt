package xyz.sattar.javid.proqueue.feature.businessDetail

import xyz.sattar.javid.proqueue.domain.model.business.Business
import xyz.sattar.javid.proqueue.feature.discovery.toPersianDigits

/** Deposit line and accepted payment methods, as shown before booking. */
internal data class PaymentInfo(val deposit: String?, val methods: List<String>)

// Server values: accepted_payment_methods (ONLINE/CARD/CASH) and the legacy
// single payment_method (NONE/CARD/GATEWAY) older businesses still carry.
private val PAYMENT_METHOD_LABELS = mapOf(
    "ONLINE" to "پرداخت آنلاین",
    "GATEWAY" to "پرداخت آنلاین",
    "CARD" to "کارت به کارت",
    "CASH" to "پرداخت حضوری",
)

/** 150000 → «۱۵۰٬۰۰۰ تومان». */
internal fun formatToman(amount: Int): String =
    amount.toString().reversed().chunked(3).joinToString("٬").reversed().toPersianDigits() + " تومان"

/** Null when there is nothing to tell the client (no deposit, no known method). */
internal fun paymentInfo(business: Business): PaymentInfo? {
    val amount = business.depositAmount ?: 0
    val deposit = when {
        amount <= 0 -> null
        business.depositMode == DEPOSIT_MANDATORY -> "بیعانه ${formatToman(amount)} هنگام رزرو"
        business.depositMode == DEPOSIT_OPTIONAL -> "بیعانه اختیاری ${formatToman(amount)}"
        else -> null
    }
    val methods = (business.acceptedPaymentMethods ?: listOfNotNull(business.paymentMethod))
        .mapNotNull { PAYMENT_METHOD_LABELS[it] }
        .distinct()
    if (deposit == null && methods.isEmpty()) return null
    return PaymentInfo(deposit, methods)
}

/** Business.deposit_mode values on the server (MANDATORY / OPTIONAL / NONE). */
internal const val DEPOSIT_MANDATORY = "MANDATORY"
internal const val DEPOSIT_OPTIONAL = "OPTIONAL"
