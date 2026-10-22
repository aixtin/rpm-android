package com.rpm.player.phone.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import com.rpm.player.core.playback.NowPlaying
import com.rpm.player.core.playback.StationItems

/**
 * A queue that has played to its end is put away, as the stop key puts it away: the iPhone's player stops itself when
 * nothing is behind the record that ended (`Player.queueRanOut`). A player left standing over a finished record would
 * keep a strip at the foot of every screen and an entry on the lock screen, with nothing left to do. A station is not a
 * queue; its stream dropping is handled by its reconnecting.
 *
 * It is read when the controller connects too, for a queue that ran out while the app was not on screen.
 */
@Composable
fun PutAwayWhenPlayedOut(controller: Player?) {
    DisposableEffect(controller) {
        val player = controller ?: return@DisposableEffect onDispose {}
        fun check() {
            val item = player.currentMediaItem ?: return
            if (player.playbackState != Player.STATE_ENDED) return
            if (StationItems.uuidOf(item.mediaId) != null || player.hasNextMediaItem()) return
            player.clearMediaItems()
        }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) = check()
        }
        player.addListener(listener)
        check()
        onDispose { player.removeListener(listener) }
    }
}

/**
 * Calls [onOver] when the listening ends: the queue stopped or played out, so the player has nothing on it. It is told
 * whether the music came from the radio, so the tab can go back to where it came from. It reads the player's state, not
 * the stop key, because there is more than one way to reach that state (the iPhone's `isOver` and `wasTheRadio`).
 *
 * A controller that goes away (the app leaving the screen) is not an ending.
 */
@Composable
fun OnListeningEnded(controller: Player?, now: NowPlaying?, onOver: (wasTheRadio: Boolean) -> Unit) {
    val over by rememberUpdatedState(onOver)
    var last by remember { mutableStateOf<Pair<Player, NowPlaying>?>(null) }
    LaunchedEffect(controller, now) {
        if (controller != null && now != null) {
            last = controller to now
            return@LaunchedEffect
        }
        val was = last
        last = null
        if (controller != null && was != null && was.first === controller) over(was.second.station != null)
    }
}
