package com.rpm.player.core.library

import com.rpm.player.core.cloud.CloudPlaylist
import com.rpm.player.core.cloud.CloudTrack
import com.rpm.player.core.cloud.CloudTrackTags
import com.rpm.player.core.cloud.Shelf
import com.rpm.player.core.cloud.TrackPlaysRow
import com.rpm.player.core.net.WireJson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class LibraryCacheTest {
    private val library = CloudLibrary(
        tracks = (1..10).map { CloudTrack(rpmId = "t$it", title = "Song $it", tags = CloudTrackTags(genre = "Rock", trackNumber = it)) },
        coverKeys = null,
        coverStamps = mapOf("a" to 1L, "b" to 2L),
        plays = listOf(TrackPlaysRow("t1", plays = 3)),
        moods = null,
        playlists = listOf(CloudPlaylist("p", name = "Mine", trackIds = listOf("t2", "t1"))),
        readAt = 1234,
    )

    @Test fun aLibraryComesBackAsItWasWrittenAndInOrder() = runBlocking {
        val cache = LibraryCache(createTempDirectory("cache").toFile())
        cache.write("me", Shelf.Own, library)
        assertEquals(library, cache.read("me", Shelf.Own))
    }

    @Test fun fewerTracksThanSlicesStillComeBack() = runBlocking {
        val cache = LibraryCache(createTempDirectory("cache").toFile())
        val small = library.copy(tracks = library.tracks.take(2))
        cache.write("me", Shelf.Own, small)
        assertEquals(small, cache.read("me", Shelf.Own))

        val none = library.copy(tracks = emptyList())
        cache.write("me", Shelf.Own, none)
        assertEquals(none, cache.read("me", Shelf.Own))
    }

    /** A cache written before the slices, one JSON document, is still read. */
    @Test fun theOlderLayoutIsStillRead() = runBlocking {
        val dir = createTempDirectory("cache").toFile()
        File(dir, "library-me.json").writeText(WireJson.encodeToString(CloudLibrary.serializer(), library))
        assertEquals(library, LibraryCache(dir).read("me", Shelf.Own))
    }

    @Test fun aFileThatDoesNotParseIsNoCache() = runBlocking {
        val dir = createTempDirectory("cache").toFile()
        File(dir, "library-me.json").writeText("RPMLIB2 40 5\n{\"tracks\":[")
        assertNull(LibraryCache(dir).read("me", Shelf.Own))
    }
}
