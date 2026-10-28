package xyz.sattar.javid.proqueue.core.billing

/** A store billing client tied to an Activity; released with it. */
interface AndroidStoreBilling : StoreBilling {
    fun dispose()
}
