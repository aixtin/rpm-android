package com.rpm.player.core.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingTest {
    private val held = NowPlaying(
        mediaId = "t1", title = "Song", artist = "Artist", album = "Album", cover = null, disc = null, playing = true,
        advancing = true, positionMs = 10_000, durationMs = 200_000, failed = false, station = null, readAtMs = 1_000,
    )

    /** Half a second later the track is half a second on: what the carried position already says. */
    @Test fun aPositionThatMovedWithTheClockIsNotNews() {
        assertFalse(held.copy(positionMs = 10_500, readAtMs = 1_500).isNewsAfter(held))
        assertFalse(held.copy(positionMs = 10_540, readAtMs = 1_500).isNewsAfter(held))
    }

    /** A seek, or a stall while buffering, leaves the position away from where the clock carried it. */
    @Test fun aPositionAwayFromTheCarriedOneIsNews() {
        assertTrue(held.copy(positionMs = 60_000, readAtMs = 1_500).isNewsAfter(held))
        assertTrue(held.copy(positionMs = 10_000, readAtMs = 1_500 + POSITION_DRIFT_MS).isNewsAfter(held))
    }

    /** Waiting for data with the play button showing pause: the position stands still, and is not carried past it. */
    @Test fun aBufferingPositionIsNotCarried() {
        val waiting = held.copy(advancing = false)
        assertTrue(waiting.positionAt(5_000) == 10_000L)
        assertTrue(held.positionAt(5_000) == 14_000L)
    }

    @Test fun anythingElseChangedIsNews() {
        assertTrue(held.copy(playing = false, readAtMs = 1_500, positionMs = 10_500).isNewsAfter(held))
        assertTrue(held.copy(mediaId = "t2", readAtMs = 1_500, positionMs = 10_500).isNewsAfter(held))
    }
}
