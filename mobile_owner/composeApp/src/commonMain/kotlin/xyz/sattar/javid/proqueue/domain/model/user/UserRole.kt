package xyz.sattar.javid.proqueue.domain.model.user

import xyz.sattar.javid.proqueue.core.i18n.Str


enum class UserRole(val value: String, private val persianNameRef: () -> String) {
    BUSINESS_OWNER("BUSINESS_OWNER", Str::business_owner),
    CLIENT("CLIENT", Str::customer),
    ADMIN("ADMIN", Str::admin),
    UNKNOWN("UNKNOWN", Str::unknown);
    val persianName: String get() = persianNameRef()


    companion object {
        fun fromString(value: String?): UserRole {
            return entries.find { it.value == value } ?: UNKNOWN
        }
    }
}
