package com.rpm.player.core.library

import com.rpm.player.core.cloud.CloudTrack
import com.rpm.player.core.cloud.CloudTrackTags
import com.rpm.player.core.papers.TrackPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The iOS app's ArtistPopularTests. */
class ArtistPopularTest {

    private fun track(id: String, title: String, disc: Int = 1, at: Int) =
        CloudTrack(rpmId = id, title = title, artist = "Adele", tags = CloudTrackTags(discNumber = disc, trackNumber = at))

    private fun record(key: String, songs: Int) =
        CloudAlbum(key = key, album = key, artist = "Adele", tracks = (1..songs).map { track("$key#$it", "$key $it", at = it) })

    private fun songs(albums: List<CloudAlbum>) = albums.flatMap { album -> album.tracks.map { it to album } }

    private fun listens(vararg values: Long) = values.withIndex().associate { TrackPlace(1, it.index + 1) to it.value }

    @Test fun theMostPlayedComeFirst() {
        val albums = listOf(record("21", 6), record("25", 6))
        val ranked = ArtistPopular.ranked(ArtistPopular.MOST, songs(albums), mapOf("21" to listens(10, 900, 30, 0, 5, 1), "25" to listens(800, 20, 0, 0, 0, 2)))
        assertEquals(listOf("21 2", "25 1", "21 3", "25 2", "21 1", "21 5"), ranked.map { it.first.title })
    }

    @Test fun atMostHalfOfTheirSongs() =
        assertEquals(6, ArtistPopular.ranked(ArtistPopular.MOST, songs(listOf(record("21", 12))), mapOf("21" to listens(12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1))).size)

    @Test fun atMostTwelve() {
        val many = listens(*LongArray(20) { 20L - it })
        assertEquals(ArtistPopular.MOST, ArtistPopular.ranked(ArtistPopular.MOST, songs(listOf(record("21", 20), record("25", 20))), mapOf("21" to many, "25" to many)).size)
    }

    @Test fun tooFewIsNone() =
        assertTrue(ArtistPopular.ranked(ArtistPopular.MOST, songs(listOf(record("21", 10))), mapOf("21" to listens(5, 4, 3))).isEmpty())

    @Test fun aShortListingHasNoSection() =
        assertTrue(ArtistPopular.ranked(ArtistPopular.MOST, songs(listOf(record("21", 6))), mapOf("21" to listens(6, 5, 4, 3, 2, 1))).isEmpty())

    @Test fun oneSongPerTitle() {
        val studio = CloudAlbum("21", "21", "Adele", listOf(
            track("s1", "Rolling in the Deep", at = 1), track("s2", "Rumour Has It", at = 2),
            track("s3", "Turning Tables", at = 3), track("s4", "Set Fire to the Rain", at = 4),
        ))
        val live = CloudAlbum("live", "Live", "Adele", listOf(
            track("l1", "Rolling in the Deep (Live)", at = 1), track("l2", "Someone Like You", at = 2),
            track("l3", "Hometown Glory", at = 3), track("l4", "Chasing Pavements", at = 4),
        ))
        val ranked = ArtistPopular.ranked(ArtistPopular.MOST, songs(listOf(studio, live)), mapOf("21" to listens(9000, 500, 400, 300), "live" to listens(100, 8000, 50, 40)))
        assertEquals(listOf("s1", "l2", "s2", "s3"), ranked.map { it.first.rpmId })
    }

    @Test fun theDiscIsPartOfThePlace() {
        val deluxe = CloudAlbum("d", "25 (Deluxe)", "Adele",
            (1..4).map { track("a$it", "Song $it", disc = 1, at = it) } + (1..4).map { track("b$it", "Demo $it", disc = 2, at = it) })
        val plays = listens(400, 300, 200, 100) + (1..4).associate { TrackPlace(2, it) to it.toLong() }
        assertEquals(listOf("a1", "a2", "a3", "a4"), ArtistPopular.ranked(ArtistPopular.MOST, songs(listOf(deluxe)), mapOf("d" to plays)).map { it.first.rpmId })
    }

    @Test fun aRecordWithNoAnswerAddsNothing() {
        val ranked = ArtistPopular.ranked(ArtistPopular.MOST, songs(listOf(record("21", 6), record("25", 6))), mapOf("21" to listens(6, 5, 4, 3, 2, 1)))
        assertEquals(6, ranked.size)
        assertTrue(ranked.all { it.second.key == "21" })
    }

    @Test fun aTrackWithNoNumberIsLeftOut() {
        val album = CloudAlbum("x", "x", "Adele", listOf(track("n", "No number", at = 0)) + (1..9).map { track("t$it", "T $it", at = it) })
        val ranked = ArtistPopular.ranked(ArtistPopular.MOST, songs(listOf(album)), mapOf("x" to listens(9, 8, 7, 6, 5, 4, 3, 2, 1)))
        assertFalse(ranked.any { it.first.rpmId == "n" })
    }

    @Test fun theRecordsHoldingMostOfTheirSongsAreAskedFirst() =
        assertEquals(listOf("album", "ep", "single"), ArtistPopular.records(songs(listOf(record("single", 1), record("album", 9), record("ep", 4)))).map { it.key })

    @Test fun aBracketedAsideDoesNotMakeAnotherSong() {
        assertEquals(ArtistPopular.title(track("a", "Hello (Live at the BBC)", at = 1)), ArtistPopular.title(track("b", "hello", at = 1)))
        assertEquals("skyfall", ArtistPopular.title(track("c", "Skyfall [Remastered]", at = 1)))
    }
}
