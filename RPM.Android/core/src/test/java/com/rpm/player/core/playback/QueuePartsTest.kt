package com.rpm.player.core.playback

import com.rpm.player.core.playback.QueuePart.ResumeHeading
import com.rpm.player.core.playback.QueuePart.Song
import com.rpm.player.core.playback.QueuePart.UpNextHeading
import org.junit.Assert.assertEquals
import org.junit.Test

/** The queue drawer's headings over the songs the person added ([QueueParts]). */
class QueuePartsTest {

    /** "o" is a song of the list that was played, "+" one the person added. */
    private fun parts(queue: String) = QueueParts.of(queue.map { it == '+' })

    @Test fun aQueueWithNothingAddedHasNoHeadings() {
        assertEquals(listOf(Song(0), Song(1), Song(2)), parts("ooo"))
    }

    @Test fun anEmptyQueueHasNoLines() {
        assertEquals(emptyList<QueuePart>(), parts(""))
    }

    @Test fun addedSongsInTheMiddleGetUpNextAboveAndTheListResumesUnderAHeading() {
        assertEquals(
            listOf(Song(0), Song(1), UpNextHeading(2), Song(2), Song(3), ResumeHeading(4), Song(4), Song(5)),
            parts("oo++oo"),
        )
    }

    @Test fun aRunAtTheEndHasNoResumeHeading() {
        assertEquals(listOf(Song(0), UpNextHeading(1), Song(1), Song(2)), parts("o++"))
    }

    @Test fun aRunAtTheTopStillGetsItsHeading() {
        assertEquals(listOf(UpNextHeading(0), Song(0), ResumeHeading(1), Song(1)), parts("+o"))
    }

    @Test fun everyRunGetsItsOwnHeading() {
        assertEquals(
            listOf(Song(0), UpNextHeading(1), Song(1), ResumeHeading(2), Song(2), UpNextHeading(3), Song(3)),
            parts("o+o+"),
        )
    }

    @Test fun theSongsKeepTheirPlayOrder() {
        val songs = parts("o+o++oo+").filterIsInstance<Song>().map { it.at }
        assertEquals((0 until 8).toList(), songs)
    }

    @Test fun aSongIsFoundPastTheHeadingsAboveIt() {
        val parts = parts("o++o")
        assertEquals(0, QueueParts.placeOf(parts, 0))
        assertEquals(2, QueueParts.placeOf(parts, 1))
        assertEquals(5, QueueParts.placeOf(parts, 3))
        assertEquals(-1, QueueParts.placeOf(parts, 9))
    }
}
