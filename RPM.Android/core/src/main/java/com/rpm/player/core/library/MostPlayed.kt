package com.rpm.player.core.library

import com.rpm.player.core.cloud.CloudTrack

/**
 * The songs played most, from the play counts the collection carries. Same rule as the iPhone's `HomeSongs.mostPlayed`.
 * Home's quick selection and a genre's page both use it.
 */
object MostPlayed {
    /**
     * The songs among [tracks] with at least one play, most played first, then the most recently played, then by
     * id, so two reads with nothing played in between give the same order. At most [count].
     */
    fun songs(tracks: Iterable<CloudTrack>, plays: Map<String, Plays>, count: Int): List<CloudTrack> {
        if (count <= 0 || plays.isEmpty()) return emptyList()
        return tracks
            .filter { (plays[it.rpmId]?.count ?: 0) > 0 }
            .distinctBy { it.rpmId }
            .sortedWith(
                compareByDescending<CloudTrack> { plays[it.rpmId]?.count ?: 0 }
                    .thenByDescending { plays[it.rpmId]?.lastPlayedAt ?: 0 }
                    .thenBy { it.rpmId },
            )
            .take(count)
    }
}
