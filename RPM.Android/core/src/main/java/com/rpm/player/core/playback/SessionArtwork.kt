package com.rpm.player.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.FlagSet
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import com.rpm.player.core.cast.CastCovers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArraySet

/**
 * The session's player with the picture of what is playing in its metadata: the record's cover, or the station's logo.
 *
 * Android TV's now playing card on the launcher draws the session's `ALBUM_ART` bitmap, else its `ART` bitmap, and
 * nothing else (checked in com.google.android.tvrecommendations 7.4); without it the card shows a bare icon. Media3 puts a
 * bitmap there, and on the media notification, only from the metadata's artwork. The queue's items carry no picture,
 * because every controller is sent the whole queue, so the picture of the item playing is read here, through the app's
 * image loader ([CastCovers], the JPEG a cast receiver is sent), and added to [getMediaMetadata] for that item alone.
 *
 * Media3 builds the platform session's metadata again from [getMediaMetadata] on every metadata event, so a picture
 * that arrives is announced as one ([announce]), and a later event from the player underneath (a station's song title)
 * keeps it.
 *
 * Main thread, as the player is. Two events can arrive in either order: the item changing and a picture's read
 * finishing. A picture is kept with the media id it was read for and shown only while
 * that id is the current item's, so a read that finishes after the item changed cannot dress the next item in it.
 */
@OptIn(UnstableApi::class)
class SessionArtwork(
    player: Player,
    private val covers: CastCovers,
    private val scope: CoroutineScope,
) : ForwardingPlayer(player) {
    private val listeners = CopyOnWriteArraySet<Player.Listener>()

    /** The last picture read, by the media id it is of. */
    private var held: Held? = null

    /** The read in flight and the media id it is for. */
    private var reading: Job? = null
    private var readingFor: String? = null

    private class Held(val mediaId: String, val jpeg: ByteArray)

    init {
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = follow()

            // A queue handed over replaces the items without a transition when the index stays the same.
            override fun onTimelineChanged(timeline: Timeline, reason: Int) = follow()
        })
        follow()
    }

    override fun addListener(listener: Player.Listener) {
        super.addListener(listener)
        listeners += listener
    }

    override fun removeListener(listener: Player.Listener) {
        super.removeListener(listener)
        listeners -= listener
    }

    override fun getMediaMetadata(): MediaMetadata {
        val own = super.getMediaMetadata()
        val picture = held ?: return own
        if (currentMediaItem?.mediaId != picture.mediaId) return own
        return own.buildUpon().setArtworkData(picture.jpeg, MediaMetadata.PICTURE_TYPE_FRONT_COVER).build()
    }

    /** Reads the current item's picture, unless it is held already or being read. */
    private fun follow() {
        val item = currentMediaItem ?: return
        val id = item.mediaId
        if (id.isEmpty() || id == held?.mediaId || id == readingFor) return
        reading?.cancel()
        readingFor = id
        reading = scope.launch {
            val jpeg = try {
                covers.jpegOf(item)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (readingFor == id) readingFor = null
            if (jpeg == null) return@launch
            held = Held(id, jpeg)
            if (currentMediaItem?.mediaId == id) announce()
        }
    }

    /** Tells the session the metadata changed: it reads [getMediaMetadata] again, picture included. */
    private fun announce() {
        val metadata = mediaMetadata
        val events = Player.Events(FlagSet.Builder().add(Player.EVENT_MEDIA_METADATA_CHANGED).build())
        for (listener in listeners) {
            listener.onMediaMetadataChanged(metadata)
            listener.onEvents(this, events)
        }
    }
}
