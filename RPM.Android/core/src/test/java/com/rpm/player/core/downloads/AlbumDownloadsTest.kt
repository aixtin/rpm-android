package com.rpm.player.core.downloads

import com.rpm.player.core.cloud.ApiException
import com.rpm.player.core.cloud.CloudTrack
import com.rpm.player.core.library.CloudAlbum
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * What the downloader does when the store answers a sign with a live encode: it does not download it, it asks the store
 * to make the rendition and shows the album as preparing. Ported from the iPhone's `AlbumDownloadsTests`, with a
 * transfer of a stored object and a refused one added.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumDownloadsTest {
    private val root: File = Files.createTempDirectory("rpm-downloads").toFile()
    private val book = DownloadBook(root)

    @After fun clean() {
        root.deleteRecursively()
    }

    /** A store that has made nothing at any rate, unless [stored] says otherwise. */
    private class Store {
        val signed = mutableListOf<Pair<String, Int>>()
        val asked = mutableListOf<Pair<List<String>, Int>>()
        var failing = 0
        var stored = false
        var status = 200
    }

    private var kilobits = 320

    private fun TestScope.downloads(store: Store): AlbumDownloads = AlbumDownloads(
        book = book,
        online = { true },
        doubt = {},
        sign = { rpmId, rate ->
            store.signed += rpmId to rate
            AlbumDownloads.Signed("https://store.invalid/$rpmId", rate, live = !store.stored)
        },
        make = { rpmIds, rate ->
            if (store.failing > 0) {
                store.failing--
                throw ApiException.Status(400)
            }
            store.asked += rpmIds to rate
        },
        transfer = { _, into, moved ->
            into.parentFile?.mkdirs()
            into.writeBytes(ByteArray(100))
            moved(1.0)
            store.status
        },
        rate = { kilobits },
        scope = backgroundScope,
        io = StandardTestDispatcher(testScheduler),
        clock = { 1_000_000_000L + testScheduler.currentTime },
    ).also {
        it.begin()
        runCurrent()
    }

    private fun album(key: String, ids: List<String>) =
        CloudAlbum(key, key, "Talk Talk", ids.map { CloudTrack(rpmId = it, title = it, album = key) })

    @Test fun aLiveAnswerIsNotDownloadedAndTheStoreIsAskedToMakeIt() = runTest {
        val store = Store()
        val downloads = downloads(store)
        downloads.download(album("a", listOf("t1", "t2", "t3")))
        runCurrent()

        assertEquals(listOf(listOf("t1", "t2", "t3") to 320), store.asked)
        assertEquals(AlbumDownloads.Standing.Downloading(0, 3, 0.0, preparing = true), downloads.standing("a"))
        assertTrue(downloads.busy.value)

        // Every track signed once, and none again before its wait is up.
        downloads.pumpNow()
        assertEquals(3, store.signed.size)
        assertEquals(1, store.asked.size)
        assertNull(book.held("t1"))
    }

    /** The store starts the transcoder at most once every thirty seconds, and each run reads the want list once. So the
     * whole album goes in the first ask, before the tracks are signed one at a time. */
    @Test fun theFirstLiveAnswerAsksForEveryTrackStillToCome() = runTest {
        val store = Store()
        val downloads = downloads(store)
        downloads.download(album("a", listOf("t1", "t2", "t3", "t4", "t5", "t6")))
        runCurrent()
        downloads.pumpNow()

        assertEquals(6, store.signed.size)
        assertEquals(listOf("t1", "t2", "t3", "t4", "t5", "t6"), store.asked.first().first)
        assertEquals("the tracks signed after the first ask were asked for again", 1, store.asked.size)
    }

    @Test fun anAskThatFailedIsSentAgainOnTheNextPass() = runTest {
        val store = Store().apply { failing = 1 }
        val downloads = downloads(store)
        downloads.download(album("a", listOf("t1", "t2")))
        runCurrent()
        downloads.pumpNow()

        assertEquals(listOf(listOf("t1", "t2") to 320), store.asked)
        // Not signed again: the ask is what was lost, not the answer.
        assertEquals(2, store.signed.size)
    }

    @Test fun removingAnAlbumThatIsPreparingEndsItsWait() = runTest {
        val store = Store()
        val downloads = downloads(store)
        downloads.download(album("a", listOf("t1", "t2")))
        runCurrent()

        downloads.remove(setOf("a"))
        runCurrent()
        assertNull(downloads.standing("a"))

        downloads.pumpNow()
        assertEquals(2, store.signed.size)
        assertEquals(1, store.asked.size)
    }

    @Test fun aChangeOfRateSignsAgainAtOnceAndAsksAtTheNewRate() = runTest {
        val store = Store()
        val downloads = downloads(store)
        downloads.download(album("a", listOf("t1", "t2")))
        runCurrent()

        kilobits = 256
        downloads.pumpNow()

        assertEquals(listOf(320, 320, 256, 256), store.signed.map { it.second })
        assertEquals(listOf(320, 256), store.asked.map { it.second })
        assertEquals(listOf("t1", "t2"), store.asked.last().first)
    }

    @Test fun tryingAgainStartsTheWaitOver() = runTest {
        val store = Store()
        val downloads = downloads(store)
        val record = album("a", listOf("t1"))
        downloads.download(record)
        runCurrent()
        downloads.download(record)
        runCurrent()

        assertEquals(2, store.signed.size)
        assertEquals(2, store.asked.size)
    }

    @Test fun aStoredObjectIsDownloadedAndTheAlbumIsOnThePhone() = runTest {
        val store = Store().apply { stored = true }
        val downloads = downloads(store)
        downloads.download(album("a", listOf("t1", "t2")))
        runCurrent()

        assertEquals(AlbumDownloads.Standing.OnPhone, downloads.standing("a"))
        assertEquals(setOf("t1", "t2"), downloads.held.value)
        assertEquals(setOf("a"), downloads.onPhone.value)
        assertEquals(200L, downloads.rows.value.single().bytes)
        assertTrue(store.asked.isEmpty())
        assertTrue(!downloads.busy.value)
    }

    @Test fun aTrackRefusedThreeTimesLeavesTheAlbumStuck() = runTest {
        val store = Store().apply {
            stored = true
            status = 404
        }
        val downloads = downloads(store)
        downloads.download(album("a", listOf("t1")))
        runCurrent()

        assertEquals(AlbumDownloads.MOST_GOES, store.signed.size)
        assertEquals(AlbumDownloads.Standing.Stuck(0, 1), downloads.standing("a"))
        assertNull(book.held("t1"))
    }
}
