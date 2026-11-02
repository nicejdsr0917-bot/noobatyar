package xyz.sattar.javid.proqueue.domain.model.appointment

import xyz.sattar.javid.proqueue.core.i18n.Str

/**
 * Where a booking came from, as the server reports it in `source`.
 *
 * Only the three known origins get a badge. `UNKNOWN` (rows older than the
 * field), a missing key (old server) and any value a newer server might add
 * all map to null so the row simply shows nothing.
 */
enum class AppointmentSource(val value: String, private val labelRef: () -> String) {
    APP("APP", Str::source_app),
    WEB("WEB", Str::source_web),
    OWNER("OWNER", Str::source_owner);

    val label: String get() = labelRef()

    companion object {
        fun fromValue(value: String?): AppointmentSource? =
            entries.firstOrNull { it.value.equals(value?.trim(), ignoreCase = true) }

        /** The badge text for a raw `source`, or null when nothing should be shown. */
        fun labelFor(value: String?): String? = fromValue(value)?.label
    }
}
