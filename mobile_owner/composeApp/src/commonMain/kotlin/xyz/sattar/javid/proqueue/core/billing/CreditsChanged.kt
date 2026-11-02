package xyz.sattar.javid.proqueue.core.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Bumped whenever the owner's balances change outside Home: a purchase that
 * completed in the app (wallet or store) or a wallet conversion. Home loads its
 * usage card once, so without this it kept showing the old wallet and credit.
 */
object CreditsChanged {
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    fun notifyChanged() = _version.update { it + 1 }
}
