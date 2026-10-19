package com.rpm.player.core.library

import com.rpm.player.core.cloud.CloudTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchTest {
    private val iv = CloudAlbum(
        "toto|iv", "Toto IV", "Toto",
        listOf(CloudTrack(rpmId = "1", title = "Africa", artist = "Toto"), CloudTrack(rpmId = "2", title = "Rosanna", artist = "Toto")),
    )
    private val clandestino = CloudAlbum(
        "manu|clandestino", "Clandestino", "Manu Chao",
        listOf(CloudTrack(rpmId = "3", title = "Desaparecido", artist = "Manu Chao"), CloudTrack(rpmId = "4", title = "Bongo Bong", artist = "Manu Chao")),
    )
    private val afrika = CloudAlbum(
        "various|afrika", "Afrika Shox", "Leftfield",
        listOf(CloudTrack(rpmId = "5", title = "Afrika Shox", artist = "Leftfield & Afrika Bambaataa")),
    )
    private val artists = listOf(
        ArtistWallItem("Leftfield", listOf(afrika.key), emptyList(), emptyList(), 1),
        ArtistWallItem("Manu Chao", listOf(clandestino.key), emptyList(), emptyList(), 2),
        ArtistWallItem("Toto", listOf(iv.key), emptyList(), emptyList(), 2),
    )
    private val search = LibrarySearch.of(listOf(afrika, clandestino, iv), artists)

    @Test fun eachKindIsSearched() {
        val hits = search.find("toto")
        assertEquals(listOf("Toto"), hits.artists.map { it.name })
        assertEquals(listOf(iv), hits.albums)
        assertEquals(listOf("Africa", "Rosanna"), hits.tracks.map { it.track.title })
    }

    /** Case, accents and punctuation are folded on both sides. */
    @Test fun caseAndAccentsAreIgnored() {
        assertEquals(listOf("Manu Chao"), search.find("MANÚ").artists.map { it.name })
    }

    /** Every word must be found, in any order. */
    @Test fun everyWordMustMatch() {
        assertEquals(listOf("Bongo Bong"), search.find("chao bong").tracks.map { it.track.title })
        assertTrue(search.find("toto bong").isEmpty)
    }

    /** A name that starts with the query comes before one that only holds it, though the collection lists
     * Desaparecido before Africa. */
    @Test fun aNameStartingWithTheQueryComesFirst() {
        assertEquals(listOf("Africa"), search.find("afric").tracks.map { it.track.title })
        assertEquals(listOf("Afrika Shox", "Africa", "Desaparecido"), search.find("a").tracks.map { it.track.title }.take(3))
    }

    @Test fun anEmptyQueryFindsNothing() {
        assertTrue(search.find("  ").isEmpty)
    }
}
