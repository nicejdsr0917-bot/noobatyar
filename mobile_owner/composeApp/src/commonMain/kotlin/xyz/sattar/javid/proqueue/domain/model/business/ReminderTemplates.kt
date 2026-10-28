package xyz.sattar.javid.proqueue.domain.model.business

import xyz.sattar.javid.proqueue.core.i18n.Str


/**
 * The fixed wordings a PANEL (server-sent) reminder can use.
 *
 * Mirror of backend/business/reminder_templates.py — same keys, same text. The
 * server is what actually sends, so that file is the source of truth; this
 * copy only exists to render the preview the owner picks from. When a wording
 * changes there, change it here too.
 *
 * Why the owner can't edit these: a PANEL reminder goes out of Noobatyar's
 * advertising line, billed to the owner's plan, and the line has rules (no
 * full stops, delivery hours, the mandatory لغو11). Fixed texts keep every
 * reminder inside those rules and inside a predictable number of parts.
 */
data class ReminderTemplate(val key: String, val label: String, val text: String)

object ReminderTemplates {
    const val DEFAULT_KEY = "classic"

    val all: List<ReminderTemplate> get() = listOf(
        ReminderTemplate(
            key = "classic",
            label = Str.formal,
            text = "{visitor} عزیز، نوبت شما در {business} ساعت {time} تاریخ {date} است\n" + // i18n-keep: customer SMS template
                "لطفاً چند دقیقه زودتر تشریف بیاورید" // i18n-keep: customer SMS template
        ),
        ReminderTemplate(
            key = "friendly",
            label = Str.friendly,
            text = "سلام {visitor} جان\n" + // i18n-keep: customer SMS template
                "{minutes} دقیقه دیگر نوبت شما در {business} است (ساعت {time})\n" + // i18n-keep: customer SMS template
                "منتظرتان هستیم" // i18n-keep: customer SMS template
        ),
        ReminderTemplate(
            key = "short",
            label = Str.short,
            text = "یادآوری نوبت {business}: امروز ساعت {time}\n" + // i18n-keep: customer SMS template
                "{visitor} عزیز منتظرتان هستیم" // i18n-keep: customer SMS template
        ),
        ReminderTemplate(
            key = "confirm",
            label = Str.with_call_request,
            text = "{visitor} عزیز، نوبت شما در {business} ساعت {time} تاریخ {date} است\n" + // i18n-keep: customer SMS template
                "اگر امکان حضور ندارید لطفاً با ما تماس بگیرید" // i18n-keep: customer SMS template
        ),
    )

    fun byKey(key: String?): ReminderTemplate = all.firstOrNull { it.key == key } ?: all.first()
}

/**
 * What the SMS panel appends after every message it sends — the footer and the
 * advertising line's mandatory opt-out (backend api/sms.py `signed()`).
 * Shown locked under the text so the owner sees, and pays for, the message the
 * customer actually receives.
 */
object PanelSmsSuffix {
    const val FOOTER = "نوبت‌یار" // i18n-keep: part of the SMS the customer receives
    const val OPT_OUT = "لغو11" // i18n-keep: customer SMS template
    const val TEXT = "\n\n$FOOTER\n$OPT_OUT"

    /** Mirrors `api.sms.signed()`: the line rejects a full stop as a link. */
    fun sign(body: String): String = body.replace(".", "").trimEnd() + TEXT
}

/** Billed SMS parts, with the operator's arithmetic. */
object SmsSegments {
    private const val GSM7 =
        "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?" +
            "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"

    /** Characters the operator counts: UTF-16 units, so an emoji is two. */
    fun length(text: String): Int = text.length

    fun isUnicode(text: String): Boolean = text.any { it !in GSM7 }

    fun parts(text: String): Int {
        if (text.isEmpty()) return 0
        val (single, multi) = if (isUnicode(text)) 70 to 67 else 160 to 153
        val len = length(text)
        return if (len <= single) 1 else (len + multi - 1) / multi
    }

    /** Characters left before the message tips into one more part. */
    fun remainingInPart(text: String): Int {
        val (single, multi) = if (isUnicode(text)) 70 to 67 else 160 to 153
        val len = length(text)
        return if (len <= single) single - len else multi * parts(text) - len
    }
}
