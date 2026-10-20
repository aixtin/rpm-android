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
        for ((family, other) in listOf(f.big to RoomFigures.BIG_ECHOES + 1, f.small to RoomFigures.SMALL_ECHOES + 1)) {
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
        for (k in 1..RoomFigures.BIG_ECHOES) {
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

    @Test fun theSmallBallsTrailIsOneStreakWhateverTheSpin() {
        // Echoes stand closer than the smaller of two neighbours' radii, so they overlap into one tail,
        // at any spin and at the loudest air, which spaces them furthest.
        for (spin in listOf(0f, 3f, 40f, 400f)) {
            val f = RoomFigures().apply { advance(spin, 1f, 0f); yang(spin.toDouble(), 1f, 0f, 1f) }
            var length = 0.0
            for (k in 1..RoomFigures.SMALL_ECHOES) {
                val gap = hypot((at(f.small, k, 0) - at(f.small, k - 1, 0)).toDouble(), (at(f.small, k, 1) - at(f.small, k - 1, 1)).toDouble())
                assertTrue("echo $k is $gap away", gap < at(f.small, k, 2))
                length += gap
            }
            // Spaced by length along the orbit: the same tail at every spin (chords, so a little short).
            assertEquals(0.045 * 1.4 * RoomFigures.SMALL_ECHOES, length, 0.012)
        }
    }

    @Test fun aStillRoomKeepsItsPose() {
        val f = RoomFigures().apply { advance(5f, 0.5f, 0.5f) }
        f.ribbons(0f, 0f, 0f)
        val before = f.ribbonB.copyOf()
        f.advance(0f, 1f, 1f)
        f.ribbons(0f, 0f, 0f)
        assertArrayEquals(before, f.ribbonB, 0f)
    }

    @Test fun aFreshRoomIsTheSkinsPicture() {
        // At drift 0 every swing is at its middle: the script's numbers (make_rpamp_ground.py's BANDS).
        val f = RoomFigures().apply { ribbons(0f, 0f, 0f) }
        assertEquals(0.44f, at(f.ribbonA, 1, 0), 1e-6f)
        assertEquals(0.13f, at(f.ribbonA, 1, 2), 1e-6f)
        assertEquals(2.1f, at(f.ribbonB, 1, 0), 1e-6f)
        assertEquals(0.16f, at(f.ribbonB, 1, 2), 1e-6f)
        assertEquals(0.90f, at(f.ribbonC, 1, 1), 1e-6f)
        assertEquals(0.22f, at(f.ribbonC, 1, 2), 1e-6f)
    }

    @Test fun theRibbonsSwingButNeverWanderOff() {
        val f = RoomFigures()
        repeat(4000) {
            f.advance(0.25f, 0f, 1f)
            f.ribbons(0f, 0f, 0f)
            for (i in 0 until RoomFigures.RIBBONS) {
                val start = RoomFigures().apply { ribbons(0f, 0f, 0f) }
                assertTrue(abs(at(f.ribbonB, i, 0) - at(start.ribbonB, i, 0)) <= 1.1f + 1e-5f)
                assertTrue(abs(at(f.ribbonA, i, 0) - at(start.ribbonA, i, 0)) <= 0.025f + 1e-5f)
            }
        }
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

    @Test fun theBassSwellsTheMainRibbonAndTheAirFlaresItsFilament() {
        val quiet = RoomFigures().apply { ribbons(0f, 0f, 0f) }
        val bass = RoomFigures().apply { ribbons(1f, 0f, 0f) }
        val air = RoomFigures().apply { ribbons(0f, 0f, 1f) }
        assertTrue(at(bass.ribbonC, 1, 1) > at(quiet.ribbonC, 1, 1))
        assertTrue(at(bass.ribbonB, 1, 2) > at(quiet.ribbonB, 1, 2))
        assertEquals(at(quiet.ribbonC, 2, 1), at(bass.ribbonC, 2, 1), 0f)
        assertTrue(at(air.ribbonC, 1, 2) > at(quiet.ribbonC, 1, 2) * 2)
        assertEquals(at(quiet.ribbonC, 0, 2), at(air.ribbonC, 0, 2), 0f)
    }
}
