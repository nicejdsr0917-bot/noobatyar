package xyz.sattar.javid.proqueue.core.prefs

import kotlin.random.Random

/** 32 random hex chars — enough to be unique per install, meaningless otherwise. */
internal fun generateDeviceId(): String =
    (1..16).joinToString("") { Random.nextInt(0, 256).toString(16).padStart(2, '0') }
