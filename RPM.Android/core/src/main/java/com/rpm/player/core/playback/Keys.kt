package com.rpm.player.core.playback

import android.view.KeyEvent
import androidx.media3.common.Player

/** What a key asks the player to do. */
enum class PlayerAction { PLAY, PAUSE, STOP, NEXT, PREVIOUS, FORWARD, BACK }

/**
 * The remote's keys as player actions. Pure, so the rules can be tested without a device. The
 * Amiko remote has its own key map (see `forMediaKey`).
 */
object KeyRules {

    /**
     * A media key. [playing] is whether the player means to play (`playWhenReady`), not whether sound
     * is coming out, so a pause during buffering still pauses.
     *
     * `MEDIA_PAUSE` resumes when paused: the Amiko's remote sends its only play button as
     * `MEDIA_PAUSE`, and a pause that cannot be undone from the remote leaves the person stuck.
     * `MEDIA_CLOSE` is that remote's Stop.
     */
    fun forMediaKey(keyCode: Int, playing: Boolean): PlayerAction? = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> if (playing) PlayerAction.PAUSE else PlayerAction.PLAY
        KeyEvent.KEYCODE_MEDIA_PLAY -> PlayerAction.PLAY
        KeyEvent.KEYCODE_MEDIA_PAUSE -> if (playing) PlayerAction.PAUSE else PlayerAction.PLAY
        KeyEvent.KEYCODE_MEDIA_STOP, KeyEvent.KEYCODE_MEDIA_CLOSE -> PlayerAction.STOP
        KeyEvent.KEYCODE_MEDIA_NEXT -> PlayerAction.NEXT
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> PlayerAction.PREVIOUS
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> PlayerAction.FORWARD
        KeyEvent.KEYCODE_MEDIA_REWIND -> PlayerAction.BACK
        else -> null
    }

    /**
     * An arrow on the player screen while its controls are hidden: Left and Right go 10 seconds back
     * and forward, and a held arrow keeps seeking, every repeat a step. Null for every other key, which
     * the screen handles itself.
     *
     * OK is not an action here. It used to pause, and a person who pressed it only to see the controls
     * stopped the music; now it only brings the controls up ([raisesControls]), and pausing is the play
     * key's job, or the remote's own play/pause key ([forMediaKey]), which works on every screen.
     */
    fun forHiddenControls(keyCode: Int): PlayerAction? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> PlayerAction.BACK
        KeyEvent.KEYCODE_DPAD_RIGHT -> PlayerAction.FORWARD
        else -> null
    }

    /** Whether a key on the player screen with its controls hidden brings them up (with the play key
     * focused): OK, Up and Down. It only shows them; nothing is played or paused. */
    fun raisesControls(keyCode: Int): Boolean =
        isOk(keyCode) || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN

    /** OK, as the remotes send it: the d-pad's centre, or Enter from a keyboard or a remote that acts as one. */
    fun isOk(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
}

/**
 * Left and Right on the player's focused seek bar, by the desktop's rule (PegasusChrome.Adjust): a
 * press moves the playhead 10 seconds, as with the controls hidden. A held key moves it at least
 * [HELD_LEAST_MS] a second, or fast enough to cross the whole track in [HELD_CROSS_S] seconds where
 * that is faster. Pure, so the rule can be tested without a device. The bar moves a preview by it
 * and seeks once, when the key comes up (the TV's `SeekKeys`).
 */
object SeekSteps {
    /** While the key is held, the playhead moves at least this far a second. */
    const val HELD_LEAST_MS = 30_000L

    /** Or fast enough to cross the whole track in this many seconds, if that is faster. */
    const val HELD_CROSS_S = 6L

    /** The longest gap between two steps that a held step counts. Android's first repeat comes 400 ms
     * after the press. If it were counted whole, it would jump 12 seconds or more at once. */
    const val HELD_GAP_MS = 250L

    /**
     * Where one key moves the playhead from [fromMs], kept within 0 and [durationMs]. [way] is -1 for
     * Left and 1 for Right; [repeat] is whether the key is held and this is one of its repeats; [sinceMs]
     * is the time since the previous step, on the key's own clock.
     */
    fun next(fromMs: Long, way: Int, repeat: Boolean, sinceMs: Long, durationMs: Long): Long {
        val by = if (repeat) {
            maxOf(HELD_LEAST_MS, durationMs / HELD_CROSS_S) * sinceMs.coerceIn(0, HELD_GAP_MS) / 1_000
        } else {
            PlaybackService.SEEK_STEP_MS
        }
        return (fromMs + way * by).coerceIn(0, durationMs)
    }
}

/** Carries out an action on a player: the session's own player, or a controller of it. */
fun Player.perform(action: PlayerAction) {
    when (action) {
        PlayerAction.PLAY -> {
            if (playbackState == Player.STATE_IDLE) prepare()
            if (playbackState == Player.STATE_ENDED) seekToDefaultPosition(0)
            play()
        }
        PlayerAction.PAUSE -> pause()
        // A stop that keeps the queue: paused at the start of the current track, so the next play
        // starts it again.
        PlayerAction.STOP -> {
            pause()
            seekTo(currentMediaItemIndex, 0)
        }
        PlayerAction.NEXT -> if (hasNextMediaItem()) seekToNextMediaItem()
        PlayerAction.PREVIOUS -> seekToPrevious()
        PlayerAction.FORWARD -> seekForward()
        PlayerAction.BACK -> seekBack()
    }
}

/** Whether the player means to play, which is what the toggles decide on. */
val Player.meansToPlay: Boolean
    get() = playWhenReady && playbackState != Player.STATE_ENDED && playbackState != Player.STATE_IDLE
