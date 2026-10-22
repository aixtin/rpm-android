package com.rpm.player.core.playback

import java.io.File

/**
 * The tracks this device holds a file for: the phone's downloads. The player asks before it signs anything, so a
 * downloaded record plays from the file, with or without a network, as the iPhone's player reads its `DownloadBook`
 * first.
 *
 * Asked on a loader thread, at every open of a track, so it may touch the disk.
 */
fun interface LocalCopies {
    /** The file that holds [rpmId], or null when this device has none. */
    fun fileOf(rpmId: String): File?

    /**
     * Whether this device holds [rpmId], asked on the main thread by the player's offline rule ([OfflinePlayback]) for
     * every track of a queue it looks along. A book that keeps its list in memory answers without the disk.
     */
    fun holds(rpmId: String): Boolean = fileOf(rpmId) != null

    companion object {
        /** No downloads: the TV, which streams everything. */
        val None = LocalCopies { null }
    }
}
