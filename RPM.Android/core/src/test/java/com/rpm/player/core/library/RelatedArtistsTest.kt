package com.rpm.player.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatedArtistsTest {

    private val NO_ALBUMS = emptyMap<String, CloudAlbum>()

    private fun act(name: String, albums: List<String>, together: List<String> = emptyList(), appearances: List<String> = emptyList(), tracks: Int = 10) =
        ArtistWallItem(name, albums, together, appearances, tracks)

    private val genres = listOf(
        GenreWallItem("Rock", listOf("r1", "r2", "r3", "r4", "r5")),
        GenreWallItem("Jazz", listOf("j1", "j2", "j3")),
        GenreWallItem(GenreWall.UNCLASSIFIED, listOf("u1", "u2")),
    )

    @Test
    fun aRecordMadeTogetherCountsBeforeAGenreInCommon() {
        val me = act("Me", listOf("r1"), together = listOf("j3"))
        val partner = act("Partner", listOf("j2"), together = listOf("j3"))
        val rocker = act("Rocker", listOf("r2", "r3"))
        val related = RelatedArtists.of(me, Walls(listOf(me, partner, rocker), genres), NO_ALBUMS, emptyList())
        assertEquals(listOf("Partner", "Rocker"), related.map { it.name })
    }

    @Test
    fun anActOfOtherGenresAndNoRecordInCommonIsNotRelated() {
        val me = act("Me", listOf("r1", "r2"))
        val jazz = act("Jazz act", listOf("j1"))
        assertTrue(RelatedArtists.of(me, Walls(listOf(me, jazz), genres), NO_ALBUMS, emptyList()).isEmpty())
    }

    @Test
    fun theActItselfAndVariousArtistsAreNeverRelated() {
        val me = act("Me", listOf("r1"))
        val various = act("Various Artists", listOf("r2"))
        val again = act("me", listOf("r3"))
        assertTrue(RelatedArtists.of(me, Walls(listOf(me, various, again), genres), NO_ALBUMS, emptyList()).isEmpty())
    }

    @Test
    fun unshelvedRecordsDoNotMakeActsAlike() {
        val me = act("Me", listOf("u1"))
        val other = act("Other", listOf("u2"))
        assertTrue(RelatedArtists.of(me, Walls(listOf(me, other), genres), NO_ALBUMS, emptyList()).isEmpty())
    }

    @Test
    fun tiesGoToTheActWithMoreSongs() {
        val me = act("Me", listOf("r1"))
        val small = act("Small", listOf("r2"), tracks = 3)
        val big = act("Big", listOf("r3"), tracks = 40)
        assertEquals(listOf("Big", "Small"), RelatedArtists.of(me, Walls(listOf(me, small, big), genres), NO_ALBUMS, emptyList()).map { it.name })
    }

    @Test
    fun unshelvedRecordsWithTheSameTagAreAlike() {
        fun record(key: String, genre: String?) = CloudAlbum(key, key, "x", listOf(com.rpm.player.core.cloud.CloudTrack(rpmId = key + "1", tags = com.rpm.player.core.cloud.CloudTrackTags(genre = genre))))
        val albums = listOf(record("u1", "Singer-Songwriter"), record("u2", " singer-songwriter "), record("r5", "Rock")).associateBy { it.key }
        val me = act("Me", listOf("u1"))
        val alike = act("Alike", listOf("u2"))
        val rocker = act("Rocker", listOf("r5"))
        assertEquals(listOf("Alike"), RelatedArtists.of(me, Walls(listOf(me, alike, rocker), genres), albums, emptyList()).map { it.name })
    }

    @Test
    fun actsOnTheSameSmallListsAreRelated() {
        fun record(key: String) = CloudAlbum(key, key, "x", listOf(com.rpm.player.core.cloud.CloudTrack(rpmId = key + "1")))
        val albums = listOf(record("u1"), record("u2"), record("j1")).associateBy { it.key }
        fun list(id: String, vararg keys: String) = TrackList(id = id, storeId = id, name = ListName.Text(id), tracks = keys.map { albums.getValue(it).tracks.first() }, missing = 0, folder = emptyList(), generated = true, kind = null, plainMood = true, genre = null, decade = 0, blurb = null)
        val me = act("Me", listOf("u1"))
        val company = act("Company", listOf("u2"))
        val stranger = act("Stranger", listOf("j1"))
        val related = RelatedArtists.of(me, Walls(listOf(me, company, stranger), genres), albums, listOf(list("chill", "u1", "u2"), list("all", "j1", "u2")))
        assertEquals(listOf("Company"), related.map { it.name })
    }

    @Test
    fun atMostTwelve() {
        val me = act("Me", listOf("r1"))
        val many = (1..20).map { act("Act $it", listOf("r2")) }
        assertEquals(RelatedArtists.MOST, RelatedArtists.of(me, Walls(listOf(me) + many, genres), NO_ALBUMS, emptyList()).size)
    }
}
