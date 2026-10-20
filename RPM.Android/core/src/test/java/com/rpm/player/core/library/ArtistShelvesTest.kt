package com.rpm.player.core.library

import com.rpm.player.core.cloud.CloudTrack
import com.rpm.player.core.cloud.CloudTrackTags
import org.junit.Assert.assertEquals
import org.junit.Test

/** The shelves an artist's page is cut into on the TV: the same rule as the desktop's ArtistShelvesTests. */
class ArtistShelvesTest {

    private fun album(key: String, name: String, year: Int = 0, artist: String = "Queen", trackArtist: String = artist) =
        CloudAlbum(key, name, artist, listOf(CloudTrack(rpmId = "$key/1", artist = trackArtist, tags = CloudTrackTags(year = year))))

    private fun typed(key: String, name: String, vararg types: String?) =
        CloudAlbum(key, name, "Queen", types.mapIndexed { i, type -> CloudTrack(rpmId = "$key/$i", artist = "Queen", tags = CloudTrackTags(releaseType = type)) })

    @Test fun aSetReleaseTypeWinsOverTheNameAndEpsAndMixtapesHaveShelves() {
        val albums = listOf(
            typed("through", "Live Through This", "Album"),
            typed("ep", "Five Live", "Ep"),
            typed("tape", "Street Sounds", "Mixtape"),
            typed("guess", "Live at Wembley", null),
        ).associateBy { it.key }
        val queen = ArtistWallItem("Queen", albums = albums.keys.toList(), together = emptyList(), appearances = emptyList(), tracks = 4)

        val shelves = ArtistShelves.of(queen, albums)

        assertEquals(listOf("through"), shelves.records.map { it.key })
        assertEquals(listOf("ep"), shelves.eps.map { it.key })
        assertEquals(listOf("tape"), shelves.mixtapes.map { it.key })
        assertEquals(listOf("guess"), shelves.live.map { it.key })
    }

    @Test fun mostOfARecordsTracksDecideItsType() {
        assertEquals(ReleaseType.Ep, ReleaseType.shelfOf(typed("a", "A", "Ep", "Ep", "single")))
        assertEquals(ReleaseType.Single, ReleaseType.shelfOf(typed("b", "B - Single", null, "nonsense")))
        assertEquals(ReleaseType.Album, ReleaseType.shelfOf(typed("c", "C", null)))
    }

    @Test fun eachRecordLandsOnOneShelfByItsName() {
        val albums = listOf(
            album("opera", "A Night at the Opera"),
            album("wembley", "Live at Wembley"),
            album("hits", "Greatest Hits"),
            album("flash", "Flash Gordon (Original Soundtrack)"),
            album("rhapsody", "Bohemian Rhapsody - Single"),
            album("killers", "Live Killers - Single"),
            album("duets", "Hot Space Duets", artist = "Queen & David Bowie"),
            album("aid", "Live Aid", artist = "Various Artists", trackArtist = "Queen"),
        ).associateBy { it.key }
        val queen = ArtistWallItem(
            "Queen",
            albums = listOf("opera", "wembley", "hits", "flash", "rhapsody", "killers"),
            together = listOf("duets"),
            appearances = listOf("aid"),
            tracks = 8,
        )

        val shelves = ArtistShelves.of(queen, albums)

        assertEquals(listOf("opera"), shelves.records.map { it.key })
        assertEquals(listOf("duets"), shelves.together.map { it.key })
        assertEquals(listOf("wembley"), shelves.live.map { it.key })
        assertEquals(listOf("flash"), shelves.soundtracks.map { it.key })
        assertEquals(listOf("hits"), shelves.compilations.map { it.key })
        assertEquals(setOf("rhapsody", "killers"), shelves.singles.map { it.key }.toSet())
        assertEquals(listOf("aid"), shelves.appearances.map { it.key })
        // Every song on their own records, then the songs on other artists' records that name them.
        assertEquals("aid/1", shelves.songs.last().first.rpmId)
    }

    @Test fun aShelfIsNewestFirstWithTheUndatedLast() {
        val albums = listOf(
            album("old", "Old", year = 1990),
            album("undated", "Undated"),
            album("new", "New", year = 2020),
            album("also", "Also New", year = 2020),
        ).associateBy { it.key }
        val act = ArtistWallItem("Queen", listOf("old", "undated", "new", "also"), emptyList(), emptyList(), 4)

        assertEquals(listOf("also", "new", "old", "undated"), ArtistShelves.of(act, albums).records.map { it.key })
    }
}
