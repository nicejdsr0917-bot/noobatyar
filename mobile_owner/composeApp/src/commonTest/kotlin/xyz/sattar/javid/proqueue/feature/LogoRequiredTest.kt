package xyz.sattar.javid.proqueue.feature

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import xyz.sattar.javid.proqueue.feature.createBusiness.isLogoMissing

class LogoRequiredTest {
    @Test
    fun newBusinessWithoutLogoIsBlocked() {
        assertTrue(isLogoMissing(businessId = 0L, pickedLogo = null, savedLogoPath = null))
        assertTrue(isLogoMissing(businessId = 0L, pickedLogo = null, savedLogoPath = ""))
    }

    @Test
    fun newBusinessWithPickedLogoPasses() {
        assertFalse(isLogoMissing(businessId = 0L, pickedLogo = byteArrayOf(1), savedLogoPath = null))
    }

    @Test
    fun editingNeverRequiresALogo() {
        assertFalse(isLogoMissing(businessId = 12L, pickedLogo = null, savedLogoPath = null))
    }
}
