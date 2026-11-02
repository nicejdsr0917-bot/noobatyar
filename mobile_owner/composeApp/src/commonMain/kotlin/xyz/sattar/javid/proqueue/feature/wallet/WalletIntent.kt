package xyz.sattar.javid.proqueue.feature.wallet

sealed interface WalletIntent {
    data object Load : WalletIntent
    data object LoadMore : WalletIntent
    data object ClearMessage : WalletIntent
}
