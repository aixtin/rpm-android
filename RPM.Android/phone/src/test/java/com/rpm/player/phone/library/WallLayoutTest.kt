package com.rpm.player.phone.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallLayoutTest {

    /** An iPhone 16 is 393 points wide. The wall opens with two columns at the default stride of 194. */
    @Test fun anIphoneWidthOpensOnTwoColumns() {
        val (columns, art) = WallLayout.wall(393.0, 194.0, edge = 20.0, gutter = 24.0, maxColumns = 4)
        assertEquals(2, columns)
        assertEquals(164.0, art, 0.0)
    }

    /**
     * The Titan's square screen is 576 points wide. 560 points of room is closer to three strides than to two, so it
     * opens with three columns.
     */
    @Test fun theTitanOpensOnThreeColumns() {
        val (columns, art) = WallLayout.wall(576.0, 194.0, edge = 20.0, gutter = 24.0, maxColumns = 4)
        assertEquals(3, columns)
        assertEquals(162.0, art, 0.0)
    }

    /** A row of covers and their gaps never run past the margins. */
    @Test fun aRowFitsInsideItsMargins() {
        for (width in listOf(320.0, 393.0, 430.0, 576.0, 800.0)) {
            for (target in listOf(60.0, 120.0, 194.0, 400.0, 900.0)) {
                val (columns, art) = WallLayout.wall(width, target, edge = 20.0, gutter = 24.0, maxColumns = 4)
                assertTrue("$width at $target", columns * art + (columns - 1) * 24 <= width - 40 + 0.001)
            }
        }
    }

    @Test fun theColumnCountIsClampedBetweenOneAndFour() {
        assertEquals(4, WallLayout.wall(576.0, 20.0, 20.0, 24.0, maxColumns = 4).first)
        assertEquals(1, WallLayout.wall(576.0, 5000.0, 20.0, 24.0, maxColumns = 4).first)
    }

    /** At the anchor edge, each figure is the one that is drawn. */
    @Test fun theCaptionAtTheAnchorIsTheDrawnOne() {
        val caption = AlbumCaption.of(170.0)
        assertEquals(15f, caption.album)
        assertEquals(14f, caption.artist)
        assertEquals(20.0, caption.albumLine, 0.0)
        assertEquals(19.0, caption.artistLine, 0.0)
        assertEquals(170.0 + 10 + 20 + 2 + 19 + 10, caption.card, 0.0)
    }

    @Test fun theCaptionNeverGoesBelowElevenOrAboveTwenty() {
        assertEquals(11f, AlbumCaption.of(10.0).album)
        assertEquals(20f, AlbumCaption.of(5000.0).album)
    }
}
