package xyz.sattar.javid.proqueue.domain.model.business

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

    val all: List<ReminderTemplate> = listOf(
        ReminderTemplate(
            key = "classic",
            label = "رسمی",
            text = "{visitor} عزیز، نوبت شما در {business} ساعت {time} تاریخ {date} است\n" +
                "لطفاً چند دقیقه زودتر تشریف بیاورید"
        ),
        ReminderTemplate(
            key = "friendly",
            label = "دوستانه",
            text = "سلام {visitor} جان\n" +
                "{minutes} دقیقه دیگر نوبت شما در {business} است (ساعت {time})\n" +
                "منتظرتان هستیم"
        ),
        ReminderTemplate(
            key = "short",
            label = "کوتاه",
            text = "یادآوری نوبت {business}: امروز ساعت {time}\n" +
                "{visitor} عزیز منتظرتان هستیم"
        ),
        ReminderTemplate(
            key = "confirm",
            label = "با درخواست تماس",
            text = "{visitor} عزیز، نوبت شما در {business} ساعت {time} تاریخ {date} است\n" +
                "اگر امکان حضور ندارید لطفاً با ما تماس بگیرید"
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
    const val FOOTER = "نوبت‌یار"
    const val OPT_OUT = "لغو11"
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
