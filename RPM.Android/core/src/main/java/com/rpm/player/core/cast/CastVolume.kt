package com.rpm.player.core.cast

/**
 * The volume exchange with a Cast device: what the room is set to, what this side asked for, and which of the
 * two to believe when they differ. Ported from `RPM.Core/Audio/CastVolume.cs` through the phone's
 * `RPMCast/CastVolume.swift`. While casting there is one volume control and it is the speaker's. Its own
 * buttons, the Home app and voice commands can change it too.
 *
 * Two rules. **Echo:** every level that is sent comes back as a status, and so does the level the device had a
 * moment earlier. A report that differs from the last level asked for counts only after [ECHO_MS] with nothing
 * sent. **Coalescing:** a held volume key makes changes faster than a device answers them, and two sends in
 * flight can arrive in any order. [next] returns only the latest wanted level, one at a time.
 *
 * No side effects, main thread only; the clock is passed in. The controller sends what [next] returns and asks
 * again after [EVERY_MS].
 */
class CastVolume(private val millis: () -> Long) {
    /** The volume, 0 to 100, as far as this side knows. */
    var level = 100
        private set

    /** Whether the room's own level has been read. Until it has, a volume key steps from an unknown level. */
    var known = false
        private set

    private var wanted = -1
    private var sentAt = Long.MIN_VALUE / 2
    private var pending = false

    /** The person set the volume to [volume]. Returns true if a send should start now (none is running). */
    fun set(volume: Int): Boolean {
        level = volume.coerceIn(0, 100)
        wanted = level
        sentAt = millis()
        if (pending) return false
        pending = true
        return true
    }

    /** The level to send next, or null if the last level sent is still the wanted one. Call it to start, and again
     * after each send has finished and [EVERY_MS] has passed. */
    fun next(lastSent: Int?): Int? {
        if (wanted < 0 || wanted == lastSent) {
            pending = false
            return null
        }
        return wanted
    }

    /**
     * The device reported its volume as [deviceLevel] (0 to 1). Returns the level to show, or null if there is
     * nothing to do: the device gave no level, the report is our own echo, the level has not really changed, or
     * the report arrived before the last send had settled.
     */
    fun adopt(deviceLevel: Double?): Int? {
        deviceLevel ?: return null
        val volume = Math.round(deviceLevel.coerceIn(0.0, 1.0) * 100).toInt()
        val quiet = millis() - sentAt >= ECHO_MS
        if (wanted >= 0 && volume != wanted && !quiet) return null
        val first = !known
        known = true
        // A difference of one point is the device's own rounding, not a change by a person.
        if (!first && kotlin.math.abs(volume - level) <= 1) return null
        level = volume
        return volume
    }

    companion object {
        /** How often a change is sent while the level is still changing. */
        const val EVERY_MS = 120L

        /** For this long after our own change, a different report is treated as our own echo. */
        const val ECHO_MS = 2_000L

        /** The change for one press of a volume key, in percent. */
        const val STEP = 5
    }
}
