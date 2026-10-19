package com.rpm.player.core.art

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * An artist's logo, cut to its lettering and reduced to the pixels it is drawn at. Matches the iPhone's `Lettering`
 * (RPMArt/Lettering.swift), which matches the desktop's `ArtScaler.Trim` and `ArtScaler.FitMark`.
 *
 * - **Trim.** The catalogues publish every logo on one canvas (800 by 310 on fanart.tv), whatever the lettering in
 *   it. Drawn as published, most of the height is transparent margin and the lettering is off centre.
 * - **Fit.** A logo is published several times larger than it is drawn. If the renderer makes such a large
 *   reduction in one step, it skips source pixels and breaks thin strokes. Instead the logo is halved by averaging
 *   2x2 blocks while the remaining reduction is more than a factor of two. Then it is finished with the B-spline
 *   cubic, which has no negative lobe and so adds no dark line beside a stroke. Every step works on premultiplied
 *   alpha, so transparent pixels add no colour.
 *
 * Works on [Plane]s, so it runs off the main thread and in the unit tests alike.
 */
object Lettering {
    /** Alpha at or below this counts as empty when the margins are cut. A soft shadow fades to an alpha of one or
     * two well before it reaches zero. */
    const val TRIM_FLOOR = 8

    /** The smallest part of the picture that holds every pixel with alpha above [TRIM_FLOOR]. If there is nothing
     * to cut or nothing visible, it returns the picture unchanged. */
    fun trimmed(plane: Plane): Plane {
        var left = plane.width
        var right = -1
        var top = plane.height
        var bottom = -1
        for (y in 0 until plane.height) {
            val row = y * plane.width * 4
            for (x in 0 until plane.width) {
                if (plane.bytes[row + x * 4 + 3].toInt() and 0xFF > TRIM_FLOOR) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    bottom = y
                }
            }
        }
        if (right < left || bottom < top) return plane
        if (left == 0 && top == 0 && right == plane.width - 1 && bottom == plane.height - 1) return plane
        return plane.cut(left, top, right - left + 1, bottom - top + 1)
    }

    /** The tallest the picture may be drawn, in pixels: [cap], or less if at that height it would be wider than
     * [room]. A [room] of zero means the width is not known yet, and only the cap applies. */
    fun height(width: Int, height: Int, cap: Int, room: Int): Int {
        if (width <= 0 || height <= 0 || room <= 0) return cap
        val fits = floor(room.toDouble() * height / width).toInt()
        return max(1, min(cap, fits))
    }

    /** The picture reduced to [height] pixels tall, with its width in proportion. If it is not taller than
     * [height], it is returned unchanged, because enlarging a mark past its own resolution makes it soft. */
    fun fitted(plane: Plane, height: Int): Plane {
        if (height <= 0 || plane.height <= height) return plane
        var source = plane
        while (source.height > height * 2 && source.width > 1) source = source.halved()
        // Both sides come from the original proportions, so rounding at each halving cannot change the shape.
        val wide = max(1, (plane.width.toDouble() * height / plane.height).roundToInt())
        return source.resampled(wide, height)
    }
}

/** A picture as premultiplied RGBA, eight bits a channel, rows from the top. */
class Plane(val width: Int, val height: Int, val bytes: ByteArray) {
    init {
        require(bytes.size == width * height * 4)
    }

    private fun at(i: Int): Int = bytes[i].toInt() and 0xFF

    fun cut(x: Int, y: Int, width: Int, height: Int): Plane {
        val out = ByteArray(width * height * 4)
        for (row in 0 until height) {
            System.arraycopy(bytes, ((y + row) * this.width + x) * 4, out, row * width * 4, width * 4)
        }
        return Plane(width, height, out)
    }

    /** Half the size in each direction. Each pixel is the mean of a 2x2 block. An odd last row or column is read twice. */
    fun halved(): Plane {
        val wide = max(1, width / 2)
        val high = max(1, height / 2)
        val out = ByteArray(wide * high * 4)
        for (y in 0 until high) {
            val y0 = min(y * 2, height - 1)
            val y1 = min(y * 2 + 1, height - 1)
            for (x in 0 until wide) {
                val x0 = min(x * 2, width - 1)
                val x1 = min(x * 2 + 1, width - 1)
                val a = (y0 * width + x0) * 4
                val b = (y0 * width + x1) * 4
                val c = (y1 * width + x0) * 4
                val d = (y1 * width + x1) * 4
                val o = (y * wide + x) * 4
                for (ch in 0 until 4) out[o + ch] = ((at(a + ch) + at(b + ch) + at(c + ch) + at(d + ch) + 2) / 4).toByte()
            }
        }
        return Plane(wide, high, out)
    }

    /** Resampled to [wide] by [high] with the B-spline cubic, across and then down, with four taps per pixel and
     * the edges clamped. Meant for a reduction of two or less, which is what [halved] leaves. */
    fun resampled(wide: Int, high: Int): Plane {
        val across = taps(width, wide)
        val down = taps(height, high)
        val middle = FloatArray(height * wide * 4)
        for (y in 0 until height) {
            val row = y * width * 4
            for (x in 0 until wide) {
                val tap = across[x]
                val o = (y * wide + x) * 4
                for (k in 0 until 4) {
                    val i = row + tap.index[k] * 4
                    val w = tap.weight[k]
                    for (ch in 0 until 4) middle[o + ch] += w * at(i + ch)
                }
            }
        }
        val out = ByteArray(high * wide * 4)
        for (y in 0 until high) {
            val tap = down[y]
            for (x in 0 until wide) {
                val sum = FloatArray(4)
                for (k in 0 until 4) {
                    val i = (tap.index[k] * wide + x) * 4
                    val w = tap.weight[k]
                    for (ch in 0 until 4) sum[ch] += w * middle[i + ch]
                }
                // The weights are never negative and sum to one, so only float error can push a channel above its
                // alpha. The clamp handles that case.
                val alpha = byte(sum[3])
                val o = (y * wide + x) * 4
                for (ch in 0 until 3) out[o + ch] = min(byte(sum[ch]), alpha).toByte()
                out[o + 3] = alpha.toByte()
            }
        }
        return Plane(wide, high, out)
    }

    class FourTaps(val index: IntArray, val weight: FloatArray)

    companion object {
        /** For each output pixel, where its centre falls in the source and the four pixels around it. */
        fun taps(source: Int, target: Int): Array<FourTaps> {
            val scale = source.toDouble() / target
            return Array(target) { out ->
                val centre = (out + 0.5) * scale - 0.5
                val base = floor(centre).toInt()
                val t = (centre - base).toFloat()
                fun clamp(offset: Int) = min(max(base + offset, 0), source - 1)
                FourTaps(
                    intArrayOf(clamp(-1), clamp(0), clamp(1), clamp(2)),
                    floatArrayOf(bSpline(1 + t), bSpline(t), bSpline(1 - t), bSpline(2 - t)),
                )
            }
        }

        /** The cubic B-spline (B=1, C=0): never negative, and the four weights at any offset sum to one. */
        fun bSpline(value: Float): Float {
            val x = abs(value)
            if (x < 1) return (3 * x * x * x - 6 * x * x + 4) / 6
            if (x < 2) {
                val u = 2 - x
                return u * u * u / 6
            }
            return 0f
        }

        private fun byte(value: Float): Int = value.roundToInt().coerceIn(0, 255)
    }
}
