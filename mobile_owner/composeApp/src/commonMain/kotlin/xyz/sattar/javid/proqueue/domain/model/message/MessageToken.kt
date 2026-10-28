package xyz.sattar.javid.proqueue.domain.model.message

import xyz.sattar.javid.proqueue.core.i18n.Str


enum class MessageToken(private val labelRef: () -> String, val token: String) {
    Visitor(Str::customer_name, "{visitor}"),
    Business(Str::business_name, "{business}"),
    Address(Str::address, "{address}"),
    Date(Str::date, "{date}"),
    Time(Str::time, "{time}"),
    Minutes(Str::reminder_minutes, "{minutes}"),
    Duration(Str::service_time, "{duration}");
    val label: String get() = labelRef()


    companion object {
        fun asList(): List<MessageToken> = entries.toList()
    }
}
