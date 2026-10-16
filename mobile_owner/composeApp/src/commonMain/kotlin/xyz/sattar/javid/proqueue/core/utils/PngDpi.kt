package xyz.sattar.javid.proqueue.core.utils

import kotlin.math.roundToInt

/**
 * Tags a PNG with a print resolution (its `pHYs` chunk), so print and layout
 * software open it at its physical size instead of assuming 72 dpi.
 *
 * Neither Android's Bitmap.compress nor Skia writes one, and without it a
 * 1654 × 2480 px card at 300 dpi opens as a 58 × 87 cm sheet. Pixels are
 * untouched: any existing pHYs is dropped and a fresh one goes right after
 * IHDR, where the spec puts it. Bytes that aren't a PNG come back unchanged.
 */
fun ByteArray.withPngDpi(dpi: Int): ByteArray {
    if (size < 8 || !PNG_SIGNATURE.indices.all { this[it] == PNG_SIGNATURE[it] }) return this

    val pixelsPerMetre = (dpi / 0.0254).roundToInt()
    val data = ByteArray(9).also {
        it.putInt(0, pixelsPerMetre)
        it.putInt(4, pixelsPerMetre)
        it[8] = 1 // unit: metre
    }
    val phys = chunk("pHYs", data)

    // Pass 1: chunk boundaries, so the output can be one exact-size array.
    val kept = ArrayList<IntRange>()
    var keptBytes = 8
    var pos = 8
    while (pos + 12 <= size) {
        val length = getInt(pos)
        val end = pos + 12 + length
        if (length < 0 || end > size) return this
        if (decodeType(pos + 4) != "pHYs") {
            kept += pos until end
            keptBytes += end - pos
        }
        pos = end
    }
    if (kept.isEmpty() || decodeType(kept[0].first + 4) != "IHDR") return this

    // Pass 2: signature, IHDR, pHYs, then every other chunk in order.
    val out = ByteArray(keptBytes + phys.size)
    copyInto(out, 0, 0, 8)
    var at = 8
    kept.forEachIndexed { i, range ->
        copyInto(out, at, range.first, range.last + 1)
        at += range.last + 1 - range.first
        if (i == 0) {
            phys.copyInto(out, at)
            at += phys.size
        }
    }
    return out
}

private val PNG_SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)

private fun ByteArray.getInt(at: Int): Int =
    ((this[at].toInt() and 0xFF) shl 24) or ((this[at + 1].toInt() and 0xFF) shl 16) or
        ((this[at + 2].toInt() and 0xFF) shl 8) or (this[at + 3].toInt() and 0xFF)

private fun ByteArray.putInt(at: Int, value: Int) {
    this[at] = (value ushr 24).toByte()
    this[at + 1] = (value ushr 16).toByte()
    this[at + 2] = (value ushr 8).toByte()
    this[at + 3] = value.toByte()
}

private fun ByteArray.decodeType(at: Int): String =
    CharArray(4) { (this[at + it].toInt() and 0xFF).toChar() }.concatToString()

private fun chunk(type: String, data: ByteArray): ByteArray {
    val typeBytes = ByteArray(4) { type[it].code.toByte() }
    val out = ByteArray(12 + data.size)
    out.putInt(0, data.size)
    typeBytes.copyInto(out, 4)
    data.copyInto(out, 8)
    out.putInt(8 + data.size, crc32(typeBytes + data))
    return out
}

private val CRC_TABLE = IntArray(256) { n ->
    var c = n
    repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
    c
}

private fun crc32(bytes: ByteArray): Int {
    var c = -1
    for (b in bytes) c = CRC_TABLE[(c xor b.toInt()) and 0xFF] xor (c ushr 8)
    return c.inv()
}
