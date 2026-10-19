package com.rpm.player.core.library

import com.rpm.player.core.cloud.CloudPlaylist
import com.rpm.player.core.cloud.CloudTrack
import com.rpm.player.core.cloud.CloudTrackTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnEditsTest {
    private fun library(tracks: List<CloudTrack>, playlists: List<CloudPlaylist> = emptyList(), readAt: Long = 0) =
        CloudLibrary(tracks = tracks, coverKeys = null, coverStamps = null, plays = emptyList(), moods = null, playlists = playlists, readAt = readAt)

    private val song = CloudTrack(rpmId = "a", tags = CloudTrackTags(genre = "Rock"), updatedAt = 100)

    @Test fun aFavouriteIsLaidOverARowReadBeforeIt() {
        val edits = OwnEdits("me", marks = mapOf("a" to OwnEdits.Mark(true, at = 7L, stamp = 200L)), playlists = emptyMap(), made = emptyMap())
        val laid = edits.over(library(listOf(song))).tracks.single()
        assertTrue(laid.tags!!.favorite)
        // When it was marked goes with it, so the marked songs put it first.
        assertEquals(7L, laid.tags.favoritedAt)
        assertEquals("Rock", laid.tags.genre)
    }

    /** A row read after the write holds it, or something newer from another device, and is left as it is. */
    @Test fun aRowReadAfterTheWriteIsLeftAlone() {
        val edits = OwnEdits("me", marks = mapOf("a" to OwnEdits.Mark(true, at = 7L, stamp = 50L)), playlists = emptyMap(), made = emptyMap())
        assertFalse(edits.over(library(listOf(song))).tracks.single().tags!!.favorite)
    }

    @Test fun aPlaylistWrittenHereReplacesAnOlderCopyOnly() {
        val older = CloudPlaylist("p", name = "Mine", trackIds = listOf("a"), updatedAt = 100)
        val written = CloudPlaylist("p", name = "Mine", trackIds = listOf("a", "b"), updatedAt = 200)
        val edits = OwnEdits("me", marks = emptyMap(), playlists = mapOf("p" to written), made = emptyMap())
        assertEquals(written, edits.over(library(emptyList(), listOf(older))).playlists.single())

        val newer = CloudPlaylist("p", name = "Mine", trackIds = listOf("c"), updatedAt = 300)
        assertEquals(newer, edits.over(library(emptyList(), listOf(newer))).playlists.single())
    }

    @Test fun aPlaylistDeletedElsewhereStaysDeleted() {
        val written = CloudPlaylist("p", name = "Mine", trackIds = listOf("a"), updatedAt = 200)
        val edits = OwnEdits("me", marks = emptyMap(), playlists = mapOf("p" to written), made = emptyMap())
        assertTrue(edits.over(library(emptyList(), emptyList())).playlists.isEmpty())
    }

    @Test fun noEditsLeaveTheLibraryAsItIs() {
        val held = library(listOf(song))
        assertSame(held, OwnEdits.NONE.over(held))
    }

    @Test fun anotherAccountStartsWithNoEdits() {
        val mine = OwnEdits("me", marks = mapOf("a" to OwnEdits.Mark(true, at = 7L, stamp = 1L)), playlists = emptyMap(), made = emptyMap())
        assertSame(mine, mine.forUser("me"))
        assertTrue(mine.forUser("you").marks.isEmpty())
        assertEquals("you", mine.forUser("you").user)
    }

    @Test fun aMarkTakenOffIsLaidOverARowReadBeforeIt() {
        val loved = song.copy(tags = song.tags!!.copy(favorite = true))
        val edits = OwnEdits("me", marks = mapOf("a" to OwnEdits.Mark(false, at = 0L, stamp = 200L)), playlists = emptyMap(), made = emptyMap())
        assertFalse(edits.over(library(listOf(loved))).tracks.single().tags!!.favorite)
    }

    private val made = CloudPlaylist("n", name = "New", trackIds = listOf("a"), updatedAt = 300)
    private val makes = OwnEdits("me", marks = emptyMap(), playlists = mapOf("n" to made), made = mapOf("n" to 500L))

    /** A read that began before the playlist was made cannot have it, and it is added. */
    @Test fun aPlaylistMadeAfterTheReadIsAdded() {
        assertEquals(listOf(made), makes.over(library(emptyList(), emptyList(), readAt = 100)).playlists)
    }

    /** A read that began after it was made and does not have it means it was deleted elsewhere. */
    @Test fun aPlaylistMadeBeforeTheReadAndMissingFromItStaysDeleted() {
        assertTrue(makes.over(library(emptyList(), emptyList(), readAt = 900)).playlists.isEmpty())
    }

    @Test fun aPlaylistMadeHereAndInTheReadIsNotShownTwice() {
        assertEquals(listOf(made), makes.over(library(emptyList(), listOf(made), readAt = 100)).playlists)
    }

    @Test fun aFavouriteIsStampedInDotNetTicks() {
        // 1 January 2026, 00:00 UTC: DateTime(2026, 1, 1).Ticks.
        assertEquals(639_028_224_000_000_000L, OwnWriter.utcTicks(1_767_225_600_000L))
    }
}
