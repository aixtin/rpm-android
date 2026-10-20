package com.rpm.player.core.playback

import com.rpm.player.core.cloud.Shelf
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import com.rpm.player.core.home.Computers
import com.rpm.player.core.home.HomeCatalogs
import com.rpm.player.core.home.PeerRefused
import com.rpm.player.core.home.PeerTrack
import com.rpm.player.core.home.PeerWire
import com.rpm.player.core.net.escapeSegment
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Where a track's bytes come from. */
sealed interface TrackSource {
    data class Computer(val key: String) : TrackSource
    data object Cloud : TrackSource
}

/**
 * Chooses, for each track, the computer or the cloud, and keeps that choice until the playback service
 * moves past the track ([keepOnly]) or starts it over from the cloud ([useCloud]). Media3 opens a
 * track again for a seek or a retry, at a byte offset into the file it is decoding. The computer's
 * file (the original, FLAC for most of the library) and the cloud's file (AAC) are different files,
 * so every open for one decoder must reach the same one. A seek back to 0:00 reuses the decoder too,
 * so not even an open at byte 0 chooses again.
 *
 * A computer is chosen when it is paired, on the network, not failed recently, holds the track, and the
 * track's format plays here; otherwise the cloud.
 */
class TrackSources(
    private val computers: Computers,
    private val catalogs: HomeCatalogs,
    private val streamLinks: StreamLinks,
    /** Whether the FFmpeg extension is in this build and decodes ALAC, AC-3 and E-AC-3. */
    private val extension: Boolean,
) {
    /** Keyed by media id, so the same track on two shelves is two choices. */
    private val chosen = ConcurrentHashMap<String, TrackSource>()

    fun sourceOf(mediaId: String): TrackSource? = chosen[mediaId]

    /** The track failed from a computer and starts over (the service prepares it again). Every open of
     * the new decoder reaches the cloud. */
    fun useCloud(mediaId: String) {
        chosen[mediaId] = TrackSource.Cloud
    }

    /** Keeps only the choices for [mediaIds] (the item playing and the one Media3 may be loading next). */
    fun keepOnly(mediaIds: Set<String>) {
        chosen.keys.retainAll(mediaIds)
    }

    /** The source chosen for [ref], choosing now if none is held. */
    fun choice(ref: TrackRef): TrackSource = chosen.getOrPut(ref.mediaId) {
        choose(ref).also { android.util.Log.d(TAG, "${ref.mediaId} from $it") }
    }

    /** The address to open [ref] at, from [source]. Blocking; called on a loader thread. */
    fun address(source: TrackSource, ref: TrackRef): String {
        return when (source) {
            is TrackSource.Computer -> {
                // Chosen earlier. If the computer has gone since, the open fails and the service starts the
                // track over from the cloud.
                val computer = computers.usable(source.key) ?: throw IOException("computer ${source.key} is away")
                val token = runBlocking { computers.token(computer) }
                // A ticket for this one track, not the token: the player does not pin the computer's key, so
                // this URL is what someone on the network could read (PeerWire.mediaTicket).
                computer.base + PeerWire.TRACK_ROUTE + escapeSegment(ref.rpmId) +
                    "?s=" + Uri.encode(PeerWire.mediaHandle(token)) +
                    "&t=" + Uri.encode(PeerWire.mediaTicket(token, PeerWire.TRACK_ROUTE, ref.rpmId))
            }
            TrackSource.Cloud -> streamLinks.resolveBlocking(ref, kilobits = PlaybackService.STREAM_KILOBITS)
        }
    }

    /** A track on a shelf another account shares always plays from the cloud: the computers on this
     * network hold the person's own music, not the owner's. */
    private fun choose(ref: TrackRef): TrackSource {
        if (ref.shelf != Shelf.Own) return TrackSource.Cloud
        return catalogs.here.value.values.firstOrNull { catalog ->
            catalog.tracks[ref.rpmId]?.let { playsHere(it, extension) } == true && computers.usable(catalog.computer) != null
        }?.let { TrackSource.Computer(it.computer) } ?: TrackSource.Cloud
    }

    /** A failed address, open or read. A 401 drops the computer's token. Anything else marks the computer
     * as away, so the next choice is the cloud. This includes a 403 from the session: the computer no
     * longer trusts the TV. */
    fun failed(source: TrackSource, error: IOException) {
        val key = (source as? TrackSource.Computer)?.key ?: return
        val status = (error as? HttpDataSource.InvalidResponseCodeException)?.responseCode ?: (error as? PeerRefused)?.status
        if (status == 401) {
            computers.discovery.found.value[key]?.let(computers::forgetToken)
        } else {
            computers.markAway(key)
        }
    }

    companion object {
        /** Where each choice is logged, for `adb logcat -s rpm.sources`. */
        private const val TAG = "rpm.sources"

        /** File types Media3 reads without the FFmpeg extension. */
        private val CONTAINERS = setOf("flac", "mp3", "m4a", "m4b", "mp4", "aac", "ogg", "oga", "opus", "wav")

        /** Whether this TV can decode the computer's file: its container, and, for ALAC, AC-3 or E-AC-3
         * (TagLib names the MP4 sample entry in the codec text, "MPEG-4 Audio (alac)"), only with the
         * FFmpeg [extension]. A track that does not play here plays from the cloud's AAC instead. */
        fun playsHere(track: PeerTrack, extension: Boolean): Boolean {
            val container = track.path.substringAfterLast('.', "").lowercase()
            if (container !in CONTAINERS) return false
            if (extension) return true
            val codec = track.codec?.lowercase().orEmpty()
            return listOf("alac", "ac-3", "ec-3").none { it in codec }
        }

        /** Whether the FFmpeg extension loaded and decodes the three formats it was built for. */
        fun extensionDecodes(): Boolean = try {
            FfmpegLibrary.isAvailable() &&
                listOf(MimeTypes.AUDIO_ALAC, MimeTypes.AUDIO_AC3, MimeTypes.AUDIO_E_AC3).all(FfmpegLibrary::supportsFormat)
        } catch (_: Throwable) {
            false
        }
    }
}

/**
 * Opens `rpm-cloud://track/<rpmId>` at the source [TrackSources] chose for the track, through one client
 * that pins each computer's key and checks every other host the ordinary way. A computer's failure, at
 * the open or in the middle of a read, is reported so the next choice avoids it.
 */
@OptIn(UnstableApi::class)
class SourcedDataSource(private val upstream: DataSource, private val sources: TrackSources) : DataSource {

    private var source: TrackSource? = null

    override fun open(dataSpec: DataSpec): Long {
        val ref = CloudTrackUri.ref(dataSpec.uri)
        if (ref == null) {
            source = null
            return upstream.open(dataSpec)
        }
        val chosen = sources.choice(ref)
        source = chosen
        val address = try {
            sources.address(chosen, ref)
        } catch (e: Exception) {
            val failure = e as? IOException ?: IOException("could not reach a source for ${ref.mediaId}", e)
            sources.failed(chosen, failure)
            throw failure
        }
        return try {
            upstream.open(dataSpec.withUri(Uri.parse(address)))
        } catch (e: IOException) {
            sources.failed(chosen, e)
            throw e
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = try {
        upstream.read(buffer, offset, length)
    } catch (e: IOException) {
        source?.let { sources.failed(it, e) }
        throw e
    }

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() = upstream.close()

    class Factory(private val upstream: DataSource.Factory, private val sources: TrackSources) : DataSource.Factory {
        override fun createDataSource(): DataSource = SourcedDataSource(upstream.createDataSource(), sources)
    }
}
