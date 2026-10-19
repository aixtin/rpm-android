package com.rpm.player.core.playback

import com.rpm.player.core.cloud.Shelf

/**
 * A cloud track as the queue names it: the shelf it is on and its id. The shelf travels in the media
 * id, `own:<rpmId>` or `shared:<owner id>:<rpmId>`, because the media id is the one field that survives
 * the controller, the session and the saved queue. Signing, the source choice and the queue's covers
 * read the shelf from it.
 */
data class TrackRef(val shelf: Shelf, val rpmId: String) {
    val mediaId: String
        get() = when (shelf) {
            Shelf.Own -> OWN + rpmId
            is Shelf.Borrowed -> SHARED + shelf.ownerId + ":" + rpmId
        }

    companion object {
        private const val OWN = "own:"
        private const val SHARED = "shared:"

        /** The track a media id names, or null for anything else (a station, an empty id). */
        fun parse(mediaId: String): TrackRef? = when {
            mediaId.startsWith(OWN) -> mediaId.removePrefix(OWN).takeIf { it.isNotEmpty() }?.let { TrackRef(Shelf.Own, it) }
            mediaId.startsWith(SHARED) -> {
                val rest = mediaId.removePrefix(SHARED)
                val split = rest.lastIndexOf(':')
                if (split <= 0 || split == rest.lastIndex) null else TrackRef(Shelf.Borrowed(rest.substring(0, split)), rest.substring(split + 1))
            }
            else -> null
        }
    }
}
