package com.rpm.player.core.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ShrinkTest {

    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun aFlatColourStaysThatColour() {
        val colour = argb(255, 200, 120, 40)
        val out = Shrink.argb(IntArray(512 * 512) { colour }, 512, 512, 312, 312)
        assertEquals(312 * 312, out.size)
        assertTrue(out.all { it == colour })
    }

    @Test
    fun everyOutputPixelsWeightsAddUpToOne() {
        for ((from, to) in listOf(512 to 312, 512 to 390, 256 to 160, 100 to 99, 7 to 1)) {
            val taps = Shrink.Taps.of(from, to)
            for (i in 0 until to) {
                val sum = (0 until taps.span).sumOf { taps.weights[i * taps.span + it].toDouble() }
                assertEquals("$from to $to, pixel $i", 1.0, sum, 1e-4)
            }
        }
    }

    /** One-pixel black and white stripes average to grey. A bilinear shrink would land on whole stripes. */
    @Test
    fun finStripesBecomeGreyNotStripes() {
        val width = 512
        val stripes = IntArray(width * 4) { i -> if ((i % width) % 2 == 0) argb(255, 0, 0, 0) else argb(255, 255, 255, 255) }
        val out = Shrink.argb(stripes, width, 4, 312, 4)
        // Away from the edges, every pixel is near mid grey.
        for (x in 4 until 308) {
            val grey = (out[x] shr 16) and 0xFF
            assertTrue("pixel $x is $grey", abs(grey - 128) < 40)
        }
    }

    /** A transparent pixel's colour does not tint its opaque neighbour. */
    @Test
    fun transparentPixelsDoNotBleedTheirColour() {
        val red = argb(255, 255, 0, 0)
        val clearGreen = argb(0, 0, 255, 0)
        val pixels = IntArray(8 * 8) { i -> if (i % 8 < 4) red else clearGreen }
        val out = Shrink.argb(pixels, 8, 8, 4, 4)
        for (p in out) {
            if ((p ushr 24) == 0) continue
            assertEquals("green in ${Integer.toHexString(p)}", 0, (p shr 8) and 0xFF)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun growingIsRefused() {
        Shrink.argb(IntArray(4), 2, 2, 3, 3)
    }
}
