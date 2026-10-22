package com.rpm.player.core.playback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * When the player pauses by itself: at a set time, or when the track playing ends. It is the iPhone's `SleepTimer`
 * (RPMAudio/SleepTimer.swift), with the same rules, and pure like it: [SleepClock] holds one, runs the clock and asks
 * it. Every question takes the time it is asked at, on the `elapsedRealtime` clock, which keeps counting while the
 * device sleeps, so a test can ask without waiting.
 *
 * Take care when changing when the clock asks it a question. A scheduled wake can arrive after the setting changed
 * (see [deadlineArrived] and [somebodyAskedForSound]).
 */
data class SleepTimer(val setting: Setting? = null) {

    /** What the timer is set to. */
    sealed interface Setting {
        /** Pause at this instant, in `elapsedRealtime` milliseconds. */
        data class At(val deadlineMs: Long) : Setting

        /** Pause when the track playing ends by itself. Skipping to another track moves the timer to that track; it
         * does not fire. */
        data object EndOfTrack : Setting
    }

    /** One of the options the menu offers. */
    sealed interface Choice {
        data class After(val ms: Long) : Choice
        data object EndOfTrack : Choice
    }

    val isOn: Boolean get() = setting != null

    /** Whether the timer waits for the track to end rather than for a time. */
    val endsWithTheTrack: Boolean get() = setting == Setting.EndOfTrack

    fun set(choice: Choice, nowMs: Long): SleepTimer = when (choice) {
        is Choice.After -> SleepTimer(Setting.At(nowMs + choice.ms))
        Choice.EndOfTrack -> SleepTimer(Setting.EndOfTrack)
    }

    /**
     * Adds time to a running timer. A timer waiting for the end of the track becomes a timed one, counted from the
     * moment the track would have ended: adding 5 minutes in the last minute of a song gives 6 minutes from now.
     *
     * [trackLeftMs] is how much of the track playing is left, or null when there is no track (a station). It has no
     * default: without it an end-of-track timer would be counted from now, and the rest of the song lost silently.
     */
    fun add(moreMs: Long, nowMs: Long, trackLeftMs: Long?): SleepTimer = when (val now = setting) {
        is Setting.At -> SleepTimer(Setting.At(maxOf(now.deadlineMs, nowMs) + moreMs))
        Setting.EndOfTrack -> SleepTimer(Setting.At(nowMs + (trackLeftMs ?: 0L) + moreMs))
        null -> this
    }

    fun turnOff(): SleepTimer = SleepTimer()

    /** Time left at [nowMs], or null when the timer is off. For an end-of-track timer it is the track's own time left,
     * [trackLeftMs], which is null when there is no track. */
    fun left(nowMs: Long, trackLeftMs: Long?): Long? = when (val now = setting) {
        is Setting.At -> maxOf(0L, now.deadlineMs - nowMs)
        Setting.EndOfTrack -> trackLeftMs
        null -> null
    }

    /**
     * The scheduled wake for the deadline arrived: whether to pause, and the timer after it (off when it does).
     * **The answer comes from the setting, not from the wake.** A wake scheduled before time was added can still
     * arrive, and then its deadline is no longer the current one; it does nothing.
     */
    fun deadlineArrived(nowMs: Long): Pair<Boolean, SleepTimer> {
        val at = setting as? Setting.At ?: return false to this
        return if (at.deadlineMs <= nowMs) true to SleepTimer() else false to this
    }

    /** The track playing ended by itself: whether to pause, and the timer after it (off when it does). */
    fun trackEnded(): Pair<Boolean, SleepTimer> =
        if (setting == Setting.EndOfTrack) true to SleepTimer() else false to this

    /**
     * Somebody asked for sound: play, a track, a station. **A deadline that has already passed is turned off**, so it
     * cannot pause what was just asked for: the timer can run out while the music is paused and the wake be held back,
     * and then play must not be silenced by the late wake.
     */
    fun somebodyAskedForSound(nowMs: Long): SleepTimer {
        val at = setting as? Setting.At ?: return this
        return if (at.deadlineMs <= nowMs) SleepTimer() else this
    }

    /** A station was tuned. A station has no end, so a timer waiting for one is turned off instead of waiting for ever.
     * A timed one carries on. */
    fun stationTuned(): SleepTimer = if (setting == Setting.EndOfTrack) SleepTimer() else this

    companion object {
        /** The options the menu offers, in order: 5, 15, 30 and 60 minutes. The drawer adds the same to a running
         * timer. */
        val DURATIONS_MS: List<Long> = listOf(5, 15, 30, 60).map { it * 60_000L }
    }
}

