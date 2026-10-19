package com.rpm.player.core.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pacing rules of [CastFeed], on a clock the test moves. */
class CastFeedTest {
    private val rate = 44_100
    private var now = 1_000_000_000L
    private val served = mutableListOf<Pair<CastStream?, Int>>()
    private val began = mutableListOf<CastStream>()
    private val ring = PcmRing(CastFeed.RING_S * 48_000 * 4)
    private var ids = 0

    private val feed = CastFeed(
        ring,
        nanos = { now },
        listener = object : CastFeed.Listener {
            // What the ring held at the moment the server was told: the cut must come before the emptying.
            override fun serve(stream: CastStream?) {
                served += stream to ring.held
            }

            override fun began(stream: CastStream) {
                began += stream
            }
        },
        newId = { "s${++ids}" },
    )

    private fun seconds(s: Double): Long = (s * 1e9).toLong()
    private fun advance(s: Double) {
        now += seconds(s)
    }

    /** Writes [frames] as the sink does: what [CastFeed.take] allows, into the ring. */
    private fun write(frames: Int): Int {
        val allowed = feed.take(frames)
        if (allowed > 0) {
            ring.write(ByteArray(allowed * 4), 0, allowed * 4)
            feed.wrote(allowed)
        }
        return allowed
    }

    private fun drainRing() {
        val pail = ByteArray(1 shl 20)
        val of = feed.current?.generation ?: 0
        do {
            val took = ring.read(pail, 0, of)
        } while (took > 0)
    }

    @Test
    fun `nothing is taken before a stream has begun`() {
        assertEquals(0, feed.take(1_000))
    }

    @Test
    fun `before the room has said anything the ring fills to its capacity less a second`() {
        feed.begin(rate)
        val capacityFrames = ring.capacity / 4
        val headStart = capacityFrames - rate
        var total = 0
        while (true) {
            val took = write(4_096)
            if (took == 0) break
            total += took
        }
        assertEquals(headStart, total)
        assertTrue(feed.heldSeconds() > 5.0)
    }

    @Test
    fun `once the room reports, the decoder runs at most eight seconds ahead of it`() {
        val stream = feed.begin(rate)
        write(rate)
        drainRing()
        feed.says(stream.generation, 0.5)
        // Written 1 s, heard 0.5 s: 7.5 s more may go in, and no more.
        var total = 0
        while (true) {
            drainRing()
            val took = write(4_096)
            if (took == 0) break
            total += took
        }
        assertEquals((CastFeed.LEAD_S * rate).toInt() - rate / 2, total)
        // The room moves on by a second: a second more may go in.
        feed.says(stream.generation, 1.5)
        drainRing()
        assertEquals(rate, write(10 * rate))
    }

    @Test
    fun `what is taken never overfills the ring`() {
        val stream = feed.begin(rate)
        feed.says(stream.generation, 0.0)
        val fit = ring.free / 4
        // The lead would allow eight seconds; the ring holds six at 48 kHz, so less at 44.1 kHz.
        assertEquals(minOf(fit, (CastFeed.LEAD_S * rate).toInt()), feed.take(Int.MAX_VALUE))
    }

    @Test
    fun `the room's position is filled in between reports, at most a second and never past what was written`() {
        val stream = feed.begin(rate)
        write(3 * rate)
        assertNull(feed.heardFrames())
        feed.says(stream.generation, 1.0)
        advance(0.5)
        assertEquals((1.5 * rate).toLong(), feed.heardFrames())
        advance(5.0)
        assertEquals((2.0 * rate).toLong(), feed.heardFrames())
        // A report behind what was handed out does not walk the needle back.
        feed.says(stream.generation, 1.2)
        assertEquals((2.0 * rate).toLong(), feed.heardFrames())
        // Never past what was written.
        feed.says(stream.generation, 2.9)
        advance(0.9)
        assertEquals(3L * rate, feed.heardFrames())
    }

