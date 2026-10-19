package com.rpm.player.core.library

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

/** What the rpm API's `artwork` answers for an artist and album: photos of the artist (fanart.tv and
 * TheAudioDB), square pictures of the artist ([thumbs], which the desktop's ArtistPortraits.SquareAsync and
 * the phone read first), and the artist's logo. It matches the desktop's `Artwork` record. */
@Serializable
data class ArtistArtwork(
    val backgrounds: List<String> = emptyList(),
    val thumbs: List<String> = emptyList(),
    val logo: String? = null,
)

/**
 * The artist photos and logos home's banner is dressed in (the desktop's ArtworkService), asked once per
 * artist and kept for the life of the app. Two asks for the same artist while the first is out share its
 * answer. A failed ask is not kept, so the next banner card for that artist asks again.
 *
 * [peek] reads an answer already kept without asking or waiting, so a card composed again can start from it.
 */
class Artwork(private val http: OkHttpClient) {
    private val lock = Mutex()
    private val known = HashMap<String, CompletableDeferred<ArtistArtwork?>>()

    /** The answers kept, by the same key as [known], for [peek]: read on the main thread, written by the ask. */
    private val answered = java.util.concurrent.ConcurrentHashMap<String, ArtistArtwork>()

    /** The answer kept for [artist], or null when there is none yet (not asked, still out, or failed). Never asks. */
    fun peek(artist: String): ArtistArtwork? {
        val key = artist.trim().lowercase()
        return if (key.isEmpty()) null else answered[key]
    }

    suspend fun of(artist: String, album: String): ArtistArtwork? {
        val key = artist.trim().lowercase()
        if (key.isEmpty()) return null
        val (asked, mine) = lock.withLock {
            known[key]?.let { it to false } ?: CompletableDeferred<ArtistArtwork?>().also { known[key] = it }.let { it to true }
        }
        if (!mine) return asked.await()
        val answer = try {
            fetch(artist, album)
        } catch (e: CancellationException) {
            lock.withLock { known.remove(key) }
            asked.complete(null)
            throw e
        }
        if (answer == null) lock.withLock { known.remove(key) } else answered[key] = answer
        asked.complete(answer)
        return answer
    }

    private suspend fun fetch(artist: String, album: String): ArtistArtwork? {
        val url = (Hosts.API + "artwork").toHttpUrl().newBuilder()
            .addQueryParameter("artist", artist)
            .addQueryParameter("album", album)
            .build()
        val request = Request.Builder().url(url).header(AppKey.HEADER, AppKey.VALUE).build()
        return try {
            http.newCall(request).await().use { response ->
                if (!response.isSuccessful) return null
                WireJson.decodeFromString(ArtistArtwork.serializer(), response.body.string())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}
