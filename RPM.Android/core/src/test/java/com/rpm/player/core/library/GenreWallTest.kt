package com.rpm.player.core.library

import com.rpm.player.core.cloud.CloudTrack
import com.rpm.player.core.cloud.CloudTrackTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The TV's genres list and a genre's shelves: the cases of the phone's GenresTests. */
class GenreWallTest {

    private fun album(key: String, vararg genres: String?, artist: String = "A", year: Int = 0, added: Long = 0) =
        CloudAlbum(key, key, artist, genres.mapIndexed { i, g -> CloudTrack(rpmId = "$key/$i", addedAt = added, tags = CloudTrackTags(genre = g, year = year)) })

    @Test fun anAlbumIsOnTheGenreMostOfItsTracksCarry() {
        val wall = GenreWall.build(listOf(album("a", "Jazz", "Blues", "Jazz")))
        assertEquals(listOf("Jazz"), wall.map { it.name })
    }

    @Test fun aTieGoesToTheTagMetFirst() {
        assertEquals("Blues", GenreWall.genreOf(album("a", "Blues", "Jazz")))
    }

    @Test fun untaggedAndUnknownTagsAreOtherAndOtherComesLast() {
        val wall = GenreWall.build(listOf(album("a", null), album("b", "zzz not a genre"), album("c", "Rock")))
        assertEquals("Rock", wall.first().name)
        assertEquals(GenreWall.UNCLASSIFIED, wall.last().name)
        assertEquals(listOf("a", "b"), wall.last().albums)
    }

    @Test fun relatedGenresPutTheHeadsFirst() {
        assertEquals("Rock", Genres.related("Punk").first())
        assertTrue(Genres.related("Nothing of the sort").isEmpty())
    }

    @Test fun aGenresShelvesCountActsOnThisGenreAndCutDecades() {
        val albums = listOf(
            album("r1", "Rock", artist = "R", year = 1975, added = 5),
            album("r2", "Rock", artist = "R", year = 1979, added = 9),
            album("s1", "Rock", artist = "S", year = 1994, added = 7),
        )
        val wall = GenreWall.build(albums)
        val artists = ArtistWall.build(ArtistWall.rows(albums))
        val rock = wall.single { it.name == "Rock" }

        val shelves = GenreShelves.of(rock, wall, albums.associateBy { it.key }, artists)

        assertEquals(listOf("R", "S"), shelves.acts.map { it.name })
        assertEquals(listOf("r2", "s1", "r1"), shelves.added)
        assertEquals(listOf(1970 to listOf("r2", "r1"), 1990 to listOf("s1")), shelves.eras)
    }
}
