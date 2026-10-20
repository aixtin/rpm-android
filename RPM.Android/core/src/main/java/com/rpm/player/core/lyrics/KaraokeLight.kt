package com.rpm.player.core.lyrics

/**
 * How lit each letter of the sung line is, for karaoke. It is the desktop Pegasus sweep (`TickWords` and the
 * constants above it in RPM.Desktop/Presentation/Pegasus/PegasusChrome.xaml.cs), so a line lights the same on the
 * TV as on the PC.
 *
 * The light moves THROUGH a word, letter by letter, rather than switching the word on: a word lit as one thing
 * pops, a word lit a letter at a time reads as a wipe passing over it. A word's moment is shared evenly between
 * its letters, but never slower than [SLOWEST_GLYPH] a letter, because a measured word can carry a gap where the
 * aligner lost the thread, and a sweep crawling through it reads as the sheet stalling. Each letter arrives over
 * [GLYPH_SOFTNESS] or its own share, whichever is longer, so three or four letters are always part-way lit and the
 * eye sees one soft edge, not a row of lights. The arrival FINISHES on the letter's moment, so the letter is at
 * full strength as it is sung, not after. A held last word (longer than its letters take to sing) is a carried
 * note, not a sweep: it comes up whole over [HELD_ARRIVAL] and stays lit while the note is held.
 *
 * Letters are counted in reading order over the spans, the gaps between words excluded ([letters]).
 */
object KaraokeLight {
    /** How strong a letter is before the light reaches it, on the sung line. Fainter than another line, so the
     * eye can see where the voice has got to, but readable, so a reader running ahead can see where it goes. */
    const val REST = 0.42f

    /** The shortest time the light takes to arrive at one letter, in seconds: the softness of the edge. */
    const val GLYPH_SOFTNESS = 0.20

    /** The slowest the light may cross a letter, in seconds: the same seven a second [LyricTiming] caps a line at. */
    const val SLOWEST_GLYPH = 1.0 / 7.0

    /** How quickly a held last word comes up, whole, in seconds. */
    const val HELD_ARRIVAL = 0.22

    /** How many letters the spans hold, which is the size [light] fills. */
    fun letters(spans: List<WordSpan>): Int = spans.sumOf { it.length }

    /** How far the light has arrived at letter [c] of [word] at [now], from 0 (not yet) to 1 (lit). [last] is
     * whether the word ends the line, the only place a word is taken as a held note. */
    fun through(word: WordSpan, c: Int, last: Boolean, now: Double): Float {
        val of = word.length
        val held = word.to - word.from
        val carried = last && held > of * SLOWEST_GLYPH
        val step = if (carried) 0.0 else minOf(held / maxOf(1, of), SLOWEST_GLYPH)
        val opens = word.from + step * c
        val arriving = if (carried) HELD_ARRIVAL else maxOf(GLYPH_SOFTNESS, step)
        return ((now - (opens - arriving)) / arriving).coerceIn(0.0, 1.0).toFloat()
    }

    /** The strength of every letter at [now], from [REST] to 1, written into [into] in reading order. It fills an
     * array the caller keeps, so a frame allocates nothing. */
    fun light(spans: List<WordSpan>, now: Double, into: FloatArray) {
        var k = 0
        for (w in spans.indices) {
            val word = spans[w]
            val last = w == spans.lastIndex
            for (c in 0 until word.length) {
                if (k >= into.size) return
                into[k++] = REST + (1 - REST) * through(word, c, last, now)
            }
        }
    }
}
