package com.rpm.player.tv.ui

import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Whether the screens are put away so that only the room shows. When the remote has been left alone for
 * the number of minutes in the settings ([PlayerSettings.screensaverMinutes]), the screens fade out. The next
 * key brings them back as they were.
 *
 * The idle time is worked out from the time of the last key, not from a timer that a key cancels: [watch]
 * wakes when the time is up, reads the last key time again, and only then puts the screens away. A key that
 * arrives while the timer is running out therefore moves the time on before it is read, whichever of the two
 * the main thread handles first. Everything here runs on the main thread.
 */
@Stable
class Screensaver {
    /** True while the screens are put away (fading out or gone). */
    var shown by mutableStateOf(false)
        private set

    private var lastKeyAt = SystemClock.uptimeMillis()

    /**
     * A key went down (a repeat too). Returns true when the screens were put away: this press brings them back
     * and does nothing else, so the caller swallows it until it is let go.
     */
    fun keyDown(): Boolean {
        lastKeyAt = SystemClock.uptimeMillis()
        if (!shown) return false
        shown = false
        return true
    }

    /** The app came to the front. Time spent behind another app does not count as idle time here. */
    fun resumed() {
        lastKeyAt = SystemClock.uptimeMillis()
        shown = false
    }

    /** Puts the screens away each time [minutes] pass with no key, until cancelled. Does nothing at 0 (off). */
    suspend fun watch(minutes: Int) {
        if (minutes <= 0) return
        val wait = minutes * 60_000L
        while (true) {
            val left = lastKeyAt + wait - SystemClock.uptimeMillis()
            if (left > 0) {
                delay(left)
                continue
            }
            shown = true
            snapshotFlow { shown }.first { !it }
        }
    }
}
