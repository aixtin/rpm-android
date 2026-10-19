package com.rpm.player.core.cast

import com.rpm.player.core.cast.RoomStream.Because
import com.rpm.player.core.cast.RoomStream.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The reading rules, as in the iOS app's `RoomStreamTests`. */
class RoomStreamTest {
    private fun playing(session: Int) = RoomReport.Holding(MediaStatus(session, "PLAYING", 1.0, null))
    private fun paused(session: Int) = RoomReport.Holding(MediaStatus(session, "PAUSED", 1.0, null))
    private fun idle(session: Int, reason: String?) = RoomReport.Holding(MediaStatus(session, "IDLE", null, reason))

    /** A room holding our stream under session 7. */
    private fun holding(): RoomStream = RoomStream().apply {
        pickingUp()
        nowHolding(7)
    }

    private fun RoomStream.read(report: RoomReport, paused: Boolean = false): Word = reading(report, turns, stamp, paused)

    @Test
    fun `nothing the room says before it holds our stream is news`() {
        val room = RoomStream().apply { pickingUp() }
        assertEquals(Word.Nothing, room.read(RoomReport.NothingLoaded))
        assertEquals(Word.Nothing, room.read(idle(3, "CANCELLED")))
    }

    @Test
    fun `holding nothing while it holds our stream is a stop at the speaker`() {
        assertEquals(Word.Stopped(Because.HOLDING_NOTHING), holding().read(RoomReport.NothingLoaded))
    }

    @Test
    fun `a reply that crossed a track change is dropped`() {
        val room = holding()
        val asked = room.turns
        val told = room.stamp
        // The skip: our stream comes off the receiver while the question is on the wire.
        room.takenBack()
        assertEquals(Word.Nothing, room.reading(RoomReport.NothingLoaded, asked, told, paused = false))
        // And after the next LOAD the old question is still about the old state.
        room.nowHolding(8)
        assertEquals(Word.Nothing, room.reading(RoomReport.NothingLoaded, asked, told, paused = false))
    }

    @Test
    fun `the room's emptiness during a changeover is our own doing`() {
        val room = holding()
        room.takenBack()
        assertNull(room.session)
        assertEquals(Word.Nothing, room.read(RoomReport.NothingLoaded))
    }

    @Test
    fun `a hand on the speaker pauses and resumes`() {
        val room = holding()
        assertEquals(Word.Pause, room.read(paused(7), paused = false))
        assertEquals(Word.Nothing, room.read(paused(7), paused = true))
        assertEquals(Word.Resume, room.read(playing(7), paused = true))
        assertEquals(Word.Nothing, room.read(playing(7), paused = false))
    }

    @Test
    fun `a reply from before a key is not a hand on the speaker`() {
        val room = holding()
        val asked = room.turns
        val told = room.stamp
        // Pause pressed here, after the question went out.
        room.toldIt()
        room.heardBack()
        assertEquals(Word.Nothing, room.reading(playing(7), asked, told, paused = true))
    }

    @Test
    fun `a question asked while a key is on its way has no date`() {
        val room = holding()
        room.toldIt()
        assertNull(room.stamp)
        assertEquals(Word.Nothing, room.reading(paused(7), room.turns, room.stamp, paused = false))
        room.heardBack()
        assertEquals(Word.Pause, room.read(paused(7), paused = false))
    }

    @Test
    fun `a status about another media session is not about our stream`() {
        assertEquals(Word.Nothing, holding().read(paused(6), paused = false))
    }

    @Test
    fun `IDLE for a stop is a stop, IDLE for a finish is not`() {
        assertEquals(Word.Stopped(Because.LET_THE_MUSIC_GO), holding().read(idle(7, "CANCELLED")))
        assertEquals(Word.Stopped(Because.LET_THE_MUSIC_GO), holding().read(idle(7, "INTERRUPTED")))
        assertEquals(Word.Nothing, holding().read(idle(7, "FINISHED")))
        assertEquals(Word.Nothing, holding().read(idle(7, null)))
        assertEquals(Word.Nothing, holding().read(RoomReport.NoAnswer))
    }

    @Test
    fun `the receiver app gone is a stop, unless our stream was just taken off`() {
        val ours = ReceiverStatus(listOf(ReceiverStatus.Application(CastApps.RPM, "app-1", "t-1", emptyList())), null)
        val gone = ReceiverStatus(emptyList(), null)
        val room = holding()
        assertEquals(Word.Nothing, room.reading(ours, "app-1", room.turns))
        assertEquals(Word.Stopped(Because.CLOSED_THE_RECEIVER), room.reading(gone, "app-1", room.turns))
        val asked = room.turns
        room.takenBack()
        assertEquals(Word.Nothing, room.reading(gone, "app-1", asked))
        assertEquals(Word.Nothing, room.reading(gone, "app-1", room.turns))
    }

    @Test
    fun `fetched moves holding to playing and nothing else`() {
        val room = RoomStream()
        room.itFetched()
        assertEquals(RoomStream.Stage.None, room.stage)
        room.pickingUp()
        room.nowHolding(3)
        room.itFetched()
        assertEquals(RoomStream.Stage.Playing(3), room.stage)
        assertEquals(3, room.session)
        room.gone()
        assertNull(room.session)
    }
}
