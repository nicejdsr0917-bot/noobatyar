package xyz.sattar.javid.proqueue.core.billing

import androidx.activity.ComponentActivity

fun createStoreBilling(activity: ComponentActivity): AndroidStoreBilling? = MyketStoreBilling(activity)
