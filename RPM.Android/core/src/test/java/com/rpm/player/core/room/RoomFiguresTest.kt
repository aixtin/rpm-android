package com.rpm.player.core.room

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

class RoomFiguresTest {

    private fun at(a: FloatArray, i: Int, k: Int) = a[i * 4 + k]

    @Test fun yangsPairsStandOppositeEachOther() {
        val f = RoomFigures()
        f.advance(3.7f, 0.4f, 0.2f)
        f.yang(3.7, 0.4f, 0.2f, 0.3f)
        val other = RoomFigures.ECHOES + 1
        for (family in listOf(f.big, f.small)) {
            assertEquals(-at(family, 0, 0), at(family, other, 0), 1e-5f)
            assertEquals(-at(family, 0, 1), at(family, other, 1), 1e-5f)
        }
    }

    @Test fun theBigBallsFollowTheBassAndTheSmallOnesTheAir() {
        val quiet = RoomFigures().apply { yang(1.0, 0f, 0f, 0f) }
        val bass = RoomFigures().apply { yang(1.0, 1f, 0f, 0f) }
        val air = RoomFigures().apply { yang(1.0, 0f, 0f, 1f) }
        assertTrue(at(bass.big, 0, 2) > at(quiet.big, 0, 2) * 1.5f)
        assertEquals(at(quiet.small, 0, 2), at(bass.small, 0, 2), 1e-6f)
        assertTrue(at(air.small, 0, 2) > at(quiet.small, 0, 2) * 1.5f)
        assertEquals(at(quiet.big, 0, 2), at(air.big, 0, 2), 1e-6f)
    }

    @Test fun eachEchoIsSmallerLighterAndFurtherBack() {
        val f = RoomFigures().apply { advance(2f, 0f, 0f); yang(2.0, 0f, 0f, 0f) }
        var angle = atan2(at(f.big, 0, 1).toDouble(), at(f.big, 0, 0) / RoomFigures.WIDE)
        for (k in 1..RoomFigures.ECHOES) {
            assertTrue(at(f.big, k, 2) < at(f.big, k - 1, 2))
            assertTrue(at(f.big, k, 3) < at(f.big, k - 1, 3))
            val a = atan2(at(f.big, k, 1).toDouble(), at(f.big, k, 0) / RoomFigures.WIDE)
            // Behind: the room turns anticlockwise, so each echo is at a smaller angle (mod a turn).
            val step = Math.IEEEremainder(angle - a, 2 * Math.PI)
            assertTrue("echo $k is not behind", step > 0.0)
            angle = a
        }
        assertEquals(1f, at(f.big, 0, 3), 0f)
    }

    @Test fun theBassTurnsYangFaster() {
        val calm = RoomFigures().apply { advance(1f, 0f, 0f) }
        val loud = RoomFigures().apply { advance(1f, 1f, 0f) }
        assertTrue(loud.spin > calm.spin * 2)
    }

    @Test fun aStillRoomKeepsItsPose() {
        val f = RoomFigures().apply { advance(5f, 0.5f, 0.5f) }
        f.strands(0f, 0f, 0f)
        val before = f.strandA.copyOf()
        f.advance(0f, 1f, 1f)
        f.strands(0f, 0f, 0f)
        assertArrayEquals(before, f.strandA, 0f)
    }

    @Test fun lavaStaysOnTheScreen() {
        val f = RoomFigures()
        repeat(2000) {
            f.advance(0.25f, 0.6f, 0.3f)
            f.lava(0.6f, 0.3f)
            for (i in 0 until RoomFigures.LAVA_BLOBS) {
                assertTrue(abs(at(f.blobs, i, 0)) < 0.89f)
                assertTrue(abs(at(f.blobs, i, 1)) <= 0.45f)
            }
        }
    }

    @Test fun lavasBackgroundSwellsWithTheMiddle() {
        val quiet = RoomFigures().apply { lava(0f, 0f) }
        val loud = RoomFigures().apply { lava(0f, 1f) }
        assertTrue(loud.back[2] > quiet.back[2] * 1.4f)
    }

    @Test fun aLoudBandLiftsAndBrightensItsColumns() {
        val quiet = RoomFigures().apply { strands(0f, 0f, 0f) }
        val bass = RoomFigures().apply { strands(1f, 0f, 0f) }
        for (i in 0 until RoomFigures.STRANDS) {
            val onBass = RoomFigures.STRAND_BAND[i] == 0
            assertEquals(onBass, at(bass.strandA, i, 1) > at(quiet.strandA, i, 1))
            assertEquals(onBass, at(bass.strandA, i, 3) > at(quiet.strandA, i, 3))
        }
        assertTrue(bass.floor[1] > quiet.floor[1])
    }

    @Test fun eachHeadSitsOnItsColumnAboveItsTop() {
        val f = RoomFigures().apply { advance(7f, 0f, 0.4f); strands(0.2f, 0.4f, 0.6f) }
        for (i in 0 until RoomFigures.STRANDS) {
            val headY = f.heads[i * 2 + 1]
            assertTrue(headY > at(f.strandA, i, 1))
            // Within the column's sway of its centre.
            assertTrue(hypot((f.heads[i * 2] - at(f.strandA, i, 0)).toDouble(), 0.0) <= at(f.strandB, i, 0) + 1e-6)
        }
    }
}
