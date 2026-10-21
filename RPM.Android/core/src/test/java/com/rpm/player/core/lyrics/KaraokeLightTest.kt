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
    fun aHeldLastWordCaughtOnlyAtItsTailIsSweptThroughTheHold() {
        // "it" ends at 85.5, then "on" measured as 89.28-89.32: the light goes through "on" over the hold, not in 40 ms.
        val spans = listOf(WordSpan(0, 2, 85.0, 85.5), WordSpan(3, 2, 89.28, 89.32))
        val into = FloatArray(4)
        KaraokeLight.light(spans, 86.3, into)
        assertTrue(into[2] > KaraokeLight.REST && into[2] < 1f)
        // The second letter is on its way, a second and a half behind the first.
        KaraokeLight.light(spans, 87.0, into)
        assertTrue(into[3] > KaraokeLight.REST && into[3] < 1f)
        KaraokeLight.light(spans, 89.32, into)
        assertEquals(1f, into[3], 1e-6f)
    }

    @Test
    fun aSliverOfAWordAfterAPauseEasesInInsteadOfSnapping() {
        // Mid-line: "up" measured as 10.96-11.0 after a pause; swept over half a second before its end, not 40 ms.
        val spans = listOf(WordSpan(0, 2, 9.0, 9.5), WordSpan(3, 2, 10.96, 11.0), WordSpan(6, 3, 11.2, 11.6))
        val into = FloatArray(7)
        KaraokeLight.light(spans, 10.2, into)
        assertEquals(KaraokeLight.REST, into[2], 1e-6f)
        KaraokeLight.light(spans, 10.6, into)
        assertTrue(into[2] > KaraokeLight.REST && into[3] < 1f)
        KaraokeLight.light(spans, 11.0, into)
        assertEquals(1f, into[3], 1e-6f)
    }

    @Test
    fun theLineFadesBackToRestSlowlyAfterItsLastWord() {
        val spans = listOf(WordSpan(0, 2, 0.0, 0.5))
        val into = FloatArray(2)
        KaraokeLight.light(spans, 0.5, into)
        assertEquals(1f, into[1], 1e-6f)
        KaraokeLight.light(spans, 0.5 + KaraokeLight.LINE_FADE / 2, into)
        assertEquals(KaraokeLight.REST + (1 - KaraokeLight.REST) * 0.5f, into[1], 1e-6f)
        KaraokeLight.light(spans, 0.5 + KaraokeLight.LINE_FADE, into)
        val held = FloatArray(into.size)
        KaraokeLight.light(spans, 0.5 + KaraokeLight.LINE_FADE * 3, held, fades = false)
        assertTrue("a line still being sung stays lit after its last word", held.all { it == 1f })
        assertEquals(KaraokeLight.REST, into[1], 1e-6f)
    }

    @Test
    fun aHeldWordIsSweptSlowlyOverItsHold() {
        // Two letters held for a second: half a second a letter, so the second letter is lit at 20.5 s, not at once.
        val word = WordSpan(0, 2, 20.0, 21.0)
        assertEquals(1f, KaraokeLight.through(word, 0, last = true, now = 20.0), 1e-6f)
        assertEquals(0f, KaraokeLight.through(word, 1, last = true, now = 20.0), 1e-6f)
        assertEquals(0.5f, KaraokeLight.through(word, 1, last = true, now = 20.25), 1e-6f)
        assertEquals(1f, KaraokeLight.through(word, 1, last = true, now = 20.5), 1e-6f)
    }

    @Test
    fun aStretchedWordIsCrossedNoSlowerThanTheHeldRate() {
        // Three letters measured over ten seconds: the aligner lost the thread. The last letter is lit by 2 x 0.6 s.
        val word = WordSpan(0, 3, 0.0, 10.0)
        assertEquals(1f, KaraokeLight.through(word, 2, last = false, now = 2 * KaraokeLight.HELD_GLYPH), 1e-6f)
    }

    @Test
    fun lightFillsEveryLetterFromRestToOne() {
        val spans = LyricTiming.sweep("so far away", 0.0, 2.0)
        val into = FloatArray(KaraokeLight.letters(spans))
        assertEquals(9, into.size)
        KaraokeLight.light(spans, -1.0, into)
        into.forEach { assertEquals(KaraokeLight.REST, it, 1e-6f) }
        // Every letter lit as the last word ends, and back at rest once the line has faded after it.
        KaraokeLight.light(spans, spans.last().to, into)
        into.forEach { assertEquals(1f, it, 1e-6f) }
        KaraokeLight.light(spans, 10.0, into)
        into.forEach { assertEquals(KaraokeLight.REST, it, 1e-6f) }
    }
}
