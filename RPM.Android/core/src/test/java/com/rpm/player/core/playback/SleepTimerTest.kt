package com.rpm.player.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The iPhone's SleepTimerTests, for the same rules. */
class SleepTimerTest {
    private val minute = 60_000L

    @Test fun aTimedChoiceSetsADeadlineFromNow() {
        val timer = SleepTimer().set(SleepTimer.Choice.After(5 * minute), nowMs = 1_000)
        assertEquals(SleepTimer.Setting.At(1_000 + 5 * minute), timer.setting)
        assertEquals(5 * minute, timer.left(nowMs = 1_000, trackLeftMs = null))
    }

    @Test fun addingToATimedOneExtendsItsDeadline() {
        val timer = SleepTimer().set(SleepTimer.Choice.After(5 * minute), 0).add(15 * minute, nowMs = minute, trackLeftMs = 30_000)
        assertEquals(SleepTimer.Setting.At(20 * minute), timer.setting)
    }

    @Test fun addingToAnEndOfTrackOneCountsFromTheEndOfTheTrack() {
        val timer = SleepTimer().set(SleepTimer.Choice.EndOfTrack, 0).add(5 * minute, nowMs = 10_000, trackLeftMs = minute)
        assertEquals(SleepTimer.Setting.At(10_000 + 6 * minute), timer.setting)
    }

    @Test fun anEndOfTrackOneIsLeftWhatTheTrackHasLeft() {
        val timer = SleepTimer().set(SleepTimer.Choice.EndOfTrack, 0)
        assertEquals(42_000L, timer.left(nowMs = 0, trackLeftMs = 42_000))
        assertNull(timer.left(nowMs = 0, trackLeftMs = null))
    }

    @Test fun aStaleWakeDoesNothing() {
        val timer = SleepTimer().set(SleepTimer.Choice.After(5 * minute), 0).add(5 * minute, 0, null)
        val (sleeps, after) = timer.deadlineArrived(nowMs = 5 * minute)
        assertFalse(sleeps)
        assertEquals(timer, after)
    }

    @Test fun theDeadlinePausesAndTurnsTheTimerOff() {
        val (sleeps, after) = SleepTimer().set(SleepTimer.Choice.After(minute), 0).deadlineArrived(nowMs = minute)
        assertTrue(sleeps)
        assertFalse(after.isOn)
    }

    @Test fun aPassedDeadlineCannotSilenceAPlay() {
        val timer = SleepTimer().set(SleepTimer.Choice.After(minute), 0).somebodyAskedForSound(nowMs = 2 * minute)
        assertFalse(timer.isOn)
    }

    @Test fun aStationEndsAnEndOfTrackTimerButNotATimedOne() {
        assertFalse(SleepTimer().set(SleepTimer.Choice.EndOfTrack, 0).stationTuned().isOn)
        assertTrue(SleepTimer().set(SleepTimer.Choice.After(minute), 0).stationTuned().isOn)
    }

    @Test fun onlyAnEndOfTrackTimerFiresAtTheEndOfATrack() {
        assertTrue(SleepTimer().set(SleepTimer.Choice.EndOfTrack, 0).trackEnded().first)
        assertFalse(SleepTimer().set(SleepTimer.Choice.After(minute), 0).trackEnded().first)
    }
}
