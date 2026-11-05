package xyz.sattar.javid.proqueue.core.ui.components.map

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Restyles HOT tiles into a soft, natural palette — grey ground, white
 * streets, salmon motorways, pastel parks and water — the look of Balad or
 * Neshan's own maps, from free raster tiles.
 *
 * HOT tiles are palette PNGs (colour type 3): every pixel is an index into
 * one PLTE chunk of at most 256 colours. So instead of decoding pixels this
 * rewrites only that chunk — a few hundred bytes per tile — and leaves the
 * image data untouched. A colour matrix can't do this: it moves every colour
 * the same way, whereas the cream ground must turn grey while the yellow
 * streets beside it turn white.
 *
 * Each palette colour is mapped by lightness and hue:
 * - neutrals follow a lightness curve that pulls the ground (≈247) and the
 *   white streets (≥249) apart: ground to a light grey, streets to white,
 *   buildings a step darker than the ground, labels near-black;
 * - coloured entries move towards a target picked by hue (yellow/orange
 *   streets → white, brick primaries → peach, purple motorways → salmon,
 *   greens → park green, blues → water), blended by how saturated they are
 *   so the anti-aliased edges between two areas stay smooth.
 * Same restyling as the client app (mobile_client …/map/TileRecolor.kt), so
 * both apps draw one map. Tuned against a Balad screenshot on Tehran tiles at city and street zoom
 * (2026-10-28). Anything that isn't a palette PNG passes through unchanged.
 */
internal object TileRecolor {

    private val PNG_SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)

    fun recolor(png: ByteArray): ByteArray {
        if (png.size < 8 || !png.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE)) return png
        var pos = 8
        while (pos + 12 <= png.size) {
            val length = readInt(png, pos)
            val typeStart = pos + 4
            val dataStart = pos + 8
            if (length < 0 || dataStart + length + 4 > png.size) return png
            if (png[typeStart] == 'P'.code.toByte() && png[typeStart + 1] == 'L'.code.toByte() &&
                png[typeStart + 2] == 'T'.code.toByte() && png[typeStart + 3] == 'E'.code.toByte()
            ) {
                val out = png.copyOf()
                var i = dataStart
                while (i + 2 < dataStart + length) {
                    val rgb = ((png[i].toInt() and 0xFF) shl 16) or
                        ((png[i + 1].toInt() and 0xFF) shl 8) or (png[i + 2].toInt() and 0xFF)
                    // At most 256 entries: cheaper to map than to share a cache
                    // between the loader's worker threads.
                    val mapped = remap(rgb)
                    out[i] = (mapped shr 16).toByte()
                    out[i + 1] = (mapped shr 8).toByte()
                    out[i + 2] = mapped.toByte()
                    i += 3
                }
                writeInt(out, dataStart + length, crc32(out, typeStart, length + 4))
                return out
            }
            if (png[typeStart] == 'I'.code.toByte() && png[typeStart + 1] == 'D'.code.toByte()) return png
            pos = dataStart + length + 4
        }
        return png
    }

    // ── Colour mapping ───────────────────────────────────────────────────────

    /** Lightness curve for neutrals, as (in, out) points on 0..255. */
    private val CURVE = arrayOf(
        0f to 40f, 82f to 62f, 150f to 140f, 200f to 210f, 215f to 222f, 230f to 229f,
        244f to 234f, 247f to 236f, 249f to 249f, 252f to 255f, 255f to 255f,
    )

    private fun curve(l: Float): Float {
        for (k in 1 until CURVE.size) {
            val (a, fa) = CURVE[k - 1]
            val (b, fb) = CURVE[k]
            if (l <= b) return fa + (fb - fa) * (l - a) / (b - a)
        }
        return 255f
    }

    private fun luminance(r: Int, g: Int, b: Int) = 0.2126f * r + 0.7152f * g + 0.0722f * b

    /** Target for a coloured entry, by hue in degrees. */
    private fun target(hue: Float): Triple<Float, Float, Float> = when {
        hue >= 255f && hue < 315f -> Triple(244f, 182f, 160f) // motorways → salmon
        hue < 20f || hue >= 330f -> Triple(249f, 206f, 186f)  // primaries → soft peach
        hue < 70f -> Triple(255f, 255f, 255f)                 // secondary/tertiary → white
        hue < 170f -> Triple(178f, 222f, 186f)                // parks, grass → green
        else -> Triple(168f, 210f, 226f)                      // water → soft blue
    }

    private fun remap(rgb: Int): Int {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        val l = luminance(r, g, b)

        // Neutral: the lightness curve, with the faint green cast of the reference grey.
        val n = curve(l)
        val nr = n
        val ng = n + 1f
        val nb = n - 1f

        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val chroma = (mx - mn) / 255f
        val hue = hueDegrees(r, g, b, mx, mn)
        // HOT's purple motorway is muted, so purples count as coloured sooner.
        val (lo, span) = if (hue >= 240f && hue < 320f) 0.03f to 0.05f else 0.06f to 0.18f
        val a = ((chroma - lo) / span).coerceIn(0f, 1f)

        val (tr, tg, tb) = target(hue)
        // Dark sources (casings, icons, labels) stay dark; road fills don't.
        val k = if (l >= 130f) 1f else max(0.3f, l / 130f)

        fun mix(neutral: Float, t: Float) = (neutral * (1 - a) + t * k * a).roundToInt().coerceIn(0, 255)
        return (mix(nr, tr) shl 16) or (mix(ng, tg) shl 8) or mix(nb, tb)
    }

    private fun hueDegrees(r: Int, g: Int, b: Int, mx: Int, mn: Int): Float {
        if (mx == mn) return 0f
        val d = (mx - mn).toFloat()
        val h = when (mx) {
            r -> ((g - b) / d).let { if (it < 0) it + 6 else it }
            g -> (b - r) / d + 2
            else -> (r - g) / d + 4
        }
        return h * 60f
    }

    // ── PNG plumbing ─────────────────────────────────────────────────────────

    private fun readInt(a: ByteArray, at: Int): Int =
        ((a[at].toInt() and 0xFF) shl 24) or ((a[at + 1].toInt() and 0xFF) shl 16) or
            ((a[at + 2].toInt() and 0xFF) shl 8) or (a[at + 3].toInt() and 0xFF)

    private fun writeInt(a: ByteArray, at: Int, v: Int) {
        a[at] = (v ushr 24).toByte()
        a[at + 1] = (v ushr 16).toByte()
        a[at + 2] = (v ushr 8).toByte()
        a[at + 3] = v.toByte()
    }

    private val CRC_TABLE = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) (0xEDB88320.toInt() xor (c ushr 1)) else (c ushr 1) }
        c
    }

    /** CRC-32 over the chunk type and data, as PNG requires. */
    private fun crc32(a: ByteArray, from: Int, len: Int): Int {
        var c = -1
        for (i in from until from + len) c = CRC_TABLE[(c xor a[i].toInt()) and 0xFF] xor (c ushr 8)
        return c.inv()
    }
}
