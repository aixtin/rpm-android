package com.rpm.player.core.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A download waits for the stored rendition instead of taking a live encode. These cover how long the wait lasts, how
 * often the store is checked, and what a change of rate does to it. Ported from the iPhone's `RenditionWaitTests`. */
class RenditionWaitTest {
    private val start = 1_000_000_000L
    private fun at(seconds: Long) = start + seconds * 1000

    @Test fun aTrackNobodyIsWaitingOnIsDue() {
        val wait = RenditionWait()
        assertTrue(wait.isDue("t1", 320, start))
        assertFalse(wait.isWaiting("t1"))
        assertNull(wait.nextCheck)
    }

    @Test fun theFirstLiveAnswerStartsAWaitAndIsNotDueUntilTheFirstGap() {
        val wait = RenditionWait()
        assertEquals(RenditionWait.Said.New, wait.stillMaking("t1", 320, start))
        assertTrue(wait.isWaiting("t1"))
        assertFalse(wait.isDue("t1", 320, start + RenditionWait.FIRST_MS - 1000))
        assertTrue(wait.isDue("t1", 320, start + RenditionWait.FIRST_MS))
        assertEquals(start + RenditionWait.FIRST_MS, wait.nextCheck)
    }

    @Test fun theGapDoublesAndStopsAtTheLongest() {
        val wait = RenditionWait()
        wait.stillMaking("t1", 320, start)
        assertEquals(RenditionWait.Said.Waiting, wait.stillMaking("t1", 320, at(15)))
        assertEquals(at(15 + 30), wait.nextCheck)
        assertEquals(RenditionWait.Said.Waiting, wait.stillMaking("t1", 320, at(45)))
        assertEquals(at(45 + 60), wait.nextCheck)
        assertEquals(RenditionWait.Said.Waiting, wait.stillMaking("t1", 320, at(105)))
        assertEquals(at(105) + RenditionWait.LONGEST_MS, wait.nextCheck)
    }

    @Test fun aChangeOfRateMakesTheTrackDueAtOnceAndStartsANewWait() {
        val wait = RenditionWait()
        wait.stillMaking("t1", 320, start)
        wait.asked(listOf("t1"), 320)
        assertTrue(wait.isDue("t1", 256, at(1)))
        assertEquals(RenditionWait.Said.New, wait.stillMaking("t1", 256, at(1)))
        // The new rate has not been asked for, whatever the old one was.
        assertEquals(mapOf(256 to listOf("t1")), wait.unasked)
    }

    @Test fun waitingTooLongIsOverdueAndAsksTheStoreAgain() {
        val wait = RenditionWait()
        wait.stillMaking("t1", 320, start)
        wait.asked(listOf("t1"), 320)
        assertTrue(wait.unasked.isEmpty())

        val late = start + RenditionWait.GIVE_UP_MS + 1000
        assertEquals(RenditionWait.Said.Overdue, wait.stillMaking("t1", 320, late))
        // The wait starts again from now, and the ask goes out again.
        assertEquals(mapOf(320 to listOf("t1")), wait.unasked)
        assertEquals(late + RenditionWait.FIRST_MS, wait.nextCheck)
        assertEquals(RenditionWait.Said.Waiting, wait.stillMaking("t1", 320, late + 20_000))
    }

    @Test fun unaskedIsGroupedByRate() {
        val wait = RenditionWait()
        wait.stillMaking("b", 320, start)
        wait.stillMaking("a", 320, start)
        wait.stillMaking("c", 192, start)
        assertEquals(mapOf(320 to listOf("a", "b"), 192 to listOf("c")), wait.unasked)
        wait.asked(listOf("a", "b"), 320)
        assertEquals(mapOf(192 to listOf("c")), wait.unasked)
    }

    @Test fun anAskAtTheOldRateDoesNotMarkAWaitRestartedAtANewOne() {
        val wait = RenditionWait()
        wait.stillMaking("t1", 320, start)
        // The rate changed while the ask for 320 was out.
        wait.stillMaking("t1", 256, at(1))
        wait.asked(listOf("t1"), 320)
        assertEquals(mapOf(256 to listOf("t1")), wait.unasked)
    }

    @Test fun anAskForATrackNoLongerWaitedOnChangesNothing() {
        val wait = RenditionWait()
        wait.stillMaking("t1", 320, start)
        wait.forget(listOf("t1"))
        wait.asked(listOf("t1"), 320)
        assertTrue(wait.isEmpty)
        assertTrue(wait.unasked.isEmpty())
    }

    @Test fun forgettingEndsTheWait() {
        val wait = RenditionWait()
        wait.stillMaking("t1", 320, start)
        wait.stillMaking("t2", 320, at(5))
        wait.forget(listOf("t1"))
        assertFalse(wait.isWaiting("t1"))
        assertTrue(wait.isDue("t1", 320, at(6)))
        assertEquals(at(5) + RenditionWait.FIRST_MS, wait.nextCheck)
        wait.forgetAll()
        assertTrue(wait.isEmpty)
        assertNull(wait.nextCheck)
    }
}
