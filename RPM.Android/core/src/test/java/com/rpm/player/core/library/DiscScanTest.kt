package com.rpm.player.core.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class DiscScanTest {

    /** A square picture whose alpha is 255 inside [opaqueTo] of the radius, around a hole of [holeTo], and 0 elsewhere. */
    private fun ring(edge: Int, opaqueTo: Double, holeTo: Double = 0.0): (Int, Int) -> Int = { x, y ->
        val r = hypot(x + 0.5 - edge / 2.0, y + 0.5 - edge / 2.0) / (edge / 2.0)
        if (r in holeTo..opaqueTo) 255 else 0
    }

    /** A CD: opaque out to its rim, with its centre hole. */
    @Test fun aWholeCdCoversTheRecord() {
        assertTrue(DiscScan.coversRecord(400, 400, ring(400, opaqueTo = 0.98, holeTo = 0.12)))
    }

    /** A vinyl label as the cover search stamps it: the middle third, the rest of the disc-sized square transparent. */
    @Test fun aLabelIsWornOnTheRecord() {
        assertFalse(DiscScan.coversRecord(400, 400, ring(400, opaqueTo = 0.33)))
    }

    /** A picture with no transparency at all, such as a JPEG of a disc. */
    @Test fun anOpaqueSquareCoversTheRecord() {
        assertTrue(DiscScan.coversRecord(300, 200) { _, _ -> 255 })
    }

    /** Too small to read: taken as a whole record. */
    @Test fun aPictureTooSmallToReadCoversTheRecord() {
        assertTrue(DiscScan.coversRecord(8, 8) { _, _ -> 0 })
    }
}
