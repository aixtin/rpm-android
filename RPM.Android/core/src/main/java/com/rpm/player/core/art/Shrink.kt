package com.rpm.player.core.art

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Shrinks a picture with a Catmull-Rom filter widened to the reduction, so every source pixel counts toward the
 * output pixels it falls in. The decoder's own reduction is bilinear: it reads four source pixels per output pixel
 * whatever the reduction. For example, a 512-pixel cover drawn at 312 loses detail and fine lettering looks
 * jagged. See [SharpDecoder].
 */
object Shrink {
    /**
     * [pixels] (ARGB, not premultiplied, [width] by [height], rows from top to bottom) shrunk to [toWidth] by
     * [toHeight], in the same form. Colours are averaged premultiplied, so the colour of a transparent pixel does
     * not spread into the edge of a logo. Neither side may grow.
     */
    fun argb(pixels: IntArray, width: Int, height: Int, toWidth: Int, toHeight: Int): IntArray {
        require(toWidth in 1..width && toHeight in 1..height) { "shrink $width x $height to $toWidth x $toHeight" }
        val across = Taps.of(width, toWidth)
        val down = Taps.of(height, toHeight)

        // Across each row: premultiplied channels, four floats per pixel.
        val rows = FloatArray(toWidth * height * 4)
        for (y in 0 until height) {
            val from = y * width
            val to = y * toWidth * 4
            for (x in 0 until toWidth) {
                var a = 0f
                var r = 0f
                var g = 0f
                var b = 0f
                val start = across.start[x]
                val base = x * across.span
                for (k in 0 until across.span) {
                    val w = across.weights[base + k]
                    if (w == 0f) continue
                    val p = pixels[from + (start + k).coerceIn(0, width - 1)]
                    val pa = (p ushr 24) / 255f
                    a += w * pa
                    r += w * pa * ((p shr 16) and 0xFF)
                    g += w * pa * ((p shr 8) and 0xFF)
                    b += w * pa * (p and 0xFF)
                }
                val o = to + x * 4
                rows[o] = a
                rows[o + 1] = r
                rows[o + 2] = g
                rows[o + 3] = b
            }
        }

        // Down each column, then back to ARGB.
        val out = IntArray(toWidth * toHeight)
        for (y in 0 until toHeight) {
            val start = down.start[y]
            val base = y * down.span
            for (x in 0 until toWidth) {
                var a = 0f
                var r = 0f
                var g = 0f
                var b = 0f
                for (k in 0 until down.span) {
                    val w = down.weights[base + k]
                    if (w == 0f) continue
                    val o = ((start + k).coerceIn(0, height - 1) * toWidth + x) * 4
                    a += w * rows[o]
                    r += w * rows[o + 1]
                    g += w * rows[o + 2]
                    b += w * rows[o + 3]
                }
                out[y * toWidth + x] = pack(a, r, g, b)
            }
        }
        return out
    }

    /** One premultiplied pixel back to ARGB. The filter's negative lobes can overshoot, so each channel is clamped. */
    private fun pack(a: Float, r: Float, g: Float, b: Float): Int {
        val alpha = a.coerceIn(0f, 1f)
        if (alpha <= 0f) return 0
        val ai = (alpha * 255f).roundToInt()
        val ri = (r / alpha).roundToInt().coerceIn(0, 255)
        val gi = (g / alpha).roundToInt().coerceIn(0, 255)
        val bi = (b / alpha).roundToInt().coerceIn(0, 255)
        return (ai shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    /**
     * The weights for one axis: output pixel `i` is the sum over `k` of `weights[i * span + k]` times source
     * pixel `start[i] + k` (clamped to the edge). Each output pixel's weights add up to 1.
     */
    internal class Taps(val start: IntArray, val span: Int, val weights: FloatArray) {
        companion object {
            fun of(from: Int, to: Int): Taps {
                val scale = from.toDouble() / to
                val radius = RADIUS * scale
                val span = (2 * radius).toInt() + 2
                val start = IntArray(to)
                val weights = FloatArray(to * span)
                for (i in 0 until to) {
                    val centre = (i + 0.5) * scale - 0.5
                    val first = floor(centre - radius).toInt() + 1
                    start[i] = first
                    var sum = 0.0
                    for (k in 0 until span) {
                        val w = catmullRom((first + k - centre) / scale)
                        weights[i * span + k] = w.toFloat()
                        sum += w
                    }
                    for (k in 0 until span) weights[i * span + k] = (weights[i * span + k] / sum).toFloat()
                }
                return Taps(start, span, weights)
            }
        }
    }

    /** How far the filter reaches, in output pixels. */
    private const val RADIUS = 2.0

    /** The Catmull-Rom cubic (Keys, a = -0.5): sharp, with small negative lobes. */
    internal fun catmullRom(t: Double): Double {
        val x = abs(t)
        return when {
            x < 1 -> 1.5 * x * x * x - 2.5 * x * x + 1
            x < 2 -> -0.5 * x * x * x + 2.5 * x * x - 4 * x + 2
            else -> 0.0
        }
    }
}
