package com.rpm.player.core.radio

import kotlinx.coroutines.delay
import com.rpm.player.core.net.AppKey
import com.rpm.player.core.net.Hosts
import com.rpm.player.core.net.WireJson
import com.rpm.player.core.net.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.Normalizer

/**
 * The cover of the song a station says it is playing, so the player and the mini player can show the song
 * rather than only the station's logo. Asked of the rpm API (`metadata/song`), which looks the song up and keeps
 * the answer for every listener; a station repeats its songs, so most asks are answered from there at once.
 *
 * One ask per song for the life of the app, keyed on the artist and title folded (case, accents, spacing), and two
 * asks while the first is running share its answer. A song the API found no cover for is kept as having none; an
 * ask that failed (offline, refused) is forgotten, so the song is asked again the next time it plays.
 */
class SongCovers(private val http: OkHttpClient) {
    private val lock = Mutex()
    private val known = HashMap<String, CompletableDeferred<String?>>()

    /** The covers found so far, by the same key as [known], for [peek]: read on the main thread. */
    private val answered = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** The cover already found for this song, without asking. */
    fun peek(artist: String, title: String): String? = answered[keyOf(artist, title)]

    /** The address of this song's cover, or null where there is none or it could not be asked. */
    suspend fun of(artist: String, title: String): String? {
        val key = keyOf(artist, title)
        if (key.isBlank()) return null
        val (asked, mine) = lock.withLock {
            known[key]?.let { it to false } ?: CompletableDeferred<String?>().also { known[key] = it }.let { it to true }
        }
        if (!mine) return asked.await()
        val answer = try {
            // A song nobody has asked about is looked up while the ask waits; one that takes longer than the API
            // waits comes back "busy" (503), and is asked once more after the wait it names.
            fetch(artist, title).let { first -> if (first == Answer.Busy) { delay(BUSY_WAIT_MS); fetch(artist, title) } else first }
        } catch (e: CancellationException) {
            lock.withLock { known.remove(key) }
            asked.complete(null)
            throw e
        }
        when (answer) {
            is Answer.Found -> answered[key] = answer.cover
            Answer.Failed, Answer.Busy -> lock.withLock { known.remove(key) }
            Answer.None -> {}
        }
        val cover = (answer as? Answer.Found)?.cover
        asked.complete(cover)
        return cover
    }

    private sealed interface Answer {
        data class Found(val cover: String) : Answer
        data object None : Answer
        data object Failed : Answer
        data object Busy : Answer
    }

    private suspend fun fetch(artist: String, title: String): Answer {
        val url = (Hosts.API + "metadata/song").toHttpUrl().newBuilder()
            .addQueryParameter("artist", artist.trim())
            .addQueryParameter("title", title.trim())
            .addQueryParameter("size", COVER_SIZE.toString())
            .build()
        val request = Request.Builder().url(url).header(AppKey.HEADER, AppKey.VALUE).build()
        return try {
            http.newCall(request).await().use { response ->
                if (response.code == 503) return Answer.Busy
                if (!response.isSuccessful) return Answer.Failed
                val song = WireJson.decodeFromString(SongCover.serializer(), response.body.string())
                song.cover?.takeIf { it.startsWith("https://") }?.let { Answer.Found(it) } ?: Answer.None
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Answer.Failed
        }
    }

    /** What the API answers: the cover and what it matched, all null for a song it could not find. */
    @Serializable
    private data class SongCover(val cover: String? = null, val album: String? = null, val artist: String? = null, val title: String? = null)

    companion object {
        /** The size the cover is asked for: the player's sleeve on a 1080p screen, which the mini player shares. */
        const val COVER_SIZE = 600

        /** How long a busy answer is waited out before the one more ask: the API's Retry-After. */
        private const val BUSY_WAIT_MS = 5_000L

        /** The artist and title folded, so two spellings of one announcement share an answer. */
        fun keyOf(artist: String, title: String): String = fold(artist) + "\u0000" + fold(title)

        private fun fold(text: String): String =
            Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .replace(Regex("\\s+"), " ")
    }
}
