package com.rpm.player.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AlbumArtKeyTest {

    // The two values the iOS app pins (AlbumArtKeyTests.swift), which are what the desktop writes.
    @Test fun theCoverKeyIsWhatTheDesktopWrites() {
        assertEquals("d90d382db82e7ba38c335b59ae3eaf33", AlbumArtKey.of(RecordPicture.COVER, "Manu Chao", "Clandestino"))
    }

    @Test fun theDiscKeyIsWhatTheDesktopWrites() {
        assertEquals("ee1067d65d84ffa7e4c8b433b457284d", AlbumArtKey.of(RecordPicture.DISC, "Manu Chao", "Clandestino"))
    }

    @Test fun caseAndSurroundingSpaceDoNotChangeTheKey() {
        val canonical = AlbumArtKey.of(RecordPicture.COVER, "manu chao", "clandestino")
        assertEquals(canonical, AlbumArtKey.of(RecordPicture.COVER, "  Manu Chao  ", "Clandestino "))
        assertEquals(canonical, AlbumArtKey.of(RecordPicture.COVER, "MANU CHAO", "CLANDESTINO"))
    }

    @Test fun aBlankAlbumArtistIsNotAnAlbumArtist() {
        assertEquals(
            AlbumArtKey.of(RecordPicture.COVER, "Someone", "Record"),
            AlbumArtKey.forTrack(RecordPicture.COVER, "   ", "Someone", "Record"),
        )
    }

    @Test fun aCompilationIsAskedAboutUnderBothNamesAlbumArtistFirst() {
        val keys = AlbumArtKey.spellings(RecordPicture.COVER, "Various Artists", "Snap!", "Now 22")
        assertEquals(
            listOf(
                AlbumArtKey.of(RecordPicture.COVER, "Various Artists", "Now 22"),
                AlbumArtKey.of(RecordPicture.COVER, "Snap!", "Now 22"),
            ),
            keys,
        )
    }

    @Test fun aDiscLabelIsNotFiledWhereTheCoverIs() {
        assertNotEquals(
            AlbumArtKey.of(RecordPicture.COVER, "Manu Chao", "Clandestino"),
            AlbumArtKey.of(RecordPicture.DISC, "Manu Chao", "Clandestino"),
        )
    }
}
