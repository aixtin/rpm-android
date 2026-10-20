package com.rpm.player.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokeLightTest {

    @Test
    fun aLetterIsFullyLitOnItsMomentAndUnlitASoftnessBefore() {
        // Two words so the first is not the held last word; 4 letters over 0.4 s is 0.1 s a letter.
        val word = WordSpan(0, 4, 10.0, 10.4)
        assertEquals(1f, KaraokeLight.through(word, 0, last = false, now = 10.0), 1e-6f)
        assertEquals(0f, KaraokeLight.through(word, 0, last = false, now = 10.0 - KaraokeLight.GLYPH_SOFTNESS), 1e-6f)
        assertEquals(0.5f, KaraokeLight.through(word, 0, last = false, now = 10.0 - KaraokeLight.GLYPH_SOFTNESS / 2), 1e-6f)
        // The third letter opens two shares in, and arrives over the softness, which is wider than its share.
        assertEquals(1f, KaraokeLight.through(word, 2, last = false, now = 10.2), 1e-6f)
        assertEquals(0.5f, KaraokeLight.through(word, 2, last = false, now = 10.1), 1e-6f)
    }

    @Test
    fun theLightMovesThroughAWordSoSeveralLettersArePartLit() {
        val word = WordSpan(0, 6, 5.0, 5.3)
        val at = (0 until 6).map { KaraokeLight.through(word, it, last = false, now = 5.1) }
        assertTrue(at.zipWithNext().all { (a, b) -> a >= b })
        assertTrue(at.count { it > 0f && it < 1f } >= 3)
    }

    @Test
    fun aStretchedWordIsCrossedAtASingingRateAndThenWaits() {
        // Three letters measured over two seconds: the aligner lost the thread. The last letter is lit by 3/7 s.
        val word = WordSpan(0, 3, 0.0, 2.0)
        assertEquals(1f, KaraokeLight.through(word, 2, last = false, now = 2 * KaraokeLight.SLOWEST_GLYPH), 1e-6f)
    }

    @Test
    fun aHeldLastWordComesUpWhole() {
        val word = WordSpan(0, 4, 20.0, 23.0)
        val letters = (0 until 4).map { KaraokeLight.through(word, it, last = true, now = 20.0 - KaraokeLight.HELD_ARRIVAL / 2) }
        letters.forEach { assertEquals(0.5f, it, 1e-6f) }
        assertEquals(1f, KaraokeLight.through(word, 3, last = true, now = 20.0), 1e-6f)
    }

    @Test
    fun lightFillsEveryLetterFromRestToOne() {
        val spans = LyricTiming.sweep("so far away", 0.0, 2.0)
        val into = FloatArray(KaraokeLight.letters(spans))
        assertEquals(9, into.size)
        KaraokeLight.light(spans, -1.0, into)
        into.forEach { assertEquals(KaraokeLight.REST, it, 1e-6f) }
        KaraokeLight.light(spans, 10.0, into)
        into.forEach { assertEquals(1f, it, 1e-6f) }
    }
}
