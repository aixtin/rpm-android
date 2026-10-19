package com.rpm.player.core.room

import android.graphics.Bitmap
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The room as a card's ground (the desktop's CardRoom and CardRoomArt). It has the same three families
 * of figures as the full room, drawn small on the CPU and stretched over the card, as the desktop does
 * with its tenth-size Skia shader. There is no dimming and no vignette: a card's room is drawn at
 * full strength, clamped. Its clock runs 2.2 times the room's, and its levels are three slow swings of
 * 11.9, 8.8 and 17.0 seconds, so it breathes without beating.
 *
 * [paint] is pure arithmetic and allocation-free per call. A card that does not drift is painted once,
 * at the moment its colours put it ([still]), so the same card is always caught in the same pose.
 */
object CardRoom {
    private const val RATE = 2.2
    private const val BASS = 0.20
    private const val BASS_SWING = 0.16
    private const val MID = 0.16
    private const val MID_SWING = 0.14
    private const val AIR = 0.12
    private const val AIR_SWING = 0.11
    private const val SPREAD = 37.0

    /**
     * Where in the drift a room of these colours begins, moved on by [apart] places (CardRoomArt.Still).
     * A generated list's room is moved on by its kind's number, because two lists can share a colourway
     * (Favourite tracks and Loved and forgotten do) and would otherwise be the same picture. Every other
     * room is at 0.
     */
    fun still(palette: RoomPalette, apart: Int): Double =
        (palette.tint.r + palette.glow.g + palette.shade.b) * SPREAD + SPREAD * apart

