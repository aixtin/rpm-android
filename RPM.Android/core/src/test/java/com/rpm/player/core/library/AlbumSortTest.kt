package com.rpm.player.core.library

import com.rpm.player.core.cloud.CloudTrack
import com.rpm.player.core.cloud.CloudTrackTags
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumSortTest {

    private fun ordered(ordering: AlbumOrdering, vararg facts: AlbumFacts): List<String> =
        AlbumSort.by(facts.toList(), ordering) { it }.map { it.album }

    @Test fun byArtistThenAlbumIgnoringCase() {
        // "Abba" and "abba" are one artist, so the album breaks the tie, also ignoring case: A before b.
        assertEquals(
            listOf("A", "b", "C"),
            ordered(AlbumOrdering.Artist, AlbumFacts("C", "zed"), AlbumFacts("b", "abba"), AlbumFacts("A", "Abba")),
        )
    }

    @Test fun byYearPutsTheUntaggedLastAndTheNewestFirst() {
        assertEquals(
            listOf("2001", "1990", "none"),
            ordered(AlbumOrdering.Year, AlbumFacts("none", "x"), AlbumFacts("1990", "x", year = 1990), AlbumFacts("2001", "x", year = 2001)),
        )
    }

    @Test fun byAddedNewestFirstAndTiesFallBackToTheShelf() {
        assertEquals(
            listOf("new", "a", "b"),
            ordered(AlbumOrdering.Added, AlbumFacts("b", "b", addedAt = 5), AlbumFacts("new", "z", addedAt = 9), AlbumFacts("a", "a", addedAt = 5)),
        )
    }

    @Test fun theYearIsTheOneMostTracksCarryAndTheEarlierOnATie() {
        fun track(year: Int) = CloudTrack(rpmId = "$year", tags = CloudTrackTags(year = year))
        assertEquals(1999, AlbumSort.yearOf(listOf(track(1971), track(1999), track(1999))))
        assertEquals(1980, AlbumSort.yearOf(listOf(track(1990), track(1980))))
        assertEquals(0, AlbumSort.yearOf(listOf(track(0))))
    }
}
