package com.rpm.player.core.radio

import com.rpm.player.core.room.Rgb
import com.rpm.player.core.room.RpmPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StationGroundTest {
    /** FNV-1a 32 of "a" is 0xE40C292C; the seed folds case, so "A" is the same. */
    @Test fun theSeedIsFnv1aOverTheLowercasedName() {
        assertEquals(0xE40C292CL.toInt(), StationGround.seed("a"))
        assertEquals(StationGround.seed("a"), StationGround.seed("A"))
    }

    @Test fun theRollIsTheSameEveryTimeAndStaysInRange() {
        val one = StationGround.Roll(12345)
        val two = StationGround.Roll(12345)
        repeat(100) {
            val a = one.between(-0.15, 1.15)
            assertEquals(a, two.between(-0.15, 1.15), 0.0)
            assertTrue(a in -0.15..1.15)
        }
        // A zero seed would roll zero for ever, so it takes the fixed fallback instead.
        assertNotEquals(-0.15, StationGround.Roll(0).between(-0.15, 1.15), 0.0)
    }

    @Test fun aPaletteColourSnapsToItselfAndAGreyToNothing() {
        for (accent in RpmPalette.RING) assertEquals(accent, RpmPalette.nearest(accent))
        assertNull(RpmPalette.nearest(Rgb.of(128, 128, 128)))
    }

    @Test fun lightAndDarkInkAreTheAppsOwnTest() {
        assertTrue(StationGround.isLight(Rgb.of(240, 240, 230)))
        assertTrue(!StationGround.isLight(Rgb.of(20, 30, 90)))
    }
}
