package com.rpm.player.core.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StationCounterTest {

    private var nowMs = 0L
    private val counter = StationCounter { nowMs }
    private val one = RadioStation(uuid = "one", name = "One", streamUrl = "https://one.example")
    private val two = RadioStation(uuid = "two", name = "Two", streamUrl = "https://two.example")

    /** Reads [seconds] times, a second apart, and returns every part handed out. */
    private fun play(station: RadioStation?, seconds: Int, playing: Boolean = true): List<StationCounter.Heard> =
        (1..seconds).mapNotNull {
            nowMs += 1_000
            counter.read(station, playing)
        }

    @Test
    fun `a listen under 30 seconds is not handed out when the station changes`() {
        play(one, 20)
        assertEquals(emptyList<StationCounter.Heard>(), play(two, 1))
    }

    @Test
    fun `changing station hands out the old station's part before starting the new one`() {
        play(one, 41)
        val parts = play(two, 1)
        assertEquals(1, parts.size)
        assertEquals("one", parts[0].station.uuid)
        assertEquals(40.0, parts[0].seconds, 0.001)
        assertEquals(true, parts[0].newTune)
    }

    @Test
    fun `a part goes out every 300 seconds and only the first is a new tune`() {
        val parts = play(one, 601)
        assertEquals(listOf(300.0, 300.0), parts.map { it.seconds })
        assertEquals(listOf(true, false), parts.map { it.newTune })
    }

    @Test
    fun `paused time and long gaps are not heard`() {
        play(one, 1)
        play(one, 100, playing = false)
        nowMs += 60_000
        counter.read(one, true)
        play(one, 20)
        // 20 one-second steps; the paused stretch and the 60-second gap count for nothing.
        assertNull(counter.handOut())
        play(one, 10)
        assertEquals(30.0, counter.handOut()!!.seconds, 0.001)
    }

    @Test
    fun `nothing playing ends the listen`() {
        play(one, 45)
        val parts = play(null, 1)
        assertEquals(44.0, parts.single().seconds, 0.001)
    }
}
