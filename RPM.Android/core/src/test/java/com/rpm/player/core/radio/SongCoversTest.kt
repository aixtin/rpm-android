package com.rpm.player.core.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SongCoversTest {
    @Test fun twoSpellingsOfOneAnnouncementShareAKey() {
        assertEquals(SongCovers.keyOf("Beyoncé", "Halo"), SongCovers.keyOf("  BEYONCE ", "halo"))
        assertEquals(SongCovers.keyOf("Daft Punk", "One  More Time"), SongCovers.keyOf("daft punk", "One More Time"))
    }

    @Test fun theArtistAndTitleAreKeptApart() {
        assertNotEquals(SongCovers.keyOf("A B", "C"), SongCovers.keyOf("A", "B C"))
    }
}