    /** The picture size for a card of [width] by [height]: a tenth of it, but at least [fewest] on each side. */
    fun small(width: Float, height: Float, fewest: Int): Pair<Int, Int> {
        var w = width / 10f
        var h = height / 10f
        val short = minOf(w, h)
        if (short < fewest && short > 0f) {
            val k = fewest / short
            w *= k
            h *= k
        }
        return Pair(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
    }

    /** Paints the room at [seconds] of its own clock into [bitmap], which must be mutable ARGB_8888. */
    fun paint(bitmap: Bitmap, palette: RoomPalette, seconds: Double, pixels: IntArray, orbits: Orbits) {
        val w = bitmap.width
        val h = bitmap.height
        paintPixels(pixels, w, h, palette, seconds, orbits)
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    /**
     * [paint]'s arithmetic into [pixels], [w] by [h], opaque ARGB: RoomSource's `rpmRoom`, formula for
     * formula (see RoomShaders.ROOM), at every pixel.
     *
     * What depends on the column alone (three of the warps' six first terms), on the row alone (the other
     * three), or on neither (each lobe's colour and squared radius, the levels' gains) is worked out once
     * per picture, not once per pixel. Each is the same expression as in the formula, so the picture is
     * identical to the bit ([CardRoomTest]). A pixel costs six sines and cosines instead of twelve.
     */
    internal fun paintPixels(pixels: IntArray, w: Int, h: Int, p: RoomPalette, seconds: Double, o: Orbits) {
        val t = seconds * RATE
        val bass = BASS + BASS_SWING * sin(t * 0.53)
        val mid = MID + MID_SWING * sin(t * 0.71 + 1.7)
        val air = AIR + AIR_SWING * sin(t * 0.37 + 3.1)
        o.update(t, bass.toFloat(), mid.toFloat(), air.toFloat())
        o.prepare(w, p)
        val l1 = o.orbits.lobes1
        val l2 = o.orbits.lobes2
        val l3 = o.orbits.lobes3
        val r1 = o.r1
        val r1soft = o.r1soft
        val r2 = o.r2
        val r2soft = o.r2soft
        val r3 = o.r3
        val r3soft = o.r3soft
        for (i in 0 until RoomOrbits.LOBES1) {
            val r = l1[i * 3 + 2]
            r1[i] = (r * r).toDouble()
            r1soft[i] = r1[i] * 0.42
        }
        for (i in 0 until RoomOrbits.LOBES2) {
            val r = l2[i * 3 + 2]
            r2[i] = (r * r).toDouble()
            r2soft[i] = r2[i] * 0.88
        }
        for (i in 0 until RoomOrbits.LOBES3) {
            val r = l3[i * 3 + 2]
            r3[i] = (r * r).toDouble()
            r3soft[i] = r3[i] * AIR_SOFT
        }
        val c1r = o.c1r
        val c1g = o.c1g
        val c1b = o.c1b
        val c2r = o.c2r
        val c2g = o.c2g
        val c2b = o.c2b

        val t3 = t * RoomOrbits.AIR_RATE
        val k3 = 0.05 + air * 0.11
        val k1 = 0.10 + bass * 0.14
        val k2 = 0.07 + mid * 0.13
        val peak = 1.0 / AIR_SOFT
        val s1from = 0.17 * peak
        val s1to = 1.22 * peak
        val s2from = 0.65 * peak
        val s2to = 1.73 * peak
        val s2gain = 0.16 + air * 0.62
        val a1gain = 0.12 + bass * 0.44
        val a2gain = 0.14 + bass * 0.40
        val t3a = t3 * 0.21
        val t3b = t3 * 0.23
        val ta = t * 0.11
        val tb = t * 0.13
        val tc = t * 0.19
        val td = t * 0.21
        val groundR = p.ground.r.toDouble()
        val groundG = p.ground.g.toDouble()
        val groundB = p.ground.b.toDouble()
        val shadeR = p.shade.r.toDouble()
        val shadeG = p.shade.g.toDouble()
        val shadeB = p.shade.b.toDouble()
        val riseR = (p.shade.r - p.ground.r).toDouble()
        val riseG = (p.shade.g - p.ground.g).toDouble()
        val riseB = (p.shade.b - p.ground.b).toDouble()

        // The room counts y up from the bottom; the plane is centred and scaled by the height.
        val pxs = o.px
        val cos3 = o.cos3
        val cos1 = o.cos1
        val cos2 = o.cos2
        for (x in 0 until w) {
            val px = (x + 0.5 - 0.5 * w) / h
            pxs[x] = px
            cos3[x] = cos(px * 2.9 - t3 * 0.25)
            cos1[x] = cos(px * 1.8 - t * 0.15)
            cos2[x] = cos(px * 2.3 + t * 0.23)
        }
        for (y in 0 until h) {
            val py = ((h - y - 0.5) - 0.5 * h) / h
            val sin3 = sin(py * 3.2 + t3 * 0.29)
            val sin1 = sin(py * 2.1 + t * 0.19)
            val sin2 = sin(py * 2.6 - t * 0.27)
            for (x in 0 until w) {
                val px = pxs[x]

                var w3x = px + 0.11 * sin3
                var w3y = py + 0.11 * cos3[x]
                val w3x2 = w3x + k3 * sin(w3y * 5.1 - t3a)
                val w3y2 = w3y + k3 * cos(w3x * 4.6 + t3b)
                w3x = w3x2
                w3y = w3y2
                var field3 = 0.0
                for (i in 0 until RoomOrbits.LOBES3) {
                    val dx = w3x - l3[i * 3]
                    val dy = w3y - l3[i * 3 + 1]
                    field3 += r3[i] / (dx * dx + dy * dy + r3soft[i])
                }

                val s1 = smoothstep(s1from, s1to, field3) * 0.70
                var br = groundR + riseR * s1
                var bg = groundG + riseG * s1
                var bb = groundB + riseB * s1
                val s2 = smoothstep(s2from, s2to, field3) * s2gain
                br += shadeR * s2
                bg += shadeG * s2
                bb += shadeB * s2

                var wx = px + 0.20 * sin1
                var wy = py + 0.20 * cos1[x]
                val wx2 = wx + k1 * sin(wy * 3.9 - ta)
                val wy2 = wy + k1 * cos(wx * 3.3 + tb)
                wx = wx2
                wy = wy2
                var field = 0.0
                var weight = 0.0
                var cr = 0.0
                var cg = 0.0
                var cb = 0.0
                for (i in 0 until RoomOrbits.LOBES1) {
                    val dx = wx - l1[i * 3]
                    val dy = wy - l1[i * 3 + 1]
                    val g = r1[i] / (dx * dx + dy * dy + r1soft[i])
                    field += g
                    val wg = g * g
                    weight += wg
                    cr += c1r[i] * wg
                    cg += c1g[i] * wg
                    cb += c1b[i] * wg
                }
                val wsum = maxOf(weight, 1e-4)
                cr /= wsum
                cg /= wsum
                cb /= wsum
                val m1 = smoothstep(0.30, 1.75, field)
                var r = br + (cr - br) * m1
                var g = bg + (cg - bg) * m1
                var b = bb + (cb - bb) * m1
                val a1 = smoothstep(1.60, 3.40, field) * a1gain
                r += cr * a1
                g += cg * a1
                b += cb * a1

                var v2x = px + 0.15 * sin2
                var v2y = py + 0.15 * cos2[x]
                val v2x2 = v2x + k2 * sin(v2y * 4.4 + tc)
                val v2y2 = v2y + k2 * cos(v2x * 3.9 - td)
                v2x = v2x2
                v2y = v2y2
                var field2 = 0.0
                var weight2 = 0.0
                var dr = 0.0
                var dg = 0.0
                var db = 0.0
                for (i in 0 until RoomOrbits.LOBES2) {
                    val dx = v2x - l2[i * 3]
                    val dy = v2y - l2[i * 3 + 1]
                    val gg = r2[i] / (dx * dx + dy * dy + r2soft[i])
                    val wg = gg * sqrt(gg)
                    field2 += gg
                    weight2 += wg
                    dr += c2r[i] * wg
                    dg += c2g[i] * wg
                    db += c2b[i] * wg
                }
                val w2sum = maxOf(weight2, 1e-4)
                dr /= w2sum
                dg /= w2sum
                db /= w2sum
                val m2 = smoothstep(0.20, 1.90, field2) * 0.78
                r += (dr - r) * m2
                g += (dg - g) * m2
                b += (db - b) * m2
                val a2 = smoothstep(1.30, 3.50, field2) * a2gain
                val outR = (unit(r + dr * a2) * 255 + 0.5).toInt()
                val outG = (unit(g + dg * a2) * 255 + 0.5).toInt()
                val outB = (unit(b + db * a2) * 255 + 0.5).toInt()
                pixels[y * w + x] = (0xFF shl 24) or (outR shl 16) or (outG shl 8) or outB
            }
        }
    }

    /**
     * The lobes for one moment, and what [paintPixels] works out once per picture: the columns' terms (as
     * long as the widest picture painted with it) and each lobe's colour and squared radius. Kept between
     * paints, so painting allocates nothing after the first.
     */
    class Orbits {
        internal val orbits = RoomOrbits()
        fun update(t: Double, bass: Float, mid: Float, air: Float) = orbits.update(t, bass, mid, air)

        internal var px = DoubleArray(0)
        internal var cos1 = DoubleArray(0)
        internal var cos2 = DoubleArray(0)
        internal var cos3 = DoubleArray(0)
        internal val r1 = DoubleArray(RoomOrbits.LOBES1)
        internal val r1soft = DoubleArray(RoomOrbits.LOBES1)
        internal val r2 = DoubleArray(RoomOrbits.LOBES2)
        internal val r2soft = DoubleArray(RoomOrbits.LOBES2)
        internal val r3 = DoubleArray(RoomOrbits.LOBES3)
        internal val r3soft = DoubleArray(RoomOrbits.LOBES3)
        internal val c1r = DoubleArray(RoomOrbits.LOBES1)
        internal val c1g = DoubleArray(RoomOrbits.LOBES1)
        internal val c1b = DoubleArray(RoomOrbits.LOBES1)
        internal val c2r = DoubleArray(RoomOrbits.LOBES2)
        internal val c2g = DoubleArray(RoomOrbits.LOBES2)
        internal val c2b = DoubleArray(RoomOrbits.LOBES2)
        private var coloured: RoomPalette? = null

        /** Makes the column arrays at least [w] long, and works out each lobe's colour in [p] if [p] is new. */
        internal fun prepare(w: Int, p: RoomPalette) {
            if (px.size < w) {
                px = DoubleArray(w)
                cos1 = DoubleArray(w)
                cos2 = DoubleArray(w)
                cos3 = DoubleArray(w)
            }
            if (coloured == p) return
            coloured = p
            for (i in 0 until RoomOrbits.LOBES1) {
                val f = fract(i * 0.41 + 0.15)
                c1r[i] = p.tint.r + (p.glow.r - p.tint.r) * f
                c1g[i] = p.tint.g + (p.glow.g - p.tint.g) * f
                c1b[i] = p.tint.b + (p.glow.b - p.tint.b) * f
            }
            for (i in 0 until RoomOrbits.LOBES2) {
                val f = fract(i * 0.53 + 0.40)
                c2r[i] = p.shade.r + (p.glow.r - p.shade.r) * f
                c2g[i] = p.shade.g + (p.glow.g - p.shade.g) * f
                c2b[i] = p.shade.b + (p.glow.b - p.shade.b) * f
            }
        }
    }

    // Inline, like [unit]: called five times per pixel. On the Amiko box the compiler does not inline
    // these two, or the standard library's coerceIn, which they replace.
    @Suppress("NOTHING_TO_INLINE")
    private inline fun smoothstep(a: Double, b: Double, x: Double): Double {
        val t = unit((x - a) / (b - a))
        return t * t * (3 - 2 * t)
    }

    /** [x] held to 0..1, as `coerceIn(0.0, 1.0)` does it (a NaN stays a NaN). */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun unit(x: Double): Double = if (x < 0.0) 0.0 else if (x > 1.0) 1.0 else x

    private fun fract(x: Double): Double = x - kotlin.math.floor(x)

    private const val AIR_SOFT = 1.08
}
