package com.rpm.player.core.library

import com.rpm.player.core.cloud.CloudTrack
import com.rpm.player.core.cloud.CloudTrackTags
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumWallTest {

    private fun track(
        id: String,
        title: String,
        path: String? = null,
        artist: String? = null,
        album: String? = null,
        albumArtist: String? = null,
        disc: Int = 0,
        number: Int = 0,
    ) = CloudTrack(
        rpmId = id,
        relativePath = path,
        title = title,
        artist = artist,
        album = album,
        tags = CloudTrackTags(discNumber = disc, trackNumber = number, albumArtist = albumArtist),
    )

    @Test fun tracksInOneFolderAreOneAlbumWhateverTheirTagsSay() {
        val albums = AlbumWall.group(
            listOf(
                track("1", "A", path = "Artist/Album/01.flac", album = "Album"),
                track("2", "B", path = "Artist\\Album\\02.flac", album = "Album (Deluxe)"),
            ),
        )
        assertEquals(1, albums.size)
        assertEquals("folder:artist/album", albums[0].key)
    }

    @Test fun withoutAFolderTheDiscSuffixDoesNotSplitAnAlbum() {
        val albums = AlbumWall.group(
            listOf(
                track("1", "A", artist = "X", album = "Record (Disc 1)"),
                track("2", "B", artist = "X", album = "Record [CD2]"),
            ),
        )
        assertEquals(1, albums.size)
    }

    @Test fun tracksPlayByDiscThenNumberWithUnnumberedLastAndTitleBreakingTies() {
        val album = AlbumWall.group(
            listOf(
                track("u2", "Zeta", path = "a/b/5.flac"),
                track("d2t1", "Second disc", path = "a/b/1.flac", disc = 2, number = 1),
                track("t2", "Two", path = "a/b/2.flac", number = 2),
                track("u1", "Alpha", path = "a/b/6.flac"),
                track("t1", "One", path = "a/b/3.flac", disc = 1, number = 1),
            ),
        ).single()
        assertEquals(listOf("t1", "t2", "u1", "u2", "d2t1"), album.tracks.map { it.rpmId })
    }

    @Test fun albumsSortByArtistThenTitleIgnoringCase() {
        val albums = AlbumWall.group(
            listOf(
                track("1", "a", path = "beta/zed/1.flac", artist = "beta", album = "Zed"),
                track("2", "a", path = "Alpha/x/1.flac", artist = "Alpha", album = "X"),
                track("3", "a", path = "beta/apple/1.flac", artist = "Beta", album = "apple"),
            ),
        )
        assertEquals(listOf("X", "apple", "Zed"), albums.map { it.album })
    }

    @Test fun theFolderNamesAnAlbumWhoseTagsAreEmpty() {
        val album = AlbumWall.group(listOf(track("1", "a", path = "Some Artist/Some Album/1.flac"))).single()
        assertEquals("Some Album", album.album)
        assertEquals("Some Artist", album.artist)
    }

    @Test fun anAlbumArtistOutranksTheTrackArtist() {
        val album = AlbumWall.group(
            listOf(track("1", "a", path = "c/1.flac", artist = "Snap!", albumArtist = "Various Artists", album = "Now 22")),
        ).single()
        assertEquals("Various Artists", album.artist)
    }
}
