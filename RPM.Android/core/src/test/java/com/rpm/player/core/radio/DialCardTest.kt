package com.rpm.player.core.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The iOS app's DialCardTests. */
class DialCardTest {
    private fun signs(count: Int, caption: String = "ABC") = List(count) { caption }

    @Test fun aShortListFitsTheGlass() = assertEquals(350.0, DialCard.of(signs(6), 350.0).scaleWidth, 0.0)

    @Test fun aCrowdedListMakesTheCardLonger() = assertTrue(DialCard.of(signs(120), 350.0).scaleWidth > 350)

    @Test fun theCardIsNeverLongerThanSixGlasses() = assertTrue(DialCard.of(signs(4000, "WWWW"), 350.0).scaleWidth <= 350 * 6)

    @Test fun noTwoSignsOnOneRowOverlap() {
        val card = DialCard.of(signs(40, "KEXP"), 390.0)
        for (row in 0 until maxOf(card.nameRows, 1)) {
            card.printed.filter { it.row == row }.zipWithNext { a, b -> assertTrue(b.nameLeft >= a.markX + DialCard.SQUARE_SIZE / 2) }
        }
    }

    @Test fun theSignIsSetHardAgainstItsSquare() {
        val first = DialCard.of(signs(8), 350.0).printed[0]
        assertEquals(first.markX - DialCard.SQUARE_SIZE / 2 - DialCard.SQUARE_GAP, first.nameLeft + first.width, 1e-9)
    }

    @Test fun aSignThatClearsNoRowIsLeftOff() {
        val card = DialCard.of(signs(300, "WWWW"), 320.0)
        assertTrue(card.printed.size < 300)
        assertEquals(300, card.ticks.size)
    }

    @Test fun aCardTooCrowdedForGraduationsPrintsNoSignsEither() {
        val card = DialCard.of(signs(5000, "W"), 320.0)
        assertTrue(card.printed.isEmpty())
        assertTrue(card.ticks.size >= 8)
    }

    @Test fun anEmptyDialPrintsNothingAndDoesNotDivideByZero() {
        val card = DialCard.of(emptyList(), 350.0)
        assertTrue(card.printed.isEmpty())
        assertNull(card.nearest(100.0, 0))
    }

    @Test fun aPointOnTheCardDetentsOntoTheNearestStation() {
        val card = DialCard.of(signs(10), 350.0)
        for (station in 0 until 10) assertEquals(station, card.nearest(card.markOf(station, 10), 10))
    }

    @Test fun aPointPastEitherEndDetentsOntoTheEndStation() {
        val card = DialCard.of(signs(10), 350.0)
        assertEquals(0, card.nearest(-900.0, 10))
        assertEquals(9, card.nearest(90_000.0, 10))
    }

    @Test fun theCardCannotBeSlidPastItsOwnEnds() {
        val card = DialCard.of(signs(120), 350.0)
        assertEquals(0.0, card.slide(0.0, 300.0, 350.0), 0.0)
        assertEquals(-(card.scaleWidth - 350), card.slide(card.scaleWidth, 0.0, 350.0), 1e-9)
    }

    @Test fun aCardNoLongerThanTheGlassDoesNotSlideAtAll() =
        assertEquals(0.0, DialCard.of(signs(4), 350.0).slide(100.0, 300.0, 350.0), 0.0)

    @Test fun theInstrumentIsCentredInTheRoomBelowTheReadout() {
        val card = DialCard.of(signs(6), 350.0, height = 176.0, readoutBottom = 54.0)
        assertTrue(card.namesTop > 54)
        assertTrue(card.scaleBottom <= 176)
        assertEquals(DialCard.SCALE_SPAN, card.scaleBottom - card.scaleTop, 1e-9)
    }

    @Test fun moreRowsOfSignsPushTheGraduationsDown() {
        val few = DialCard.of(signs(6), 350.0)
        val many = DialCard.of(signs(60), 350.0)
        assertTrue(many.nameRows > few.nameRows)
        assertTrue(many.scaleTop > few.scaleTop)
    }

    @Test fun theFirstSignIsOnTheCard() {
        for (captions in listOf(signs(8, "WWWW"), signs(80, "KEXP"), signs(3, "A"))) {
            for (mark in DialCard.of(captions, 390.0).printed) assertTrue(mark.nameLeft >= 0)
        }
    }
}
