package com.rpm.player.core.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class SittingTest {

    private val record = (1..12).map { "t$it" }

    private fun startedAt(start: Int, ids: List<String> = record): Sitting =
        Sitting().apply {
            handed(ids, start, playing = emptyList())
            settle(ids, start)
        }

    @Test fun aRecordStartedAtTrackTenBeginsTheSittingThere() = assertEquals(9, startedAt(9).from)

    @Test fun playingOnLeavesTheSittingWhereItBegan() {
        val sitting = startedAt(9)
        assertEquals(9, sitting.settle(record, 10))
        assertEquals(9, sitting.settle(record, 11))
    }

    @Test fun backAboveTheStartBringsThatTrackIntoTheSitting() {
        val sitting = startedAt(9)
        assertEquals(8, sitting.settle(record, 8))
        // And it stays there when the player moves on again.
        assertEquals(8, sitting.settle(record, 10))
    }

    @Test fun aNewListStartsANewSitting() {
        val sitting = startedAt(9)
        val other = (1..5).map { "o$it" }
        sitting.handed(other, 2, playing = record)
        assertEquals(2, sitting.settle(other, 2))
    }

    @Test fun theSameListHandedBackIsTheSittingAlreadyShown() {
        val sitting = startedAt(9)
        // A song further down the record is tapped while the record plays: the list handed over is the one playing.
        sitting.handed(record, 11, playing = record)
        assertEquals(9, sitting.settle(record, 11))
    }

    @Test fun anEntryRemovedAboveTheSittingKeepsItOnTheSameEntry() {
        val sitting = startedAt(9)
        val fewer = record - "t2"
        assertEquals(8, sitting.settle(fewer, 9))
    }

    @Test fun anEntryRemovedBelowChangesNothing() {
        val sitting = startedAt(9)
        assertEquals(9, sitting.settle(record - "t12", 9))
    }

    @Test fun aSecondCopyOfTheFirstEntryIsNotTakenForIt() {
        // t10 queued again at the end: the sitting still begins at the first copy.
        val sitting = startedAt(9)
        assertEquals(9, sitting.settle(record + "t10", 10))
    }

    @Test fun theFirstEntryRemovedKeepsTheCountAndNeverPassesThePlayingRow() {
        val sitting = startedAt(9)
        sitting.settle(record, 10)
        val without = record - "t10"
        assertEquals(9, sitting.settle(without, 9))
    }

    @Test fun anEmptiedQueueHasNoSitting() = assertEquals(0, startedAt(9).settle(emptyList(), -1))

    // ── as a controller shows it ──

    @Test fun aControllerShowsWhatTheSessionSaid() = assertEquals(9, Sitting.shownFrom(published = 9, count = 12, playing = 10))

    @Test fun aControllerAheadOfTheSessionStillShowsThePlayingRow() =
        assertEquals(8, Sitting.shownFrom(published = 9, count = 12, playing = 8))

    @Test fun aControllerNeverShowsFromPastTheEnd() = assertEquals(3, Sitting.shownFrom(published = 9, count = 3, playing = null))
}