/**
 * The sleep timer as the app holds it: one [SleepTimer], the clock that wakes at its deadline, and the player it
 * pauses. The playback service attaches its player ([attach]), so the timer keeps counting while the screens are
 * gone, as the iPhone's lives in its `Player`. A screen sets it and reads [timer]. Everything here runs on the main
 * thread.
 *
 * **An end-of-track timer pauses where the track ends, before the next one is heard.** The player is told to pause at
 * the end of each item while the timer waits for one ([ExoPlayer.setPauseAtEndOfMediaItems]). When it does, the queue
 * is moved on to the next track, paused at its start, which is where the iPhone leaves it.
 */
@OptIn(UnstableApi::class)
class SleepClock(private val clock: () -> Long = SystemClock::elapsedRealtime) {
    private val _timer = MutableStateFlow(SleepTimer())

    /** The timer now. A screen reads it to show the menu's options or the drawer. */
    val timer: StateFlow<SleepTimer> = _timer.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private val wake = Runnable { deadlineArrived() }
    private var player: ExoPlayer? = null

    private val events = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady) {
                change { it.somebodyAskedForSound(clock()) }
                return
            }
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) endOfTrack()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val p = player ?: return
            // The queue was put away or replaced by a station. The end of a session ends the timer set for it, and a
            // station has no end of song to wait for.
            if (mediaItem == null) return change { it.turnOff() }
            if (StationItems.uuidOf(mediaItem.mediaId) != null) change { it.stationTuned() }
            // The other way a track can end: the pause at its end was asked for too late, and the queue went on into
            // the next one. It is paused where it stands, at its start.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                val (sleeps, next) = _timer.value.trackEnded()
                if (sleeps) {
                    put(next)
                    p.pause()
                }
            }
        }
    }

    /** The service's player, which the timer pauses. */
    fun attach(player: ExoPlayer) {
        this.player = player
        player.addListener(events)
        settle()
    }

    /** The service is ending, and the timer with it. */
    fun detach() {
        player?.removeListener(events)
        player?.pauseAtEndOfMediaItems = false
        player = null
        put(SleepTimer())
    }

    fun set(choice: SleepTimer.Choice) {
        if (choice == SleepTimer.Choice.EndOfTrack && onStation()) return
        change { it.set(choice, clock()) }
    }

    fun add(moreMs: Long) {
        val left = trackLeftMs()
        change { it.add(moreMs, clock(), left) }
    }

    fun turnOff() = change { it.turnOff() }

    /** Time left now, or null when the timer is off. For a timer waiting for the end of the track, the track's own
     * time left. */
    fun leftMs(): Long? = _timer.value.left(clock(), trackLeftMs())

    /** How much of the track playing is left, or null on a station and with nothing playing. */
    private fun trackLeftMs(): Long? {
        val p = player ?: return null
        if (p.currentMediaItem == null || onStation()) return null
        val duration = p.duration.takeIf { it > 0 } ?: p.currentMediaItem?.mediaMetadata?.durationMs ?: return null
        return maxOf(0L, duration - p.currentPosition)
    }

    private fun onStation(): Boolean = player?.currentMediaItem?.mediaId?.let { StationItems.uuidOf(it) } != null

    /** **The single entry point.** The change is written only when it differs, and then the wake and the player are
     * brought in line with it. */
    private fun change(edit: (SleepTimer) -> SleepTimer) {
        val next = edit(_timer.value)
        if (next != _timer.value) put(next)
    }

    private fun put(next: SleepTimer) {
        _timer.value = next
        settle()
    }

    /** The wake at the deadline, and the player's pause at the end of each item, as the timer now asks. */
    private fun settle() {
        handler.removeCallbacks(wake)
        val at = _timer.value.setting as? SleepTimer.Setting.At
        // The handler's clock stops while the device sleeps; while music plays it is kept awake, and a wake that
        // comes late to a paused player has nothing to pause. The setting decides on waking, not the wake.
        if (at != null) handler.postDelayed(wake, maxOf(0L, at.deadlineMs - clock()))
        player?.pauseAtEndOfMediaItems = _timer.value.endsWithTheTrack
    }

    private fun deadlineArrived() {
        val (sleeps, next) = _timer.value.deadlineArrived(clock())
        if (!sleeps) return settle()
        put(next)
        player?.pause()
    }

    /** The player paused at the end of the track, as the timer asked. The queue moves on to the next track and stays
     * paused there. */
    private fun endOfTrack() {
        val p = player ?: return
        val (sleeps, next) = _timer.value.trackEnded()
        if (!sleeps) return
        put(next)
        if (p.hasNextMediaItem()) p.seekToNextMediaItem()
    }
}
