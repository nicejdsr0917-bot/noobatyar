package xyz.sattar.javid.proqueue.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The "open this" chevron at the end of a row or link (wallet, «مشاهده»,
 * «ورود به پنل», settings rows…). Always drawn as ">", in Persian too: the
 * product owner's call (2026-11-06) over Material's mirrored "<" for RTL.
 * Not auto-mirrored on purpose, and not for back arrows or previous/next
 * steppers, which keep their mirrored icons.
 */
val ForwardChevron: ImageVector get() = Icons.Rounded.ChevronRight
