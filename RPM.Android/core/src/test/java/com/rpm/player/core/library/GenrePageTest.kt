package com.rpm.player.core.library

import com.rpm.player.core.cloud.CloudTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a genre's page reads besides its shelves: the songs played most (the iOS app's HomeSongsTests),
 * its mood lists, and its photograph's name. */
class GenrePageTest {

    private fun track(id: String) = CloudTrack(rpmId = id)

    @Test fun theMostPlayedComeFirstAndOnlySongsPlayedAtAll() {
        val tracks = listOf(track("t1"), track("t2"), track("t3"))
        val plays = mapOf("t1" to Plays(1, 10), "t2" to Plays(5, 10))
        assertEquals(listOf("t2", "t1"), MostPlayed.songs(tracks, plays, 16).map { it.rpmId })
    }

    @Test fun aTieGoesToTheMostRecentThenToTheId() {
        val tracks = listOf(track("b"), track("a"), track("c"))
        val plays = mapOf("a" to Plays(2, 10), "b" to Plays(2, 10), "c" to Plays(2, 20))
        assertEquals(listOf("c", "a", "b"), MostPlayed.songs(tracks, plays, 16).map { it.rpmId })
    }

    @Test fun theCountIsAMostAndNothingPlayedIsNothing() {
        val tracks = listOf(track("t1"), track("t2"))
        val plays = mapOf("t1" to Plays(1, 1), "t2" to Plays(1, 2))
        assertEquals(1, MostPlayed.songs(tracks, plays, 1).size)
        assertTrue(MostPlayed.songs(tracks, emptyMap(), 16).isEmpty())
        assertTrue(MostPlayed.songs(tracks, plays, 0).isEmpty())
    }

    @Test fun aSongOnTwoRecordsIsCountedOnce() {
        val plays = mapOf("t1" to Plays(3, 1))
        assertEquals(1, MostPlayed.songs(listOf(track("t1"), track("t1")), plays, 16).size)
    }

    private fun list(id: String, generated: Boolean, genre: String?, decade: Int) = TrackList(
        id = id, storeId = id, name = ListName.Text(id), tracks = emptyList(), missing = 0, folder = emptyList(),
        generated = generated, kind = null, plainMood = false, genre = genre, decade = decade, blurb = null,
    )

    @Test fun aGenresMoodsAreTheComputersListsCutToItAlone() {
        val lists = listOf(
            list("mine", generated = false, genre = "Rock", decade = 0),
            list("rock", generated = true, genre = "Rock", decade = 0),
            list("rock70s", generated = true, genre = "Rock", decade = 1970),
            list("jazz", generated = true, genre = "Jazz", decade = 0),
        )
        assertEquals(listOf("rock"), GenreWall.moods("Rock", lists).map { it.id })
    }

    @Test fun aPhotographIsNamedAsTheScriptNamesIt() {
        assertEquals("genre-hip-hop-rap", GenreMarks.photoName("Hip-Hop / Rap"))
        assertEquals("genre-r-b", GenreMarks.photoName("R&B"))
        assertEquals("genre-other-unknown", GenreMarks.photoName("Other / Unknown"))
        assertEquals("genre-electronic-dance", GenreMarks.photoName("Electronic / Dance"))
    }
}
