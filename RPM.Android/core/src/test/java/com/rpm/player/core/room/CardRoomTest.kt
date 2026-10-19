package com.rpm.player.core.room

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * [CardRoom.paintPixels] works out once per picture what the formula works out at every pixel. These
 * tests check it, to the bit, against the formula written out pixel by pixel (RoomSource's `rpmRoom`).
 */
class CardRoomTest {

    @Test fun aSquareCardIsTheFormulaToTheBit() = same(32, 38, palette(0.10f, 0.45f, 0.80f), seconds = 17.25)

    @Test fun aWideBannerFaceIsTheFormulaToTheBit() = same(87, 35, palette(0.70f, 0.20f, 0.30f), seconds = 3600.04)

    @Test fun oneOrbitsCanPaintPicturesOfOtherSizesAndColours() {
        val orbits = CardRoom.Orbits()
        same(87, 35, palette(0.70f, 0.20f, 0.30f), seconds = 9.0, orbits = orbits)
        same(32, 38, palette(0.10f, 0.45f, 0.80f), seconds = 9.04, orbits = orbits)
        same(12, 9, palette(0.30f, 0.90f, 0.10f), seconds = 9.08, orbits = orbits)
    }

    private fun same(w: Int, h: Int, p: RoomPalette, seconds: Double, orbits: CardRoom.Orbits = CardRoom.Orbits()) {
        val fast = IntArray(w * h)
        CardRoom.paintPixels(fast, w, h, p, seconds, orbits)
        assertArrayEquals(Formula.paint(w, h, p, seconds), fast)
    }

    private fun palette(r: Float, g: Float, b: Float) = RoomPalette(
        ground = Rgb(r * 0.1f, g * 0.1f, b * 0.1f),
        tint = Rgb(r, g, b),
        glow = Rgb(g, b, r),
        shade = Rgb(b, r * 0.5f, g),
    )

    /** CardRoom's painting written out one pixel at a time. */
    private object Formula {
        fun paint(w: Int, h: Int, palette: RoomPalette, seconds: Double): IntArray {
            val pixels = IntArray(w * h)
            val orbits = RoomOrbits()
            val t = seconds * 2.2
            val bass = 0.20 + 0.16 * sin(t * 0.53)
            val mid = 0.16 + 0.14 * sin(t * 0.71 + 1.7)
            val air = 0.12 + 0.11 * sin(t * 0.37 + 3.1)
            orbits.update(t, bass.toFloat(), mid.toFloat(), air.toFloat())
            val out = DoubleArray(3)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val px = (x + 0.5 - 0.5 * w) / h
                    val py = ((h - y - 0.5) - 0.5 * h) / h
                    room(px, py, t, bass, mid, air, palette, orbits, out)
                    val r = (out[0].coerceIn(0.0, 1.0) * 255 + 0.5).toInt()
                    val g = (out[1].coerceIn(0.0, 1.0) * 255 + 0.5).toInt()
                    val b = (out[2].coerceIn(0.0, 1.0) * 255 + 0.5).toInt()
                    pixels[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            return pixels
        }

        private fun lobe(px: Double, py: Double, cx: Float, cy: Float, r: Float, soft: Double): Double {
            val dx = px - cx
            val dy = py - cy
            val r2 = (r * r).toDouble()
            return r2 / (dx * dx + dy * dy + r2 * soft)
        }

        private fun smoothstep(a: Double, b: Double, x: Double): Double {
            val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0)
            return t * t * (3 - 2 * t)
        }

        private fun fract(x: Double): Double = x - floor(x)

        private fun room(px: Double, py: Double, t: Double, bass: Double, mid: Double, air: Double, p: RoomPalette, o: RoomOrbits, out: DoubleArray) {
            val l1 = o.lobes1
            val l2 = o.lobes2
            val l3 = o.lobes3
            val t3 = t * RoomOrbits.AIR_RATE
            val airSoft = 1.08

            var w3x = px + 0.11 * sin(py * 3.2 + t3 * 0.29)
            var w3y = py + 0.11 * cos(px * 2.9 - t3 * 0.25)
            val k3 = 0.05 + air * 0.11
            val w3x2 = w3x + k3 * sin(w3y * 5.1 - t3 * 0.21)
            val w3y2 = w3y + k3 * cos(w3x * 4.6 + t3 * 0.23)
            w3x = w3x2
            w3y = w3y2
            var field3 = 0.0
            for (i in 0 until RoomOrbits.LOBES3) field3 += lobe(w3x, w3y, l3[i * 3], l3[i * 3 + 1], l3[i * 3 + 2], airSoft)

            val peak = 1.0 / airSoft
            val s1 = smoothstep(0.17 * peak, 1.22 * peak, field3) * 0.70
            var br = p.ground.r + (p.shade.r - p.ground.r) * s1
            var bg = p.ground.g + (p.shade.g - p.ground.g) * s1
            var bb = p.ground.b + (p.shade.b - p.ground.b) * s1
            val s2 = smoothstep(0.65 * peak, 1.73 * peak, field3) * (0.16 + air * 0.62)
            br += p.shade.r * s2
            bg += p.shade.g * s2
            bb += p.shade.b * s2

            var wx = px + 0.20 * sin(py * 2.1 + t * 0.19)
            var wy = py + 0.20 * cos(px * 1.8 - t * 0.15)
            val k1 = 0.10 + bass * 0.14
            val wx2 = wx + k1 * sin(wy * 3.9 - t * 0.11)
            val wy2 = wy + k1 * cos(wx * 3.3 + t * 0.13)
            wx = wx2
            wy = wy2
            var field = 0.0
            var weight = 0.0
            var cr = 0.0
            var cg = 0.0
            var cb = 0.0
            for (i in 0 until RoomOrbits.LOBES1) {
                val g = lobe(wx, wy, l1[i * 3], l1[i * 3 + 1], l1[i * 3 + 2], 0.42)
                field += g
                val wg = g * g
                weight += wg
                val f = fract(i * 0.41 + 0.15)
                cr += (p.tint.r + (p.glow.r - p.tint.r) * f) * wg
                cg += (p.tint.g + (p.glow.g - p.tint.g) * f) * wg
                cb += (p.tint.b + (p.glow.b - p.tint.b) * f) * wg
            }
            val wsum = maxOf(weight, 1e-4)
            cr /= wsum
            cg /= wsum
            cb /= wsum
            val m1 = smoothstep(0.30, 1.75, field)
            var r = br + (cr - br) * m1
            var g = bg + (cg - bg) * m1
            var b = bb + (cb - bb) * m1
            val a1 = smoothstep(1.60, 3.40, field) * (0.12 + bass * 0.44)
            r += cr * a1
            g += cg * a1
            b += cb * a1

            var v2x = px + 0.15 * sin(py * 2.6 - t * 0.27)
            var v2y = py + 0.15 * cos(px * 2.3 + t * 0.23)
            val k2 = 0.07 + mid * 0.13
            val v2x2 = v2x + k2 * sin(v2y * 4.4 + t * 0.19)
            val v2y2 = v2y + k2 * cos(v2x * 3.9 - t * 0.21)
            v2x = v2x2
            v2y = v2y2
            var field2 = 0.0
            var weight2 = 0.0
            var dr = 0.0
            var dg = 0.0
            var db = 0.0
            for (i in 0 until RoomOrbits.LOBES2) {
                val gg = lobe(v2x, v2y, l2[i * 3], l2[i * 3 + 1], l2[i * 3 + 2], 0.88)
                val wg = gg * sqrt(gg)
                field2 += gg
                weight2 += wg
                val f = fract(i * 0.53 + 0.40)
                dr += (p.shade.r + (p.glow.r - p.shade.r) * f) * wg
                dg += (p.shade.g + (p.glow.g - p.shade.g) * f) * wg
                db += (p.shade.b + (p.glow.b - p.shade.b) * f) * wg
            }
            val w2sum = maxOf(weight2, 1e-4)
            dr /= w2sum
            dg /= w2sum
            db /= w2sum
            val m2 = smoothstep(0.20, 1.90, field2) * 0.78
            r += (dr - r) * m2
            g += (dg - g) * m2
            b += (db - b) * m2
            val a2 = smoothstep(1.30, 3.50, field2) * (0.14 + bass * 0.40)
            out[0] = r + dr * a2
            out[1] = g + dg * a2
            out[2] = b + db * a2
        }
    }
}
