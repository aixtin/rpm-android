package com.rpm.player.core.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CastVolumeTest {
    private var now = 10_000L
    private val volume = CastVolume { now }

    @Test
    fun `the room's own level is taken first, so connecting does not move it`() {
        assertFalse(volume.known)
        assertEquals(20, volume.adopt(0.2))
        assertTrue(volume.known)
        assertEquals(20, volume.level)
    }

    @Test
    fun `sends are coalesced to the latest level, one at a time`() {
        assertTrue("the first change starts a send", volume.set(40))
        assertFalse("a change while one is going joins it", volume.set(45))
        assertFalse(volume.set(50))
        assertEquals(50, volume.next(lastSent = null))
        // Nothing new since 50 went: the sending stops, and the next change starts another.
        assertNull(volume.next(lastSent = 50))
        assertTrue(volume.set(55))
    }

    @Test
    fun `a different level reported just after a send is the echo of an older one`() {
        volume.adopt(0.3)
        volume.set(60)
        now += 500
        assertNull(volume.adopt(0.3))
        assertEquals(60, volume.level)
        // The level asked for, coming back, changes nothing either.
        assertNull(volume.adopt(0.6))
        // Two seconds quiet: the room was turned by somebody else.
        now += CastVolume.ECHO_MS
        assertEquals(30, volume.adopt(0.3))
    }

    @Test
    fun `a point either way is the device's rounding, not a turn of the knob`() {
        volume.adopt(0.5)
        assertNull(volume.adopt(0.51))
        assertEquals(53, volume.adopt(0.53))
    }

    @Test
    fun `levels stay between nought and a hundred`() {
        volume.set(140)
        assertEquals(100, volume.level)
        volume.set(-5)
        assertEquals(0, volume.level)
        assertNull(volume.adopt(null))
    }
}
