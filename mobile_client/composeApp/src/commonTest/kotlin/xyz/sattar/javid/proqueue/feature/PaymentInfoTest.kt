package xyz.sattar.javid.proqueue.feature

import xyz.sattar.javid.proqueue.data.remoteDataSource.business.toDomain
import xyz.sattar.javid.proqueue.feature.businessDetail.formatToman
import xyz.sattar.javid.proqueue.feature.businessDetail.paymentInfo
import xyz.sattar.javid.proqueue.testsupport.businessDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaymentInfoTest {
    private fun business(mode: String?, amount: Int?, methods: List<String>?, legacy: String? = null) =
        businessDto(1).copy(
            depositMode = mode, depositAmount = amount,
            acceptedPaymentMethods = methods, paymentMethod = legacy,
        ).toDomain()

    @Test
    fun tomanIsGroupedInPersianDigits() {
        assertEquals("۱۵۰٬۰۰۰ تومان", formatToman(150000))
        assertEquals("۹۰۰ تومان", formatToman(900))
    }

    @Test
    fun mandatoryDepositAndMethodsAreListed() {
        val info = paymentInfo(business("MANDATORY", 50000, listOf("ONLINE", "CASH")))!!
        assertEquals("بیعانهٔ ۵۰٬۰۰۰ تومان هنگام رزرو", info.deposit)
        assertEquals(listOf("پرداخت آنلاین", "پرداخت حضوری"), info.methods)
    }

    @Test
    fun optionalDepositIsMarkedOptional() {
        assertEquals("بیعانهٔ اختیاری ۲۰٬۰۰۰ تومان", paymentInfo(business("OPTIONAL", 20000, null))!!.deposit)
    }

    @Test
    fun zeroOrNoDepositShowsOnlyMethods() {
        val info = paymentInfo(business("MANDATORY", 0, listOf("CARD")))!!
        assertNull(info.deposit)
        assertEquals(listOf("کارت به کارت"), info.methods)
    }

    @Test
    fun legacySingleMethodIsUsedWhenListIsMissing() {
        assertEquals(listOf("پرداخت آنلاین"), paymentInfo(business("NONE", 0, null, legacy = "GATEWAY"))!!.methods)
    }

    @Test
    fun nothingToSayHidesTheSection() {
        assertNull(paymentInfo(business("NONE", 0, emptyList(), legacy = "NONE")))
    }
}
