package com.rpm.player.core.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What was listened to lately on this device, newest first: every track that played long enough to count as a play
 * ([TrackCounter]), with when. Kept in memory only, for as long as the app runs; no screen shows it yet.
 *
 * A mixtape reads it so it does not queue a song that was just heard ([SongRadioRuns]). A track heard twice is
 * listed twice, at both times.
 */
object ListeningHistory {
    /** One listen: the track and when it was counted (Unix seconds). */
    data class Listen(val ref: TrackRef, val at: Long)

    private val held = MutableStateFlow<List<Listen>>(emptyList())

    /** The listens, newest first, at most [MOST]. */
    val listens: StateFlow<List<Listen>> = held.asStateFlow()

    fun note(ref: TrackRef, at: Long) {
        held.update { (listOf(Listen(ref, at)) + it).take(MOST) }
    }

    /** The tracks of [shelf] heard in the last [count] listens. */
    fun recent(shelf: com.rpm.player.core.cloud.Shelf, count: Int = MIXTAPE_SPARES): Set<String> =
        held.value.asSequence().take(count).filter { it.ref.shelf == shelf }.mapTo(HashSet()) { it.ref.rpmId }

    /** How many listens are kept. */
    const val MOST = 500

    /** How many of the latest listens a mixtape leaves out. */
    const val MIXTAPE_SPARES = 60
}
