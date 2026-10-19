package com.rpm.player.core.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The iOS app's LetteringTests. */
class LetteringTest {

    /** A transparent canvas with one block of [colour] on it, premultiplied by [alpha]. */
    private fun canvas(width: Int, height: Int, x: Int, y: Int, w: Int, h: Int, colour: Int = 255, alpha: Int = 255): Plane {
        val bytes = ByteArray(width * height * 4)
        for (row in y until y + h) for (col in x until x + w) {
            val at = (row * width + col) * 4
            val c = (colour * alpha / 255).toByte()
            bytes[at] = c; bytes[at + 1] = c; bytes[at + 2] = c; bytes[at + 3] = alpha.toByte()
        }
        return Plane(width, height, bytes)
    }

    /** An opaque field of one grey with vertical stripes of another. */
    private fun stripes(width: Int, height: Int, ground: Int, stripe: Int, every: Int, wide: Int): Plane {
        val bytes = ByteArray(width * height * 4)
        for (y in 0 until height) for (x in 0 until width) {
            val at = (y * width + x) * 4
            val grey = (if (x % every < wide) stripe else ground).toByte()
            bytes[at] = grey; bytes[at + 1] = grey; bytes[at + 2] = grey; bytes[at + 3] = 255.toByte()
        }
        return Plane(width, height, bytes)
    }

    private fun Plane.byteAt(i: Int) = bytes[i].toInt() and 0xFF

    @Test fun theMarginsAreCutOffTheLettering() {
        val cut = Lettering.trimmed(canvas(800, 310, 300, 100, 200, 90))
        assertEquals(200, cut.width)
        assertEquals(90, cut.height)
    }

    @Test fun aFaintShadowIsNotLettering() {
        val plane = canvas(400, 200, 100, 50, 200, 100)
        for (x in 0 until 400) plane.bytes[(10 * 400 + x) * 4 + 3] = 6
        val cut = Lettering.trimmed(plane)
        assertEquals(200, cut.width)
        assertEquals(100, cut.height)
    }

    @Test fun aPictureWithNoMarginComesBackWhole() {
        val cut = Lettering.trimmed(canvas(120, 40, 0, 0, 120, 40))
        assertEquals(120, cut.width)
        assertEquals(40, cut.height)
    }

    @Test fun aPictureWithNothingVisibleComesBackWhole() {
        val cut = Lettering.trimmed(canvas(120, 40, 0, 0, 0, 0))
        assertEquals(120, cut.width)
        assertEquals(40, cut.height)
    }

    @Test fun theCapStandsWhereTheMarkFitsAcross() = assertEquals(150, Lettering.height(400, 200, cap = 150, room = 1000))

    @Test fun aLongMarkIsAsTallAsTheWidthAllows() = assertEquals(75, Lettering.height(800, 100, cap = 150, room = 600))

    @Test fun noWidthYetLeavesTheCap() = assertEquals(150, Lettering.height(800, 100, cap = 150, room = 0))

    @Test fun aMarkIsReducedToTheHeightAskedAndKeepsItsShape() {
        val fitted = Lettering.fitted(canvas(800, 310, 0, 0, 800, 310), 60)
        assertEquals(60, fitted.height)
        assertEquals(155, fitted.width)
    }

    @Test fun aMarkIsNeverEnlarged() {
        val fitted = Lettering.fitted(canvas(200, 80, 0, 0, 200, 80), 150)
        assertEquals(200, fitted.width)
        assertEquals(80, fitted.height)
    }

    @Test fun whiteLetteringHasNoDarkRim() {
        val plane = Lettering.fitted(canvas(803, 311, 101, 57, 413, 131), 47)
        for (at in 0 until plane.bytes.size step 4) {
            if (plane.byteAt(at + 3) > 0) assertTrue(plane.byteAt(at) + 1 >= plane.byteAt(at + 3))
        }
    }

    @Test fun aStrokeHasNoDarkLineBesideIt() {
        val plane = Lettering.fitted(stripes(600, 300, ground = 100, stripe = 240, every = 20, wide = 3), 160)
        val darkest = (0 until plane.bytes.size step 4).minOf { plane.byteAt(it) }
        assertTrue(darkest >= 99)
    }

    @Test fun aThinStrokeIsNotSkipped() {
        val plane = Lettering.fitted(stripes(1200, 600, ground = 0, stripe = 255, every = 12, wide = 1), 100)
        val row = 50
        for (x in 0 until plane.width - 1 step 2) {
            val left = plane.byteAt((row * plane.width + x) * 4)
            val right = plane.byteAt((row * plane.width + x + 1) * 4)
            assertTrue(maxOf(left, right) > 10)
        }
    }

    @Test fun theFourWeightsAlwaysSumToOne() {
        for (step in 0 until 10) {
            val t = step / 10f
            val sum = Plane.bSpline(1 + t) + Plane.bSpline(t) + Plane.bSpline(1 - t) + Plane.bSpline(2 - t)
            assertTrue(abs(sum - 1) < 0.0001f)
        }
    }
}
