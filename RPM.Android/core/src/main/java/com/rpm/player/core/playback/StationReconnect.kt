package com.rpm.player.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tunes a station in again when its stream drops (an error, or a live stream that ends), as the
 * desktop's `RadioStreamSource` does: up to [MAX_ATTEMPTS] times, after 0.5, 1, 2, 4, 8 and then 15 s.
 * The count starts again once the stream plays. After the last attempt the player is paused, so the
 * screen shows a failure and not a reconnect that will never come.
 *
 * While an attempt waits, the player still means to play and holds the error (or has ended). The
 * screen reads this as "reconnecting" ([reconnecting]). Each attempt checks, when it comes due, that
 * the failed station is still the current item, still down, and still meant to play. Used on the
 * service's main looper only.
 */
class StationReconnect(
    private val player: Player,
    private val scope: CoroutineScope,
    /** Told when attempts start and when they end. The service passes it on in the session's extras
     * ([EXTRA_ATTEMPTING]), because an attempt restarts the stream at position 0 and buffers with no
     * error, which the screen cannot tell from a fresh tune. */
    private val onAttempting: (Boolean) -> Unit,
) : Player.Listener {

    private var attempts = 0
    private var waiting: Job? = null

    override fun onPlayerError(error: PlaybackException) = dropped()

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) dropped()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) settled()
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady) settled()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = settled()

    /** Playing again, paused, or another item: no attempt is under way and the count starts over. */
    private fun settled() {
        attempts = 0
        waiting?.cancel()
        onAttempting(false)
    }

    private fun dropped() {
        val item = player.currentMediaItem ?: return
        if (StationItems.uuidOf(item.mediaId) == null || !player.playWhenReady) return
        if (attempts >= MAX_ATTEMPTS) {
            // Given up: paused, so the screen says it failed and does not show a reconnect.
            player.pause()
            return
        }
        onAttempting(true)
        val wait = BACKOFF_MS[attempts.coerceAtMost(BACKOFF_MS.lastIndex)]
        attempts++
        waiting?.cancel()
        waiting = scope.launch {
            delay(wait)
            if (player.currentMediaItem?.mediaId != item.mediaId || !player.playWhenReady || !isDown(player)) return@launch
            if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition() else player.prepare()
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 8
        private val BACKOFF_MS = longArrayOf(500, 1_000, 2_000, 4_000, 8_000, 15_000)

        private fun isDown(player: Player): Boolean = player.playerError != null || player.playbackState == Player.STATE_ENDED

        /** Set in the session's extras from a drop until the station plays again or the attempts end. */
        const val EXTRA_ATTEMPTING = "rpm.reconnecting"

        /**
         * Whether a station has lost its stream: still meant to play, and holding an error, ended, under
         * an attempt ([attempting], from the session's extras), or buffering after it had already played
         * for a second. The last catches a stream that stalls, which waits out the read timeout in
         * buffering before any error arrives; a station tuned a moment ago buffers at position 0 and is
         * not taken for a drop. Read by the screen from the controller's copy of the player.
         */
        fun reconnecting(player: Player, attempting: Boolean): Boolean {
            if (player.currentMediaItem?.let { StationItems.uuidOf(it.mediaId) } == null || !player.playWhenReady) return false
            return isDown(player) || attempting ||
                (player.playbackState == Player.STATE_BUFFERING && player.currentPosition >= HEARD_MS)
        }

        /** How long a station must have played for a later buffering to count as a drop. */
        private const val HEARD_MS = 1_000L
    }
}

/**
 * Media3's retries for every load, except a live stream's: one quick retry instead of six, after which
 * the error reaches the player and [StationReconnect] takes over. With Media3's six retries the player
 * keeps buffering with no error for about 40 s. During that time the screen cannot tell a drop from a
 * slow start and says nothing.
 */
@OptIn(UnstableApi::class)
class LiveStreamRetries : DefaultLoadErrorHandlingPolicy() {
    override fun getMinimumLoadableRetryCount(dataType: Int): Int =
        if (dataType == C.DATA_TYPE_MEDIA_PROGRESSIVE_LIVE) 1 else super.getMinimumLoadableRetryCount(dataType)
}
