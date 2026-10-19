package com.rpm.player.core.papers

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory

class DossiersTest {
    private val record = AlbumSubject("Fleetwood Mac", "Rumours", 11)
    private val found = Dossier(EncyclopediaArticle("en", "Rumours (album)", "Lead.\n\nMore.", "https://en.wikipedia.org/wiki/Rumours_(album)"), listOf("rock"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val day = 24L * 3600 * 1000

    private fun store(dir: File?, clock: () -> Long = System::currentTimeMillis, fetch: suspend (PaperSubject, List<String>) -> DossierAnswer) =
        Dossiers(listOf("en"), dir, scope, clock, fetch)

    @Test fun anAnswerIsKeptAndReadBackWithoutAsking() = runBlocking {
        val dir = createTempDirectory("papers").toFile()
        val asked = AtomicInteger()
        val first = store(dir) { _, _ -> asked.incrementAndGet(); DossierAnswer(found, partial = false) }
        assertEquals(found, first.read(record) {})

        val second = store(dir) { _, _ -> error("asked again") }
        assertEquals(found, second.cached(record))
        assertEquals(1, asked.get())
    }

    /** "Rumours" the act and "Rumours" by Fleetwood Mac are two things to say something about. */
    @Test fun anActAndARecordOfOneNameAreAskedAboutApart() = runBlocking {
        val act = ArtistSubject("Rumours", emptyList())
        val asked = mutableListOf<PaperSubject>()
        val dossiers = store(null) { subject, _ -> asked += subject; DossierAnswer(found, partial = false) }
        dossiers.read(record) {}
        dossiers.read(act) {}
        assertEquals(listOf(record, act), asked)
    }

    @Test fun anActWithNoNameIsNotAskedAbout() = runBlocking {
        val dossiers = store(null) { _, _ -> error("asked") }
        assertNull(dossiers.read(ArtistSubject("  ", emptyList())) {})
    }

    @Test fun aCacheHitDoesNotSayItIsAsking() = runBlocking {
        val dossiers = store(null) { _, _ -> DossierAnswer(found, partial = false) }
        var asking = 0
        dossiers.read(record) { asking++ }
        dossiers.read(record) { asking++ }
        assertEquals(1, asking)
    }

    @Test fun aPartialAnswerIsShownAndNotKept() = runBlocking {
        val asked = AtomicInteger()
        val dossiers = store(createTempDirectory("papers").toFile()) { _, _ -> asked.incrementAndGet(); DossierAnswer(found, partial = true) }
        assertEquals(found, dossiers.read(record) {})
        assertNull(dossiers.cached(record))
        dossiers.read(record) {}
        assertEquals(2, asked.get())
    }

    @Test fun aFailedRequestIsNullAndNotKept() = runBlocking {
        var fail = true
        val dossiers = store(createTempDirectory("papers").toFile()) { _, _ ->
            if (fail) throw IOException("offline") else DossierAnswer(found, partial = false)
        }
        assertNull(dossiers.read(record) {})
        assertNull(dossiers.cached(record))
        fail = false
        assertEquals(found, dossiers.read(record) {})
    }

    @Test fun twoPagesAskingAboutOneRecordMakeOneRequest() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val asked = AtomicInteger()
        val dossiers = store(null) { _, _ -> asked.incrementAndGet(); gate.await(); DossierAnswer(found, partial = false) }
        val a = async { dossiers.read(record) {} }
        val b = async { dossiers.read(record) {} }
        withTimeout(5_000) { while (asked.get() == 0) kotlinx.coroutines.delay(5) }
        gate.complete(Unit)
        assertEquals(found, a.await())
        assertEquals(found, b.await())
        assertEquals(1, asked.get())
    }

    @Test fun aPageLeftBeforeTheAnswerStillGetsItKept() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val done = CompletableDeferred<Unit>()
        val dossiers = store(null) { _, _ ->
            started.complete(Unit)
            gate.await()
            DossierAnswer(found, partial = false).also { done.complete(Unit) }
        }
        val page = launch(Dispatchers.Default) { dossiers.read(record) {} }
        withTimeout(5_000) { started.await() }
        page.cancelAndJoin()
        gate.complete(Unit)
        withTimeout(5_000) { done.await() }
        withTimeout(5_000) { while (dossiers.cached(record) == null) kotlinx.coroutines.delay(5) }
        assertEquals(found, dossiers.cached(record))
    }

    @Test fun nothingFoundIsKeptForFiveDaysAndAnArticleForThirty() = runBlocking {
        val dir = createTempDirectory("papers").toFile()
        val other = AlbumSubject("Nobody", "Nothing", 3)
        store(dir) { subject, _ -> DossierAnswer(if (subject == record) found else Dossier.NOTHING, partial = false) }.apply {
            read(record) {}
            read(other) {}
        }
        val sixDaysOn = store(dir, clock = { System.currentTimeMillis() + 6 * day }) { _, _ -> error("not asked") }
        assertEquals(found, sixDaysOn.cached(record))
        assertNull(sixDaysOn.cached(other))

        val monthOn = store(dir, clock = { System.currentTimeMillis() + 31 * day }) { _, _ -> error("not asked") }
        assertNull(monthOn.cached(record))
    }

    @Test fun aBlankNameIsNotAskedAbout() = runBlocking {
        val dossiers = store(null) { _, _ -> error("asked") }
        var asking = false
        assertNull(dossiers.read(AlbumSubject(" ", "Rumours", 11)) { asking = true })
        assertNull(dossiers.read(AlbumSubject("Fleetwood Mac", "", 11)) { asking = true })
        assertFalse(asking)
    }

    @Test fun theTrackCountIsNotPartOfWhatARecordIs() {
        assertEquals(AlbumSubject("Fleetwood Mac", "Rumours", 11).id, AlbumSubject(" fleetwood mac", "RUMOURS ", 12).id)
        assertTrue(AlbumSubject("A", "B", 1).id != AlbumSubject("A", "C", 1).id)
    }
}
