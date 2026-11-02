package xyz.sattar.javid.proqueue.core.state

import kotlinx.coroutines.flow.StateFlow
import xyz.sattar.javid.proqueue.domain.model.business.Business

/** The business the owner is currently working in. [BusinessStateHolder] is the app-wide implementation. */
interface SelectedBusinessStore {
    val selectedBusiness: StateFlow<Business?>
    fun selectBusiness(business: Business)
    fun clearBusiness()
}
