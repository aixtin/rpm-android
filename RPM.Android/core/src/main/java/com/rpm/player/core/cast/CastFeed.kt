package com.rpm.player.core.cast

/** One stream: one address, one WAV header, one LOAD. A seek, a skip or a new sample rate starts a new stream. */
data class CastStream(
    /** Counts the streams in a session. A LOAD or a report about an older stream changes nothing. */
    val generation: Int,
    /** The name in the stream's address. A receiver that uses an older name is refused. */
    val id: String,
    val rate: Int,
)

/**
 * The audio of one casting session between the sink and the room: the ring, the stream being sent, and how far
 * the room has played. The pacing follows the phone's `RoomFeed`. The decoder may run [LEAD_S] seconds ahead of
 * the position that the **room reports**. Until the room has reported anything, the decoder may fill the ring
 * except for one second (the receiver's head start). When the room is paused, its position stops, the lead
 * fills and the decoder waits, so no timer is needed.
 *
 * Unlike the phone's version, nothing here blocks. [take] returns how much may go in now. The sink writes that
 * much and tells ExoPlayer to offer the rest again later. Apart from the ring it has no side effects, and the
 * clock is passed in, so the rules are tested on the JVM (`CastFeedTest`).
 *
 * Threads: [begin], [takenBack], [take], [wrote] and [heardFrames] are called by the sink, on ExoPlayer's
 * playback thread. [says], [paused] and [stalled] are called by the controller, on the main thread. Everything
 * is guarded by one lock.
 */
