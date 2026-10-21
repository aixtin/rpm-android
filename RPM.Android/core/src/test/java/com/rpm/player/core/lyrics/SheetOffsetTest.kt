package com.rpm.player.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SheetOffsetTest {

    private fun sheet(vararg lines: Pair<Double, String>) = LyricSheet(lines.map { LyricLine(it.first, it.second) })

    private val ours = sheet(1.0 to "first line", 10.0 to "second line", 20.0 to "third line")

    private fun answer(offset: Double?, vararg on: Pair<Double, String>) =
        MeasuredAnswer(offset = offset, offsetLines = on.mapIndexed { i, (s, t) -> SheetLine(i, s, t) })

    @Test
    fun anOffsetWorkedOutForThisSheetIsTaken() {
        assertEquals(2.4, MeasuredLyrics.offsetFor(answer(2.4, 1.0 to "first line", 10.0 to "second line"), ours), 0.0)
    }

    @Test
    fun anOffsetWorkedOutForAnotherSheetIsNot() {
        assertEquals(0.0, MeasuredLyrics.offsetFor(answer(2.4, 1.0 to "first line", 11.0 to "second line"), ours), 0.0)
        assertEquals(0.0, MeasuredLyrics.offsetFor(answer(2.4, 1.0 to "other words"), ours), 0.0)
    }

    @Test
    fun noOffsetIsNoMove() {
        assertEquals(0.0, MeasuredLyrics.offsetFor(answer(null), ours), 0.0)
        assertEquals(0.0, MeasuredLyrics.offsetFor(answer(3.0), ours), 0.0)
    }

    @Test
    fun aShiftedSheetStartsNoLineBeforeTheRecord() {
        val moved = MeasuredLyrics.shifted(ours, -3.0)
        assertEquals(listOf(0.0, 7.0, 17.0), moved.lines.map { it.seconds })
    }

    @Test
    fun measuredLinesMovedWithTheSheetStillMatchItAndKeepTheirWordTimes() {
        val measured = listOf(MeasuredLine(1, 10.0, "second line", listOf(MeasuredWord(12.5, 12.9), MeasuredWord(13.0, 13.6))))
        val sheet = MeasuredLyrics.shifted(ours, 2.4)
        val words = MeasuredLyrics.forLine(MeasuredLyrics.shifted(measured, 2.4), "second line", sheet.lines[1].seconds)
        assertNotNull(words)
        assertEquals(12.5, words!![0].from, 0.0)
        assertEquals(13.6, words[1].to, 0.0)
    }
}
