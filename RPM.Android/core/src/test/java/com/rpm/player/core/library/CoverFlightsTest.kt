package com.rpm.player.core.library

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Requests for the same cover that are in flight at once share one download ([CoverFlights]). The
 * download is cancelled only when the last of them is cancelled.
 */
class CoverFlightsTest {

    @Test
    fun Two_requests_at_once_download_once() = runBlocking {
        val downloads = AtomicInteger()
        val release = CompletableDeferred<Unit>()
        val download: suspend () -> ByteArray = {
            downloads.incrementAndGet()
            release.await()
            byteArrayOf(1, 2, 3)
        }
        val first = async { CoverFlights.shared("cover:a", download) }
        val second = async { CoverFlights.shared("cover:a", download) }
        waitUntil { downloads.get() == 1 }
        release.complete(Unit)

        assertArrayEquals(byteArrayOf(1, 2, 3), first.await())
        assertArrayEquals(byteArrayOf(1, 2, 3), second.await())
        assertEquals(1, downloads.get())
    }

    @Test
    fun One_request_leaving_does_not_cancel_the_download_for_the_other() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val download: suspend () -> ByteArray = {
            started.complete(Unit)
            release.await()
            byteArrayOf(7)
        }
        val leaving = async { CoverFlights.shared("cover:b", download) }
        val staying = async { CoverFlights.shared("cover:b", download) }
        started.await()
        leaving.cancel()
        release.complete(Unit)

        assertArrayEquals(byteArrayOf(7), staying.await())
    }

    @Test
    fun The_last_request_leaving_cancels_the_download() = runBlocking {
        val cancelled = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val download: suspend () -> ByteArray = {
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                cancelled.complete(Unit)
            }
        }
        val only = async { CoverFlights.shared("cover:c", download) }
        started.await()
        only.cancel()

        withTimeout(2_000) { cancelled.await() }
        assertTrue(cancelled.isCompleted)
    }

    @Test
    fun A_request_after_a_download_ended_downloads_again() = runBlocking {
        val downloads = AtomicInteger()
        val download: suspend () -> ByteArray = {
            downloads.incrementAndGet()
            byteArrayOf(9)
        }
        CoverFlights.shared("cover:d", download)
        CoverFlights.shared("cover:d", download)

        assertEquals(2, downloads.get())
    }

    private suspend fun waitUntil(condition: () -> Boolean) {
        withTimeout(2_000) {
            while (!condition()) kotlinx.coroutines.delay(5)
        }
    }
}