class CastFeed(
    val ring: PcmRing,
    private val nanos: () -> Long,
    private val listener: Listener,
    private val newId: () -> String = { java.util.UUID.randomUUID().toString().replace("-", "") },
) {
    interface Listener {
        /** Serve [stream] from now on, or nothing. Called **before** the ring is emptied, on the sink's
         * thread. The connection that reads the old stream must be gone before the audio of the next one
         * goes in. */
        fun serve(stream: CastStream?)

        /** A stream has begun and needs a LOAD. Called on the sink's thread. The controller passes it on to the
         * main thread. */
        fun began(stream: CastStream)
    }

    private val lock = Any()

    // Everything below is guarded by [lock].
    private var generation = 0
    private var changes = 0
    private var stream: CastStream? = null
    /** Frames handed over in this stream. */
    private var written = 0L
    /** What the room last reported as played, in frames of this stream. Null until the room reports. */
    private var reported: Long? = null
    /** When [reported] last changed, or play was paused or resumed. This is the clock of the stall check. A room
     * that was paused for an hour has not stalled the moment it resumes. */
    private var movedAt = 0L
    /** The position and time from which the estimate between reports starts: the last report, or the position
     * when play resumed. */
    private var guessFrom = 0L
    private var guessAt = 0L
    /** The furthest position that [heardFrames] has returned, so a late report never moves the position back. */
    private var handedOut = 0L
    private var isPaused = false

    /** The stream being sent, or null between a flush and the next buffer. */
    val current: CastStream? get() = synchronized(lock) { stream }

    /** How many times the stream has been cut or begun. The poll reads it before it asks, and drops the reply
     * if the number has changed: a cut made on the playback thread has not yet reached [RoomStream] on the main
     * thread. */
    val turns: Int get() = synchronized(lock) { changes }

    /** Whether the player is paused. The controller sets it from the player's `playWhenReady`. A paused room's
     * position does not move, which [stalled] would read as a room that has gone. Once told it is paused,
     * [stalled] waits. */
    var paused: Boolean
        get() = synchronized(lock) { isPaused }
        set(value) = synchronized(lock) {
            if (value == isPaused) return
            val at = nanos()
            // The estimate stops at the current position and starts again from there on resume, so it
            // does not run on while paused and does not jump when play resumes.
            if (reported != null) handedOut = maxOf(handedOut, gotTo(at))
            isPaused = value
            guessFrom = handedOut
            guessAt = at
            movedAt = at
        }

    /**
     * Begins a stream at [rate]. The order is: tell the server, empty the ring, reset the counters, then tell
     * the controller. The controller sends the LOAD once the ring holds the room's head start.
     */
    fun begin(rate: Int): CastStream {
        val next = synchronized(lock) {
            generation += 1
            changes += 1
            CastStream(generation, newId(), rate)
        }
        listener.serve(next)
        ring.empty(next.generation)
        synchronized(lock) {
            stream = next
            clearLocked()
        }
        listener.began(next)
        return next
    }

    /**
     * The stream is taken away from the room: a seek or a skip flushed the decoder. Nothing is served until the
     * next [begin], and the counters no longer describe anything real, so they are reset. If they were kept, a
     * written count that grows past a report that has stopped would look like a room that stopped answering.
     */
    fun takenBack() {
        listener.serve(null)
        ring.empty(0)
        synchronized(lock) {
            stream = null
            changes += 1
            clearLocked()
        }
    }

    /**
     * How many of [offered] frames may go into the stream now. Before the room has reported, it is as many as
     * fit in the ring except [RESERVE_S] seconds. After that, it is as many as keep the decoder within [LEAD_S] of
     * the room's report and also fit in the ring. Zero means "not now": the sink tells ExoPlayer to offer the
     * buffer again.
     */
    fun take(offered: Int): Int = synchronized(lock) {
        val now = stream ?: return 0
        val fit = ring.free / PcmConvert.OUT_FRAME_BYTES
        val room = when (val heard = reported) {
            null -> fit - (RESERVE_S * now.rate).toInt()
            else -> minOf(fit.toLong(), (LEAD_S * now.rate).toLong() - (written - heard)).toInt()
        }
        room.coerceIn(0, offered)
    }

    /** [frames] went into the ring. */
    fun wrote(frames: Int) = synchronized(lock) { written += frames }

    /**
     * The room reported that it is [seconds] into the stream of [generation]. Ignored for a stream that was
     * already replaced, because a report about the last stream holds the position the last record had reached.
     * The position only moves forward, because a position that moves back makes a seek bar jump.
     */
    fun says(generation: Int, seconds: Double) {
        synchronized(lock) {
            val now = stream ?: return
            if (now.generation != generation) return
            val frames = (maxOf(0.0, seconds) * now.rate).toLong()
            if (frames > (reported ?: -1)) {
                val at = nanos()
                reported = frames
                movedAt = at
                guessFrom = frames
                guessAt = at
            }
        }
    }

    /**
     * The frames the room has played in this stream, or null before it has reported. While playing, it is
     * estimated between reports with three limits: never past what was written, never backwards, and never more
     * than [GUESS_S] past the last report.
     */
    fun heardFrames(): Long? = synchronized(lock) {
        if (reported == null) return null
        handedOut = maxOf(handedOut, gotTo(nanos()))
        handedOut
    }

    /** Seconds into this stream by the room's clock. This is the anchor that a `meta` message carries.
     * 0 before the room has reported. */
    fun inTheStream(): Double = synchronized(lock) {
        val now = stream ?: return 0.0
        if (reported == null) return 0.0
        handedOut = maxOf(handedOut, gotTo(nanos()))
        handedOut.toDouble() / now.rate
    }

    /** Frames written in this stream. */
    val writtenFrames: Long get() = synchronized(lock) { written }

    /** Seconds of audio waiting in the ring for the room to fetch. */
    fun heldSeconds(): Double = synchronized(lock) {
        val now = stream ?: return 0.0
        ring.held.toDouble() / (now.rate * PcmConvert.OUT_FRAME_BYTES)
    }

    /** Whether the room has played everything written, within half a second. True for a stream with nothing
     * written. `isEnded` and a change of rate wait for this. */
    fun drained(): Boolean = synchronized(lock) {
        val now = stream ?: return true
        if (written == 0L) return true
        val heard = reported ?: return false
        heard >= written - now.rate / 2
    }

    /** Whether audio has been written that the room has not played yet. */
    fun pending(): Boolean = synchronized(lock) {
        if (stream == null || written == 0L) return false
        val heard = maxOf(handedOut, reported ?: 0)
        heard < written
    }

    /**
     * Whether the room has stopped taking audio. This is true when the room is not paused, it has reported before,
     * its position has not changed for [GONE_S] seconds, and at least one second of written audio is not played.
     * It is never true before the first report: the fetch check covers a room that never starts.
     */
    fun stalled(): Boolean = synchronized(lock) {
        val now = stream ?: return false
        val heard = reported ?: return false
        if (isPaused) return false
        if (written - heard < now.rate) return false
        nanos() - movedAt > GONE_S * 1_000_000_000L
    }

    /** The room's position, from the last report and the time since then. The caller must hold the lock. */
    private fun gotTo(at: Long): Long {
        val heard = reported ?: return 0
        if (isPaused) return heard
        val rate = stream?.rate ?: return heard
        val since = ((at - guessAt) / 1e9).coerceIn(0.0, GUESS_S)
        return minOf(maxOf(heard, guessFrom + (since * rate).toLong()), written)
    }

    private fun clearLocked() {
        written = 0
        reported = null
        movedAt = 0
        guessFrom = 0
        guessAt = 0
        handedOut = 0
    }

    companion object {
        /** How far the decoder may run ahead of the room's report, in seconds. Same as the phone: 8. */
        const val LEAD_S = 8.0

        /** The ring's size in seconds at the highest rate a stream has. Same as the phone: 6. */
        const val RING_S = 6

        /** Space kept free in the ring before the room has reported, so the ring holds five seconds of head start. */
        const val RESERVE_S = 1.0

        /** The longest time, in seconds, that the position is estimated after the last report. */
        const val GUESS_S = 1.0

        /** A playing room whose position has not changed for this many seconds has gone. Same as the phone: 30. */
        const val GONE_S = 30L

        /** The ring for a session: [RING_S] seconds of 16-bit stereo at 48 kHz. */
        fun ring(): PcmRing = PcmRing(RING_S * PcmConvert.HIGHEST_RATE * PcmConvert.OUT_FRAME_BYTES)
    }
}
