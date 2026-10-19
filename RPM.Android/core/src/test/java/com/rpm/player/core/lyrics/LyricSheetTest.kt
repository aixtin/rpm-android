package com.rpm.player.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LyricSheetTest {

    @Test fun linesAreReadWithTheirTimesAndSorted() {
        val sheet = LyricSheet.parse(
            """
            [ar:Someone]
            [00:12.50]Second
            [00:01.00]First
            [01:02.123]Third
            """.trimIndent(),
        )
        assertEquals(listOf("First", "Second", "Third"), sheet.lines.map { it.text })
        assertEquals(1.0, sheet.lines[0].seconds, 1e-9)
        assertEquals(12.5, sheet.lines[1].seconds, 1e-9)
        assertEquals(62.123, sheet.lines[2].seconds, 1e-9)
    }

    @Test fun oneLineWithTwoStampsIsSungTwice() {
        val sheet = LyricSheet.parse("[00:10.00][00:30.00]Chorus")
        assertEquals(listOf(10.0, 30.0), sheet.lines.map { it.seconds })
    }

    @Test fun anOffsetMovesEveryLineEarlier() {
        val sheet = LyricSheet.parse("[offset:+500]\n[00:10.00]Line")
        assertEquals(9.5, sheet.lines.single().seconds, 1e-9)
    }

    @Test fun wordStampsAreTakenOutOfTheText() {
        val sheet = LyricSheet.parse("[00:01.00]<00:01.00>One <00:01.50>two")
        assertEquals("One two", sheet.lines.single().text)
    }

    @Test fun theLineBeingSungIsTheLastThatHasStarted() {
        val sheet = LyricSheet.parse("[00:01.00]A\n[00:05.00]B\n[00:09.00]C")
        assertNull(sheet.index(0.5))
        assertEquals(0, sheet.index(1.0))
        assertEquals(1, sheet.index(8.99))
        assertEquals(2, sheet.index(100.0))
    }

    @Test fun plainTextIsNotASyncedSheet() {
        assertFalse(LyricSheet.parse("Just words\nwith no times").isSynced)
    }
}
