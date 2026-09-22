package xyz.sattar.javid.proqueue.domain.model.appointment

enum class AppointmentStatus(val value: String) {
    WAITING("WAITING"),
    PENDING_APPROVAL("PENDING_APPROVAL"),
    CONFIRMED("CONFIRMED"),
    COMPLETED("COMPLETED"),
    CANCELLED("CANCELLED"),
    LOCKED("LOCKED"),
    NO_SHOW("NO_SHOW");

    companion object {
        fun fromValue(value: String): AppointmentStatus =
            entries.find { it.value.equals(value, ignoreCase = true) } ?: WAITING
    }
}
