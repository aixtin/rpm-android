package com.rpm.player.core.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class SeekStepsTest {

    private val fourMinutes = 240_000L

    @Test fun aPressMovesTenSeconds() {
        assertEquals(70_000L, SeekSteps.next(60_000, way = 1, repeat = false, sinceMs = 5_000, durationMs = fourMinutes))
        assertEquals(50_000L, SeekSteps.next(60_000, way = -1, repeat = false, sinceMs = 5_000, durationMs = fourMinutes))
    }

    // A short track moves at the floor speed, 30 seconds a second: a 50 ms repeat moves 1.5 seconds.
    @Test fun aHeldKeyOnAShortTrackMovesThirtySecondsASecond() {
        assertEquals(61_500L, SeekSteps.next(60_000, way = 1, repeat = true, sinceMs = 50, durationMs = 120_000))
    }

    // A long track crosses in 6 seconds: an hour moves 600 seconds a second, 30 seconds per 50 ms repeat.
    @Test fun aHeldKeyOnALongTrackCrossesItInSixSeconds() {
        assertEquals(90_000L, SeekSteps.next(60_000, way = 1, repeat = true, sinceMs = 50, durationMs = 3_600_000))
    }

    // Android's first repeat comes 400 ms after the press; it counts as 250 ms, not 400.
    @Test fun theFirstRepeatCountsAQuarterOfASecondAtMost() {
        assertEquals(67_500L, SeekSteps.next(60_000, way = 1, repeat = true, sinceMs = 400, durationMs = 120_000))
    }

    @Test fun theStepStopsAtEitherEnd() {
        assertEquals(fourMinutes, SeekSteps.next(235_000, way = 1, repeat = false, sinceMs = 0, durationMs = fourMinutes))
        assertEquals(0L, SeekSteps.next(4_000, way = -1, repeat = false, sinceMs = 0, durationMs = fourMinutes))
    }

    // A repeat stamped before the step it follows moves nothing. The key's clock never runs back, but a bad
    // stamp must not move the other way.
    @Test fun aRepeatWithNoTimeSinceMovesNothing() {
        assertEquals(60_000L, SeekSteps.next(60_000, way = 1, repeat = true, sinceMs = -20, durationMs = fourMinutes))
    }
}
