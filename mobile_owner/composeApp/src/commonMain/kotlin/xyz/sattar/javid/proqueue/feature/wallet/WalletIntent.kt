package xyz.sattar.javid.proqueue.feature.wallet

sealed interface WalletIntent {
    data object Load : WalletIntent
    data object LoadMore : WalletIntent
    /** Turn toman balance into [count] SMS or booking credits ([kind]: WalletConvertKind). */
    data class Convert(val kind: String, val count: Int) : WalletIntent
    data object ClearMessage : WalletIntent
}
