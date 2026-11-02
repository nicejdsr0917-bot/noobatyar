package xyz.sattar.javid.proqueue.core.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.sattar.javid.proqueue.domain.model.business.Business

object BusinessStateHolder : SelectedBusinessStore {
    private val _selectedBusiness = MutableStateFlow<Business?>(null)
    override val selectedBusiness: StateFlow<Business?> = _selectedBusiness.asStateFlow()

    override fun selectBusiness(business: Business) {
        _selectedBusiness.value = business
    }

    override fun clearBusiness() {
        _selectedBusiness.value = null
    }
}
