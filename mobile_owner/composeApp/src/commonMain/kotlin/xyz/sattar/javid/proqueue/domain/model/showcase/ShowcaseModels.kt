package xyz.sattar.javid.proqueue.domain.model.showcase

import xyz.sattar.javid.proqueue.core.i18n.Str
import xyz.sattar.javid.proqueue.data.remoteDataSource.showcase.model.ShowcaseItemDto

/** Which showcase collection an operation targets; [path] is its URL segment. */
enum class ShowcaseKind(val path: String, val aspectWidth: Int, val aspectHeight: Int, val outputWidth: Int, val outputHeight: Int) {
    /** 1:1, uploaded as 1080x1080. */
    PORTFOLIO("portfolio", 1, 1, 1080, 1080),
    /** 4:5 portrait, uploaded as 1080x1350. */
    CERTIFICATES("certificates", 4, 5, 1080, 1350);

    val aspectRatio: Float get() = aspectWidth.toFloat() / aspectHeight

    /** The frame the cropper opens with; the owner can switch to another [ShowcaseShape]. */
    val defaultShape: ShowcaseShape get() = if (this == PORTFOLIO) ShowcaseShape.SQUARE else ShowcaseShape.PORTRAIT
}

/**
 * Frames the owner can crop to (the server accepts all three, ±10%):
 * forcing one shape made landscape things, most certificates, unusable.
 */
enum class ShowcaseShape(val aspectWidth: Int, val aspectHeight: Int, val outputWidth: Int, val outputHeight: Int) {
    SQUARE(1, 1, 1080, 1080),
    PORTRAIT(4, 5, 1080, 1350),
    LANDSCAPE(4, 3, 1080, 810);

    val aspectRatio: Float get() = aspectWidth.toFloat() / aspectHeight

    val label: String get() = when (this) {
        SQUARE -> Str.showcase_shape_square
        PORTRAIT -> Str.showcase_shape_portrait
        LANDSCAPE -> Str.showcase_shape_landscape
    }
}

/** Review state of a showcase item; anything unrecognised reads as pending. */
enum class ShowcaseStatus(val value: String, private val labelRef: () -> String) {
    PENDING("PENDING", Str::showcase_status_pending),
    APPROVED("APPROVED", Str::showcase_status_approved),
    REJECTED("REJECTED", Str::showcase_status_rejected);

    val label: String get() = labelRef()

    companion object {
        fun fromValue(value: String?): ShowcaseStatus =
            entries.firstOrNull { it.value.equals(value?.trim(), ignoreCase = true) } ?: PENDING
    }
}

val ShowcaseItemDto.statusEnum: ShowcaseStatus get() = ShowcaseStatus.fromValue(status)

/** Editable text fields of an item; the unused ones of the other kind stay empty. */
data class ShowcaseFields(
    val caption: String = "",
    val title: String = "",
    val issuer: String = "",
    val year: Int? = null
)

/** Most items the server accepts per business and kind. */
const val SHOWCASE_MAX_ITEMS = 30
