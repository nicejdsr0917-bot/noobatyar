package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The "open this" chevron at the end of a row or link (wallet, «مشاهده»,
 * «ثبت‌نام», «ورود به پنل», settings rows, city picker…): auto-mirrored, so
 * it points toward where the reader goes next — "<" in Persian, ">" in
 * English. Never pick a Left/Right variant by AppLocale.isRtl on top of an
 * auto-mirrored icon: that flips it twice and draws it backwards.
 */
val ForwardChevron: ImageVector get() = Icons.AutoMirrored.Rounded.KeyboardArrowRight
