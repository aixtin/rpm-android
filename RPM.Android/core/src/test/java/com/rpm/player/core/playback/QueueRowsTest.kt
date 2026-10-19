package com.rpm.player.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The iOS app's QueueRowsTests. */
class QueueRowsTest {

    private fun rows(look: QueueLook, vararg ids: String) = QueueRows.of(ids.toList(), look) { it }

    // ── what is shown ──

    @Test fun theRowsBeginWhereTheSittingDid() {
        val rows = rows(QueueLook.sitting(from = 2, playing = 2), "a", "b", "c", "d")
        assertEquals(listOf("c", "d"), rows.map { it.item })
        assertEquals(listOf(2, 3), rows.map { it.at })
    }

    @Test fun aWholeListShowsEverythingAndNothingIsPlayed() {
        val rows = rows(QueueLook.Whole, "a", "b", "c")
        assertEquals(3, rows.size)
        assertTrue(rows.all { it.state == QueueRow.State.Coming })
    }

    @Test fun aRowKnowsWhetherItIsBehindTheNeedleUnderItOrAheadOfIt() {
        val rows = rows(QueueLook.sitting(from = 0, playing = 2), "a", "b", "c", "d")
        assertEquals(
            listOf(QueueRow.State.Played, QueueRow.State.Played, QueueRow.State.Playing, QueueRow.State.Coming),
            rows.map { it.state },
        )
    }

    @Test fun aTopPastTheEndOfTheQueueIsAnEmptyListRatherThanACrash() {
        assertTrue(rows(QueueLook.sitting(from = 9, playing = null), "a", "b").isEmpty())
        assertEquals(2, rows(QueueLook.sitting(from = -3, playing = null), "a", "b").size)
    }

    // ── identity ──

    @Test fun aRowCarriesItsIdentityWithItWhenTheQueueIsReordered() {
        assertEquals(listOf("a#0", "b#0", "c#0"), rows(QueueLook.Whole, "a", "b", "c").map { it.id })
        assertEquals(listOf("c#0", "a#0", "b#0"), rows(QueueLook.Whole, "c", "a", "b").map { it.id })
    }

    @Test fun twoCopiesOfTheSameSongAreTwoDifferentRows() =
        assertEquals(listOf("a#0", "b#0", "a#1"), rows(QueueLook.Whole, "a", "b", "a").map { it.id })

    @Test fun anIdentityDoesNotChangeWhenTheSittingReachesFurtherBack() {
        assertEquals(listOf("a#1", "c#0"), rows(QueueLook.sitting(from = 2, playing = 2), "a", "b", "a", "c").map { it.id })
        assertEquals(listOf("b#0", "a#1", "c#0"), rows(QueueLook.sitting(from = 1, playing = 1), "a", "b", "a", "c").map { it.id })
    }

    // ── where a drag lands ──

    private fun drop(look: QueueLook, count: Int, from: Int, to: Int): Pair<Int, Int>? {
        val rows = QueueRows.of((0 until count).map { "t$it" }, look) { it }
        return QueueRows.drop(rows, look, from, to, count)
    }

    @Test fun aRowDraggedDownLandsOnePlaceBeforeTheRowItWasDroppedAt() {
        assertEquals(4, drop(QueueLook.Whole, 5, from = 0, to = 5)?.second)
        assertEquals(2, drop(QueueLook.Whole, 5, from = 0, to = 3)?.second)
    }

    @Test fun aRowDraggedUpLandsWhereItWasDropped() = assertEquals(1, drop(QueueLook.Whole, 5, from = 4, to = 1)?.second)

    @Test fun aDragIsTranslatedOutOfTheScreenAndIntoTheQueue() {
        // Shown: t2 playing, then t3, t4, t5. The last of them is dragged up one place.
        assertEquals(5 to 4, drop(QueueLook.sitting(from = 2, playing = 2), 6, from = 3, to = 2))
    }

    @Test fun theOneRowAfterTheNeedleCannotBeDraggedOverIt() =
        assertNull(drop(QueueLook.sitting(from = 3, playing = 3), 5, from = 1, to = 0))

    @Test fun aRowDroppedAboveTheNeedleLandsJustAfterItInstead() =
        assertEquals(3, drop(QueueLook.sitting(from = 0, playing = 2), 5, from = 4, to = 0)?.second)

    @Test fun withNothingPlayingTheFloorIsTheTopOfTheList() =
        assertEquals(2, drop(QueueLook.sitting(from = 2, playing = null), 5, from = 1, to = 0)?.second)

    @Test fun aDragThatEndsWhereItBeganIsNothingToDo() {
        assertNull(drop(QueueLook.Whole, 5, from = 2, to = 2))
        assertNull(drop(QueueLook.Whole, 5, from = 2, to = 3))
    }
}
