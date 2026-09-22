package xyz.sattar.javid.proqueue.core.utils

import androidx.compose.ui.graphics.ImageBitmap

/** PNG bytes for [image]. PNG rather than JPEG: these cards are flat colour
 *  and text, which JPEG's chroma subsampling turns to mush around Persian
 *  glyph edges, and a QR code compressed lossily can stop scanning. */
expect fun encodePng(image: ImageBitmap): ByteArray

/**
 * Hands a rendered card to the platform share sheet as a real image file —
 * so Instagram, Bale or Rubika receive a picture the owner can post, not a
 * link they'd have to screenshot themselves.
 *
 * [caption] rides along as the share's text: an app that takes both (most
 * messengers) shows the picture with the link under it; one that takes only
 * the image (Instagram Stories) ignores it, which is the right outcome there.
 */
expect fun shareImage(bytes: ByteArray, fileName: String, caption: String)
