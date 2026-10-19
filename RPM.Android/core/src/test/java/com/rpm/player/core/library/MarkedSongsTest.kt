package com.rpm.player.core.library

import com.rpm.player.core.cloud.CloudTrack
import com.rpm.player.core.cloud.CloudTrackTags
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkedSongsTest {

    private fun song(title: String, marked: Boolean, at: Long = 0) =
        CloudTrack(rpmId = title, title = title, tags = CloudTrackTags(favorite = marked, favoritedAt = at))

    private fun record(vararg tracks: CloudTrack) = CloudAlbum(key = tracks.first().rpmId, album = "x", artist = "y", tracks = tracks.toList())

    @Test fun onlyTheMarkedSongsAreListed() =
        assertEquals(listOf("b"), MarkedSongs.of(listOf(record(song("a", false), song("b", true)))).map { it.title })

    @Test fun theNewestMarkComesFirst() {
        val songs = MarkedSongs.of(listOf(record(song("old", true, at = 1)), record(song("new", true, at = 9))))
        assertEquals(listOf("new", "old"), songs.map { it.title })
    }

    @Test fun marksOfOneInstantAreByTitleIgnoringCase() {
        val songs = MarkedSongs.of(listOf(record(song("zed", true), song("Alpha", true), song("mid", true))))
        assertEquals(listOf("Alpha", "mid", "zed"), songs.map { it.title })
    }
}
