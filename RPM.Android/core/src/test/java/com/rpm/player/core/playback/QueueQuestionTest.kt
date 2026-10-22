package com.rpm.player.core.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The iPhone's `QueueEditTests` for `hasChangesAhead`, case for case. */
class QueueQuestionTest {

    private val album = (0 until 10).map { "t$it" }

    private fun changed(queue: List<String>, current: Int?, started: List<String>) = hasChangesAhead(queue, current, started)

    @Test fun anAlbumPlayingInOrderHasNoChangesAhead() {
        assertFalse(changed(album, 0, album))
        assertFalse(changed(album, 3, album))
    }

    @Test fun anAlbumStartedPartWayInHasNoChangesAhead() = assertFalse(changed(album, 8, album))

    @Test fun nothingLeftToComeIsNeverChanged() {
        assertFalse(changed(album, 9, album))
        assertFalse(changed(listOf("x", "y"), 1, album))
        assertFalse(changed(emptyList(), null, album))
    }

    @Test fun aSongAddedAfterThePlayingOneIsAChange() =
        assertTrue(changed(album.toMutableList().apply { add(4, "x") }, 3, album))

    /** An accepted over-report, as on iPhone: while the added song plays, nothing says where in the album it is. */
    @Test fun theAddedSongPlayingStillCountsAsAChange() =
        assertTrue(changed(album.toMutableList().apply { add(4, "x") }, 4, album))

    @Test fun anAddedSongThatHasPlayedIsNoLongerAChange() =
        assertFalse(changed(album.toMutableList().apply { add(4, "x") }, 5, album))

    @Test fun aSongDraggedIntoTheUpcomingListIsAChange() {
        val queue = album.toMutableList()
        queue.add(4, queue.removeAt(8))
        assertTrue(changed(queue, 3, album))
    }

    @Test fun removingTheNextSongIsAChange() = assertTrue(changed(album.toMutableList().apply { removeAt(4) }, 3, album))

    @Test fun removingTheLastSongIsAChange() = assertTrue(changed(album.toMutableList().apply { removeAt(9) }, 3, album))

    @Test fun removingAPlayedSongIsNotAChange() = assertFalse(changed(album.toMutableList().apply { removeAt(1) }, 2, album))

    @Test fun removingThePlayingSongIsAChange() = assertTrue(changed(album.toMutableList().apply { removeAt(3) }, null, album))

    @Test fun nothingPlayingWithTheQueueAsStartedIsNotAChange() = assertFalse(changed(album, null, album))

    @Test fun songsAddedToAnEmptyPlayerAreAChange() = assertTrue(changed(listOf("x"), null, emptyList()))

    @Test fun moreToComeThanTheListHadIsAChange() = assertTrue(changed(listOf("x") + album, 0, album))

    @Test fun twoCopiesOfOneSongSwappedAreNotAChange() {
        val started = listOf("a", "b", "a", "c")
        assertFalse(changed(listOf("a", "b", "a", "c"), 0, started))
    }
}
