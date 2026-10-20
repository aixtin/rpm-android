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
 * full strength as it is sung, not after. A held word (longer than its letters take to sing) is swept slowly over
 * the whole time it is held, so the light keeps moving through a carried note rather than stopping on it; at most
 * [HELD_GLYPH] a letter, so a gap in the timing does not crawl. (Pegasus lit a held last word whole; on the TV that
 * read as the sweep hanging on the word.)
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

    /** The slowest the light may cross a letter of a held word, in seconds. */
    const val HELD_GLYPH = 0.6

    /** The shortest a word is swept over, in seconds. A measured word can be a sliver at the end of a pause (an
     * aligner that placed a held "on" in its last 40 ms); swept over that it would sit grey through the pause and then
     * snap on. Such a word borrows time from the pause before it, so it eases in and still ends on its own moment. */
    const val SHORTEST_WORD = 0.5

    /** The slowest the light may cross a letter of a line's last word, in seconds. A line's last word is where a note
     * is held, and a measurement often catches only its tail; it is swept from where the word before it ended, through
     * the whole hold, at up to this much a letter. */
    const val LAST_GLYPH = 1.5

    /** How long a sung line takes to fade back to [REST] once its last word has been sung, in seconds. It fades slowly
     * and on an even curve, so the line lets go as the voice does, and is already at the strength the lines round it
     * have when the next line takes over, instead of dropping there in one frame. */
    const val LINE_FADE = 1.5

    /** How many letters the spans hold, which is the size [light] fills. */
    fun letters(spans: List<WordSpan>): Int = spans.sumOf { it.length }

    /** How far the light has arrived at letter [c] of [word] at [now], from 0 (not yet) to 1 (lit). [last] is
     * whether the word ends the line, the only place a word is taken as a held note. */
    fun through(word: WordSpan, c: Int, last: Boolean, now: Double, earliest: Double = word.from): Float {
        val of = word.length
        // A word too short to sweep starts earlier, into the pause before it, but never before [earliest] (where the
        // word before it ended).
        val shortest = maxOf(SHORTEST_WORD, of * SLOWEST_GLYPH)
        val from = when {
            // The last word reaches back through the pause before it, as far as its letters may be held.
            last -> maxOf(earliest, minOf(word.from, word.to - of * LAST_GLYPH))
            word.to - word.from >= shortest -> word.from
            else -> maxOf(earliest, minOf(word.from, word.to - shortest))
        }
        val held = word.to - from
        val share = held / maxOf(1, of)
        // A word sung in its letters' time keeps Pegasus's cap; a held one is swept over its hold.
        val step = if (share > SLOWEST_GLYPH) minOf(share, if (last) LAST_GLYPH else HELD_GLYPH) else share
        val opens = from + step * c
        val arriving = maxOf(GLYPH_SOFTNESS, step)
        return ((now - (opens - arriving)) / arriving).coerceIn(0.0, 1.0).toFloat()
    }

    /** The strength of every letter at [now], from [REST] to 1, written into [into] in reading order. It fills an
     * array the caller keeps, so a frame allocates nothing. */
    fun light(spans: List<WordSpan>, now: Double, into: FloatArray) {
        // After the last word: the whole line on its way back to rest.
        val after = if (spans.isEmpty()) 0.0 else now - spans.last().to
        val fade = if (after <= 0) 1f else {
            val t = (after / LINE_FADE).coerceIn(0.0, 1.0)
            (1 - t * t * (3 - 2 * t)).toFloat()
        }
        var k = 0
        for (w in spans.indices) {
            val word = spans[w]
            val last = w == spans.lastIndex
            // The first word may not reach back before the line's own first moment.
            val earliest = if (w == 0) word.from else spans[w - 1].to
            for (c in 0 until word.length) {
                if (k >= into.size) return
                into[k++] = REST + (1 - REST) * through(word, c, last, now, earliest) * fade
            }
        }
    }
}
