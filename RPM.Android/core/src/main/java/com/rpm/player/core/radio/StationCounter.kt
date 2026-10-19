package com.rpm.player.core.radio

/**
 * Counts how long a station is actually heard, as the iOS app's `StationCounter` does. It is read
 * once a second with the station playing now (or none) and whether it is playing. A part is handed out
 * every [HAND_OUT_EVERY] seconds heard and when the listen ends. Nothing is handed out until
 * [StationListens.NOTICED_SECONDS] have been heard, and only the first part of a listen is a new tune.
 *
 * A reading for a different station hands out the old station's part first, then starts the new one, so
 * the old listen is never written against the new station.
 * Not thread-safe: one caller, on one thread.
 */
class StationCounter(private val clockMs: () -> Long) {

    data class Heard(val station: RadioStation, val seconds: Double, val newTune: Boolean)

    private var station: RadioStation? = null
    private var heard = 0.0
    private var handedOut = 0.0
    private var lastReadingMs: Long? = null

    fun read(now: RadioStation?, playing: Boolean): Heard? {
        val at = clockMs()
        val last = lastReadingMs
        lastReadingMs = at
        if (!now?.uuid.equals(station?.uuid, ignoreCase = true)) {
            val finished = handOut()
            station = now
            heard = 0.0
            handedOut = 0.0
            return finished
        }
        if (station == null || !playing || last == null) return null
        val step = (at - last) / 1000.0
        // A step longer than this is a stall or a sleep, not listening.
        if (step > 0 && step <= LONGEST_STEP) heard += step
        return if (heard - handedOut >= HAND_OUT_EVERY) handOut() else null
    }

    /** What was heard since the last part, once the listen has been heard long enough to count. */
    fun handOut(): Heard? {
        val current = station ?: return null
        if (heard < StationListens.NOTICED_SECONDS || heard <= handedOut) return null
        val part = Heard(current, heard - handedOut, newTune = handedOut == 0.0)
        handedOut = heard
        return part
    }

    companion object {
        const val HAND_OUT_EVERY = 300.0
        private const val LONGEST_STEP = 5.0
    }
}
