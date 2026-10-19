package com.rpm.player.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricScoreTest {

    private fun sheet(vararg lines: Pair<Double, String>) = LyricSheet(lines.map { LyricLine(it.first, it.second) })

    @Test
    fun aLongWaitIsARestThatClosesTheStretchBeforeIt() {
        val score = LyricScore.of(sheet(10.0 to "one", 12.0 to "two", 60.0 to "three"))
        val pieces = score.parts.map { it.piece }
        assertEquals(LyricScore.Piece.Rest(12.0 + LyricScore.SUNG, 60.0), pieces[2])
        assertEquals(0, score.parts[2].section)
        assertEquals(1, score.parts[3].section)
    }

    @Test
    fun aWordlessLineIsARest() {
        val score = LyricScore.of(sheet(5.0 to "one", 9.0 to "♪", 30.0 to "two"))
        assertEquals(LyricScore.Piece.Rest(9.0, 30.0), score.parts[1].piece)
        assertTrue(LyricScore.isWordless(" ♫ 🎵 "))
        assertTrue(!LyricScore.isWordless("♯ minor"))
    }

    @Test
    fun beforeTheFirstLineThereIsNoPiece() {
        val score = LyricScore.of(sheet(10.0 to "one"))
        assertNull(score.index(3.0))
        assertEquals(0, score.index(10.5))
    }

    @Test
    fun theSweepSharesALineByLengthAndEndsOnTheNextLine() {
        val spans = LyricTiming.sweep("aa bbbb", 10.0, 10.6)
        assertEquals(2, spans.size)
        assertEquals(10.0, spans[0].from, 1e-9)
        assertEquals(10.2, spans[0].to, 1e-9)
        assertEquals(10.6, spans[1].to, 1e-9)
    }

    @Test
    fun theSweepIsNeverSlowerThanItsCap() {
        val spans = LyricTiming.sweep("seven!!", 0.0, 60.0)
        assertEquals(7 / LyricTiming.SLOWEST_CHARS, spans.last().to, 1e-9)
    }
}

class MeasuredLyricsTest {
    private val measured = listOf(MeasuredLine(3, 12.5, "hello there you", listOf(MeasuredWord(12.6, 12.9), MeasuredWord(13.4, 13.8), MeasuredWord(14.0, 14.2))))

    @Test
    fun theSameLineTakesTheMeasuredMoments() {
        val spans = MeasuredLyrics.forLine(measured, "hello there you", 12.5)!!
        org.junit.Assert.assertEquals(listOf(12.6, 13.4, 14.0), spans.map { it.from })
        org.junit.Assert.assertEquals(listOf(0, 6, 12), spans.map { it.start })
    }

    @Test
    fun anotherSheetsLineIsNotUsed() {
        org.junit.Assert.assertNull(MeasuredLyrics.forLine(measured, "hello there", 12.5))
        org.junit.Assert.assertNull(MeasuredLyrics.forLine(measured, "hello there you", 14.0))
    }
}
