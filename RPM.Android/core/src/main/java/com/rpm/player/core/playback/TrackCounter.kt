package com.rpm.player.core.playback

/**
 * Whether something that was played counts as having been listened to. It is a port of
 * `RPM.Core/Playback/Listen.cs`, as the iOS app's `Listen` is, and it must stay the same, because
 * what this TV counts is added to what the desktop and the iOS app count. A play counts after half
 * the track or four minutes, whichever comes first. A track under thirty seconds never counts.
 */
object Listen {
    const val SHORTEST_SECONDS = 30.0
    const val ENOUGH_SECONDS = 4 * 60.0

    /** Whether [heard] seconds of a track [length] seconds long is a play. A length of zero (not known)
     * never counts: guessing would count a quick skip past a track whose duration nobody knows. */
    fun counts(heard: Double, length: Double): Boolean {
        if (length < SHORTEST_SECONDS || heard <= 0) return false
        return heard >= minOf(length / 2, ENOUGH_SECONDS)
    }
}

/**
 * Turns readings of the player into plays. It is the TV's version of the iOS app's `PlayCounter` and
 * `ListenTally`. It is read once a second, on one thread, with the queued track playing now (or null
 * for a station or nothing), whether it is playing, where it is and how long it is.
 *
 * Heard time is how far the position moved while playing between two readings, so a seek adds nothing
 * and a stall adds nothing. A move back to the start of the same track (played again, or repeated)
 * ends the listen and starts another, as `ListenTally` treats a restart. A different track ends it
 * too. An ended listen is judged by [Listen.counts] and handed out as a play.
 */
class TrackCounter(private val clockSeconds: () -> Long) {

    /** A play that counted: the track and when the listen ended, in Unix seconds. */
    data class Counted(val ref: TrackRef, val at: Long)

    private var ref: TrackRef? = null
    private var heard = 0.0
    private var length = 0.0
    private var lastPosition: Double? = null

    /**
     * Takes one reading. [position] and [duration] are in seconds; [duration] zero when not known.
     * Returns the play the reading ended, if it ended one that counted.
     */
    fun read(now: TrackRef?, playing: Boolean, position: Double, duration: Double): Counted? {
        if (now != ref) {
            val ended = settle()
            ref = now
            heard = 0.0
            length = duration
            lastPosition = if (now != null) position else null
            return ended
        }
        if (now == null) return null
        if (duration > 0) length = duration
        val last = lastPosition
        lastPosition = position
        if (last == null) return null
        val moved = position - last
        // Back to the start of the same track: a new listen, and the old one is judged first.
        if (position < RESTART_WITHIN && last > RESTART_WITHIN && moved < 0) {
            val ended = settle()
            ref = now
            heard = 0.0
            return ended
        }
        // Playing on: the position moved by about the time between readings. A bigger jump is a seek,
        // and a backward move is a seek back. Neither is heard.
        if (playing && moved > 0 && moved <= LONGEST_STEP) heard += moved
        return null
    }

    /** Ends the listen in progress, as the service ends. Returns the play it made, if it counted. */
    fun settle(): Counted? {
        val current = ref ?: return null
        val counted = Listen.counts(heard, length)
        heard = 0.0
        return if (counted) Counted(current, clockSeconds()) else null
    }

    companion object {
        /** A position this close to the beginning, coming back from further on, is the track played again.
         * The iOS app's `ListenTally` uses half a second. Readings here are a second apart, so the first
         * reading after a restart can be up to a second in. */
        const val RESTART_WITHIN = 1.5

        /** Readings are a second apart. A move longer than this is a seek, not listening. */
        const val LONGEST_STEP = 5.0
    }
}