    @Test
    fun `a paused room stands still, and does not jump when it resumes`() {
        val stream = feed.begin(rate)
        write(5 * rate)
        feed.says(stream.generation, 1.0)
        advance(0.4)
        feed.paused = true
        val atPause = feed.heardFrames()!!
        assertEquals(rate + (0.4 * rate).toLong(), atPause)
        advance(60.0)
        assertEquals(atPause, feed.heardFrames())
        feed.paused = false
        assertEquals(atPause, feed.heardFrames())
        advance(0.25)
        assertEquals(atPause + (0.25 * rate).toLong(), feed.heardFrames())
    }

    @Test
    fun `a report about a replaced stream changes nothing`() {
        val first = feed.begin(rate)
        val second = feed.begin(rate)
        write(rate)
        feed.says(first.generation, 0.9)
        assertNull(feed.heardFrames())
        feed.says(second.generation, 0.2)
        assertEquals((0.2 * rate).toLong(), feed.heardFrames())
    }

    @Test
    fun `a playing room whose position stops for thirty seconds has stalled, a paused one has not`() {
        val stream = feed.begin(rate)
        write(5 * rate)
        assertFalse("not before its first report", feed.stalled())
        feed.says(stream.generation, 1.0)
        advance(29.0)
        assertFalse(feed.stalled())
        advance(2.0)
        assertTrue(feed.stalled())
        feed.paused = true
        assertFalse(feed.stalled())
        advance(600.0)
        assertFalse(feed.stalled())
        // Resumed after ten minutes: the watch starts again from the resume.
        feed.paused = false
        assertFalse(feed.stalled())
        advance(31.0)
        assertTrue(feed.stalled())
    }

    @Test
    fun `a room that has played nearly everything has not stalled, it has drained`() {
        val stream = feed.begin(rate)
        write(2 * rate)
        feed.says(stream.generation, 1.7)
        advance(40.0)
        assertFalse(feed.stalled())
        assertTrue(feed.drained())
    }

    @Test
    fun `the end of the stream waits for the room, within half a second`() {
        val stream = feed.begin(rate)
        assertTrue("nothing written is nothing to wait for", feed.drained())
        assertEquals(4 * rate, write(4 * rate))
        assertFalse(feed.drained())
        feed.says(stream.generation, 3.0)
        assertFalse(feed.drained())
        feed.says(stream.generation, 3.6)
        assertTrue(feed.drained())
        assertTrue(feed.pending())
        feed.says(stream.generation, 4.0)
        assertFalse(feed.pending())
    }

    @Test
    fun `a new stream tells the server before the ring is emptied, then starts its numbers again`() {
        val first = feed.begin(rate)
        write(rate)
        feed.says(first.generation, 0.5)
        val turns = feed.turns
        val second = feed.begin(rate)
        assertEquals(second, served.last().first)
        assertEquals("the server was told while the ring still held the old stream", rate * 4, served.last().second)
        assertEquals(0, ring.held)
        assertEquals(listOf(first, second), began)
        assertEquals(2, second.generation)
        assertEquals("s2", second.id)
        assertEquals(0L, feed.writtenFrames)
        assertNull(feed.heardFrames())
        assertTrue(feed.turns > turns)
    }

    @Test
    fun `a flush serves nothing, empties the ring after, and wants a new stream`() {
        val stream = feed.begin(rate)
        write(rate)
        feed.says(stream.generation, 0.5)
        feed.takenBack()
        assertNull(served.last().first)
        assertEquals(rate * 4, served.last().second)
        assertEquals(0, ring.held)
        assertNull(feed.current)
        assertEquals(0, feed.take(100))
        assertEquals(0.0, feed.inTheStream(), 0.0)
    }

    @Test
    fun `a reader of a replaced stream takes nothing of the next`() {
        val first = feed.begin(rate)
        feed.begin(rate)
        write(rate)
        assertEquals(-1, ring.read(ByteArray(1024), 0, first.generation))
        assertEquals(rate * 4, ring.held)
    }
}
