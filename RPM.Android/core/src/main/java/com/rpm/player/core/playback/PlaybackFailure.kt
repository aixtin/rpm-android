package com.rpm.player.core.playback

import androidx.annotation.StringRes
import com.rpm.player.core.R

/**
 * Why the player is standing still when somebody wanted it to play, as the iPhone's `PlaybackFailure` (RPMAudio). The
 * playback service holds it and publishes it in the session's extras ([EXTRA]), so the screens can say it.
 *
 * Only the failures this player can tell apart so far are named. The iPhone's others (a slow line, a store that
 * refused, a file the decoder rejects) are still shown by the screens as the player's error, in one sentence.
 *
 * @property said the sentence for it, the iPhone's `said`; null for [None], which is never shown.
 * @property blamesTheNetwork whether the network coming back would undo it, so the player carries on by itself then.
 */
enum class PlaybackFailure(@StringRes val said: Int?, val blamesTheNetwork: Boolean) {
    /** Nothing to say. */
    None(null, false),

    /** The phone has no network, and what was asked for is not on the phone. */
    Offline(R.string.Rpm_Playback_Offline, true),
    ;

    companion object {
        /** The session extra that carries the failure's name. Absent, as on the TV, means [None]. */
        const val EXTRA = "rpm.playback.failure"

        /** The failure an extra names; [None] for anything this build does not know. */
        fun of(name: String?): PlaybackFailure = entries.firstOrNull { it.name == name } ?: None
    }
}
