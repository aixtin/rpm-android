package com.rpm.player.core.papers

import com.rpm.player.core.cloud.CloudTrack
import com.rpm.player.core.cloud.CloudTrackTags
import com.rpm.player.core.library.CloudAlbum
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/** The iOS app's WorldPlaysTests. */
class WorldPlaysTest {
    private val twentyOne = PlaysRecord("Adele", "21", 11, null)
    private val answer = RecordPlays(
        tracks = listOf(RecordPlays.Track(1, "Rolling in the Deep", "a")),
        plays = listOf(RecordPlays.Plays("a", 9000, 100)),
    )
    private val first = mapOf(TrackPlace(1, 1) to 9000L)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Test fun aRecordIsAskedAboutOnce() = runBlocking {
        val asked = AtomicInteger()
        val store = WorldPlays(scope) { asked.incrementAndGet(); PlaysAnswer(answer, partial = false) }
        assertEquals(first, store.plays(twentyOne))
        assertEquals(first, store.plays(twentyOne))
        assertEquals(1, asked.get())
        assertNotNull(store.cached(twentyOne))
    }

    @Test fun noRecordIsKeptAsAnAnswer() = runBlocking {
        val asked = AtomicInteger()
        val store = WorldPlays(scope) { asked.incrementAndGet(); PlaysAnswer(RecordPlays.EMPTY, partial = false) }
        assertEquals(emptyMap<TrackPlace, Long>(), store.plays(twentyOne))
        assertEquals(emptyMap<TrackPlace, Long>(), store.plays(twentyOne))
        assertEquals(1, asked.get())
    }

    @Test fun aFailureIsNotKept() = runBlocking {
        val asked = AtomicInteger()
        val store = WorldPlays(scope) { asked.incrementAndGet(); throw IOException("down") }
        assertNull(store.plays(twentyOne))
        assertNull(store.plays(twentyOne))
        assertEquals(2, asked.get())
        assertNull(store.cached(twentyOne))
    }

    @Test fun aPartialAnswerIsNotKept() = runBlocking {
        val asked = AtomicInteger()
        val store = WorldPlays(scope) { asked.incrementAndGet(); PlaysAnswer(answer, partial = true) }
        assertNotNull(store.plays(twentyOne))
        assertNull(store.cached(twentyOne))
        store.plays(twentyOne)
        assertEquals(2, asked.get())
    }

    @Test fun twoAsksAtOnceMakeOneRequest() = runBlocking {
        val asked = AtomicInteger()
        val gate = CompletableDeferred<Unit>()
        val store = WorldPlays(scope) { asked.incrementAndGet(); gate.await(); PlaysAnswer(answer, partial = false) }
        val a = async { store.plays(twentyOne) }
        val b = async { store.plays(twentyOne) }
        gate.complete(Unit)
        assertEquals(first, a.await())
        assertEquals(first, b.await())
        assertEquals(1, asked.get())
    }

    @Test fun aRecordIsAskedWithItsTaggedRelease() {
        val album = CloudAlbum("k", "21", "Adele", listOf(
            CloudTrack(rpmId = "1", title = "One", tags = CloudTrackTags(trackNumber = 1)),
            CloudTrack(rpmId = "2", title = "Two", tags = CloudTrackTags(trackNumber = 2, musicBrainzReleaseId = "rel-21")),
        ))
        assertEquals(PlaysRecord("Adele", "21", 2, "rel-21"), PlaysRecord.of(album))
    }

    @Test fun signingOutForgetsTheAnswers() = runBlocking {
        val asked = AtomicInteger()
        val store = WorldPlays(scope) { asked.incrementAndGet(); PlaysAnswer(answer, partial = false) }
        store.plays(twentyOne)
        store.forget()
        store.plays(twentyOne)
        assertEquals(2, asked.get())
    }

    @Test fun aSecondDiscStartsWhereTheNumberingStartsAgain() {
        val set = RecordPlays(
            tracks = listOf(RecordPlays.Track(1, "a", "x"), RecordPlays.Track(2, "b", "y"), RecordPlays.Track(1, "c", "z")),
            plays = listOf(RecordPlays.Plays("x", 1, 1), RecordPlays.Plays("Z", 3, 1)),
        )
        assertEquals(mapOf(TrackPlace(1, 1) to 1L, TrackPlace(2, 1) to 3L), set.listensByPlace)
    }
}
