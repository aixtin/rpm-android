package com.rpm.player.core.library

import com.rpm.player.core.cloud.CloudTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The cases of the iOS app's SongSearchTests. */
class SongSearchTest {

    private fun album(key: String, title: String, artist: String, vararg songs: Pair<String, String?>) =
        CloudAlbum(key, title, artist, songs.mapIndexed { i, (name, by) -> CloudTrack(rpmId = "$key/$i", title = name, artist = by ?: artist) })

    private val library = listOf(
        album("hex", "Hex", "Bark Psychosis", "The Loom" to null, "Absent Friend" to null),
        album("hits", "Now 12", "Various Artists", "Loom Again" to "Someone", "Other" to "Bark Psychosis"),
        album("x", "Nothing", "Nobody", "Silence" to null),
    )

    @Test fun aSongMatchesOnItsTitleItsArtistOrItsRecord() = runBlocking {
        assertEquals(listOf("hex/0", "hits/0"), SongSearch.through(library, "loom").map { it.track.rpmId })
        assertEquals(listOf("hex/0", "hex/1", "hits/1"), SongSearch.through(library, "BARK").map { it.track.rpmId })
        assertEquals(listOf("hits/0", "hits/1"), SongSearch.through(library, "now 12").map { it.track.rpmId })
    }

    @Test fun aSongComesWithTheRecordItIsOn() = runBlocking {
        assertEquals("hits", SongSearch.through(library, "other").single().album.key)
    }

    @Test fun recordsMatchOnTheSleeveAloneNotOnTheirSongs() = runBlocking {
        assertEquals(listOf("hex"), SongSearch.records(library, "bark").map { it.key })
        assertTrue(SongSearch.records(library, "loom").isEmpty())
    }

    @Test fun blankWordsFindNothing() = runBlocking {
        assertTrue(SongSearch.through(library, "  ").isEmpty())
        assertTrue(SongSearch.records(library, "").isEmpty())
    }

    @Test fun theWordsAreTrimmed() = runBlocking {
        assertEquals(1, SongSearch.through(library, " silence ").size)
    }

    @Test fun theSongsStopAtTheCeiling() = runBlocking {
        val big = (0 until 40).map { n -> album("a$n", "Record $n", "Act", *Array(10) { "Song $it" to null }) }
        assertEquals(SongSearch.CEILING, SongSearch.through(big, "song").size)
        assertEquals(SongSearch.RECORD_CEILING.coerceAtMost(40), SongSearch.records(big, "record").size)
    }

    @Test fun aCancelledSearchStops() {
        val job = Job().apply { cancel() }
        val stopped = try {
            runBlocking { withContext(job) { SongSearch.through(library, "loom") } }
            false
        } catch (_: CancellationException) {
            true
        }
        assertTrue(stopped)
    }
}
