package com.rpm.player.core.papers

import com.rpm.player.core.cloud.ApiException
import com.rpm.player.core.library.CloudAlbum
import com.rpm.player.core.net.AppKey
import com.rpm.player.core.net.Hosts
import com.rpm.player.core.net.WireJson
import com.rpm.player.core.net.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** A song's place on its record: the disc, counted from 1, and the position on it. */
data class TrackPlace(val disc: Int, val position: Int)

/**
 * What `metadata/plays` returns for one record: its track list as the catalogue has it, and how often each
 * recording has been listened to across all listeners. It matches the iOS app's `RecordPlays`. Every field is optional.
 */
@Serializable
data class RecordPlays(val tracks: List<Track> = emptyList(), val plays: List<Plays> = emptyList()) {
    @Serializable
    data class Track(val position: Int = 0, val title: String = "", val recordingMbid: String = "")

    @Serializable
    data class Plays(val recordingMbid: String = "", val listens: Long = 0, val listeners: Int = 0)

    /** The listens by place on the record. A track list whose numbering starts again is the next disc,
     * because the catalogue lists a set one disc after another. */
    val listensByPlace: Map<TrackPlace, Long>
        get() {
            val byRecording = plays.associate { it.recordingMbid.lowercase() to it.listens }
            val places = HashMap<TrackPlace, Long>()
            var disc = 1
            var last = 0
            for (track in tracks) {
                if (track.position <= 0) continue
                if (track.position <= last) disc++
                last = track.position
                byRecording[track.recordingMbid.lowercase()]?.let { places[TrackPlace(disc, track.position)] = it }
            }
            return places
        }

    companion object {
        val EMPTY = RecordPlays()
    }
}

/** A record as the plays are asked for: who, what, how many songs (to tell an album from its single), and the
 * MusicBrainz release that a song of it is tagged with, where there is one. */
data class PlaysRecord(val artist: String, val album: String, val tracks: Int, val release: String?) {
    companion object {
        fun of(album: CloudAlbum) = PlaysRecord(
            album.artist,
            album.album,
            album.tracks.size,
            album.tracks.firstNotNullOfOrNull { it.tags?.musicBrainzReleaseId?.takeIf { id -> id.isNotBlank() } },
        )
    }
}

/** The plays, and whether the API marked them as only part of an answer. */
data class PlaysAnswer(val plays: RecordPlays, val partial: Boolean)

/**
 * How often each song of a record has been listened to across all listeners, for an artist's popular
 * songs. It matches the iOS app's `WorldPlays`. Asked once per record for the life of the app and kept
 * in memory. A record that the catalogue does not know is kept as an answer (no plays). A failure is
 * not kept, and neither is an answer that the API marked partial, because the API finishes it in the
 * background. Two asks for one record at once make one request. The request runs in [scope], so a
 * page that is left before the answer arrives does not cancel it, and the answer is kept for the next ask.
 *
 * Thread-safe: the maps are touched under [lock] only, and nothing waits while holding it.
 */
class WorldPlays(private val scope: CoroutineScope, private val fetch: suspend (PlaysRecord) -> PlaysAnswer) {
    constructor(http: OkHttpClient, scope: CoroutineScope) : this(scope, { record -> ask(http, record) })

    private val lock = Any()
    private val known = HashMap<PlaysRecord, Map<TrackPlace, Long>>()
    private val running = HashMap<PlaysRecord, Deferred<PlaysAnswer>>()

    /** The plays if they are already known, without asking. */
    fun cached(record: PlaysRecord): Map<TrackPlace, Long>? = synchronized(lock) { known[record] }

    /** The plays by place, from memory or from the API; null when the request failed. */
    suspend fun plays(record: PlaysRecord): Map<TrackPlace, Long>? {
        val asking = synchronized(lock) {
            known[record]?.let { return it }
            running[record] ?: scope.async(Dispatchers.IO) {
                try {
                    fetch(record).also { answer ->
                        if (!answer.partial) synchronized(lock) { known[record] = answer.plays.listensByPlace }
                    }
                } finally {
                    synchronized(lock) { running.remove(record) }
                }
            }.also { running[record] = it }
        }
        return try {
            asking.await().plays.listensByPlace
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            null
        } catch (_: Exception) {
            null
        }
    }

    /** Forgets every answer. Called on sign-out. */
    fun forget() = synchronized(lock) { known.clear() }

    companion object {
        private suspend fun ask(http: OkHttpClient, record: PlaysRecord): PlaysAnswer {
            if (record.artist.isBlank() || record.album.isBlank()) return PlaysAnswer(RecordPlays.EMPTY, partial = false)
            val url = (Hosts.API + "metadata/plays").toHttpUrl().newBuilder()
                .addQueryParameter("artist", record.artist)
                .addQueryParameter("album", record.album)
                .apply { if (record.tracks > 0) addQueryParameter("tracks", record.tracks.toString()) }
                .apply { if (!record.release.isNullOrBlank()) addQueryParameter("release", record.release) }
                .build()
            val request = Request.Builder().url(url).header(AppKey.HEADER, AppKey.VALUE).build()
            return http.newCall(request).await().use { response ->
                if (response.code !in 200..299) throw ApiException.Status(response.code)
                PlaysAnswer(WireJson.decodeFromString(RecordPlays.serializer(), response.body.string()), response.header(PapersClient.PARTIAL) != null)
            }
        }
    }
}
