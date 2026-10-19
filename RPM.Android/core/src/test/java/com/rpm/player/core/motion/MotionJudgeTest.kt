package com.rpm.player.core.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionJudgeTest {
    private val interval = 16_666_667L
    private val slow = 30_000_000L
    private val onTime = 12_000_000L

    /** Feeds [count] frames, the [slowEvery]th of each run slow (1 = all slow, 0 = none), from [atMs]. */
    private fun MotionJudge.feed(count: Int, slowEvery: Int, atMs: Long): Int {
        var decided = 0
        repeat(count) { i ->
            val isSlow = slowEvery > 0 && i % slowEvery == 0
            if (frame(if (isSlow) slow else onTime, if (isSlow) slow else 2_000_000L, interval, atMs + i * 16L)) decided++
        }
        return decided
    }

    @Test
    fun `nothing is judged before it is armed`() {
        val judge = MotionJudge()
        assertEquals(0, judge.feed(500, slowEvery = 1, atMs = 0))
        judge.arm(100_000)
        assertEquals(0, judge.feed(500, slowEvery = 1, atMs = 50_000))
    }

    @Test
    fun `a full window of slow frames decides one step, then waits to settle`() {
        val judge = MotionJudge()
        judge.arm(0)
        assertFalse(judge.frame(slow, slow, interval, 0))
        assertEquals(1, judge.feed(MotionJudge.WINDOW - 1, slowEvery = 1, atMs = 1))
        assertEquals(1, judge.steps)
        // Within the settle time nothing more is decided, however slow.
        assertEquals(0, judge.feed(150, slowEvery = 1, atMs = 2_000))
        // After it, a new full window decides the next step.
        assertEquals(1, judge.feed(MotionJudge.WINDOW, slowEvery = 1, atMs = 10_000))
        assertEquals(2, judge.steps)
    }

    @Test
    fun `a quarter of slow frames is not enough`() {
        val judge = MotionJudge()
        judge.arm(0)
        assertEquals(0, judge.feed(1_000, slowEvery = 4, atMs = 0))
    }

    @Test
    fun `a third of slow frames is`() {
        val judge = MotionJudge()
        judge.arm(0)
        assertTrue(judge.feed(MotionJudge.WINDOW, slowEvery = 3, atMs = 0) == 1)
    }

    @Test
    fun `slow means more than one and a half display frames`() {
        val judge = MotionJudge()
        judge.arm(0)
        // 24 ms at 60 Hz is under 25 ms: on time.
        var decided = false
        repeat(MotionJudge.WINDOW * 2) { decided = decided || judge.frame(24_000_000L, 24_000_000L, interval, it.toLong()) }
        assertFalse(decided)
    }

    @Test
    fun `a late frame whose GPU part was quick is not counted`() {
        val judge = MotionJudge()
        judge.arm(0)
        // Late on the UI thread or in an upload, with the GPU part well under a display frame.
        var decided = false
        repeat(MotionJudge.WINDOW * 2) { decided = decided || judge.frame(60_000_000L, 5_000_000L, interval, it.toLong()) }
        assertFalse(decided)
    }

    @Test
    fun `old slow frames leave the window as new ones come in`() {
        val judge = MotionJudge()
        judge.arm(0)
        // A burst of slow frames, short of the share, then on-time frames: never a step.
        assertEquals(0, judge.feed(30, slowEvery = 1, atMs = 0))
        assertEquals(0, judge.feed(1_000, slowEvery = 0, atMs = 1_000))
    }
}
