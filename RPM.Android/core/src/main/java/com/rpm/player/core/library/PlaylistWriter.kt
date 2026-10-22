package com.rpm.player.core.library

import com.rpm.player.core.auth.AuthClient
import com.rpm.player.core.cloud.CloudClient
import com.rpm.player.core.cloud.CloudPlaylist
import com.rpm.player.core.cloud.PlaylistPut
import com.rpm.player.core.cloud.PlaylistWrite
import com.rpm.player.core.cloud.Shelf
import kotlinx.coroutines.CancellationException

/** Why a playlist was not written. Each has the sentence the iPhone shows for it (`PlaylistWriteError.userMessage`). */
enum class PlaylistRefusal {
    /** The list belongs to another account, which this one may not write to. */
    NotYours,

    /** The list was on screen, but the store no longer holds it. */
    Gone,

    /** A list the computer cut. Its id names nothing in the store, and the computer replaces it on its next run. */
    Generated,

    /** Refused twice in a row: another device keeps writing the same list. */
    Busy,

    /** The list names songs this phone cannot see. A write sends the whole order, so it would drop them. */
    Unseen,

    /** No session, no network, or the store said no: the general sentence for the write that failed. */
    Failed,
}

/** What adding songs to a playlist came to. */
sealed interface SongsAdded {
    /** [count] songs were put at the end of the list. */
    data class Added(val count: Int) : SongsAdded

    /** Every song was already in the list, so nothing was written. A second copy is never added unasked. */
    data object AlreadyIn : SongsAdded

    data class Refused(val why: PlaylistRefusal) : SongsAdded
}

/**
 * Makes a playlist, adds songs to one, and changes the order of one, on the person's own shelf. It matches the
 * iPhone's `PlaylistWriter` (RPMLibrary/PlaylistWriter.swift) and its callers in `LibrarySession`.
 *
 * **Every write names the copy it replaces** (`ifUnchangedSince`). When another device wrote first, the store
 * refuses and hands back what it holds, and the two are merged ([PlaylistMerge]) and written once more. A second
 * refusal is [PlaylistRefusal.Busy], so the phone never keeps writing against somebody else's edit.
 *
 * Each write the store takes is recorded with the repository ([LibraryRepository.edited]), which lays it over the
 * collection on screen, so the page shows it at once and a load that started before it does not take it back.
 */
class PlaylistWriter(
    private val cloud: CloudClient,
    private val auth: AuthClient,
    private val library: LibraryRepository,
    private val clock: () -> Long,
    private val mint: () -> String = { java.util.UUID.randomUUID().toString().lowercase() },
) {
    /**
     * Makes a playlist called [name] in [folder] (outermost first; empty is the top of the tree) holding [rpmIds]
     * in that order. It may hold none, to be filled later. It goes under a new id and is written only if nothing is
     * stored there (`ifUnchangedSince` 0), so a clash is a failure and never somebody's playlist overwritten.
     *
     * @return null when it was made.
     */
    suspend fun make(name: String, folder: List<String>, rpmIds: List<String>): PlaylistRefusal? {
        val user = auth.session.value?.userId ?: return PlaylistRefusal.Failed
        // Trimmed, because a trailing space makes the name look like another playlist's.
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return PlaylistRefusal.Failed
        val id = mint()
        val made = clock()
        val put = orNull { cloud.putPlaylist(id, PlaylistWrite(trimmed, rpmIds, folder, ifUnchangedSince = 0)) }
        if (put !is PlaylistPut.Written) return PlaylistRefusal.Failed
        library.edited(user) {
            it.copy(
                playlists = it.playlists + (id to CloudPlaylist(id, trimmed, rpmIds, folder, put.updatedAt)),
                made = it.made + (id to made),
            )
        }
        return null
    }

    /**
     * Puts [rpmIds] at the end of [list], leaving any song the list already holds where it is: somebody who picks
     * twelve songs does not want a second copy of the three already there. The list is read from the store first,
     * not taken from the page, which can be several changes behind.
     */
    suspend fun add(list: TrackList, shelf: Shelf, rpmIds: List<String>): SongsAdded {
        if (shelf != Shelf.Own) return SongsAdded.Refused(PlaylistRefusal.NotYours)
        if (list.generated) return SongsAdded.Refused(PlaylistRefusal.Generated)
        val user = auth.session.value?.userId ?: return SongsAdded.Refused(PlaylistRefusal.Failed)
        val stored = orNull { cloud.playlists(Shelf.Own) } ?: return SongsAdded.Refused(PlaylistRefusal.Failed)
        var current = stored.firstOrNull { it.playlistId == list.storeId } ?: return SongsAdded.Refused(PlaylistRefusal.Gone)
        repeat(2) {
            val have = current.trackIds.toHashSet()
            // Each song once, even when it was picked twice.
            val added = rpmIds.filter { have.add(it) }
            if (added.isEmpty()) return SongsAdded.AlreadyIn
            val wanted = current.trackIds + added
            // No precondition where the store never gave a stamp: one made up would refuse every write.
            val write = PlaylistWrite(current.name, wanted, current.folder, current.updatedAt.takeIf { it > 0 })
            when (val put = orNull { cloud.putPlaylist(current.playlistId, write) } ?: return SongsAdded.Refused(PlaylistRefusal.Failed)) {
                is PlaylistPut.Written -> {
                    record(user, current.copy(trackIds = wanted, updatedAt = put.updatedAt))
                    return SongsAdded.Added(added.size)
                }
                // Another device wrote first. Adding to the end merges with anything, so the songs are added again to
                // the copy it stored: the same answer as a three-way merge, without one.
                is PlaylistPut.Conflict -> current = put.stored ?: return SongsAdded.Refused(PlaylistRefusal.Busy)
            }
        }
        return SongsAdded.Refused(PlaylistRefusal.Busy)
    }

    /**
     * Writes [order] (every song of [list], by id) as [list]'s running order. The whole list is sent, because that
     * is the only form the store takes, so a list with songs this phone cannot see is refused rather than shortened.
     * The name and the folder always come from the store: a phone cannot change either.
     *
     * @return null when it was written.
     */
    suspend fun reorder(list: TrackList, shelf: Shelf, order: List<String>): PlaylistRefusal? {
        if (shelf != Shelf.Own) return PlaylistRefusal.NotYours
        if (list.generated) return PlaylistRefusal.Generated
        if (list.missing > 0) return PlaylistRefusal.Unseen
        val user = auth.session.value?.userId ?: return PlaylistRefusal.Failed
        val name = (list.name as? ListName.Text)?.text ?: return PlaylistRefusal.Failed
        var wanted = CloudPlaylist(list.storeId, name, order, list.folder, 0)
        var ancestor = list.tracks.map { it.rpmId }
        var stamp = list.stamp
        repeat(2) {
            val write = PlaylistWrite(wanted.name, wanted.trackIds, wanted.folder, stamp.takeIf { it > 0 })
            when (val put = orNull { cloud.putPlaylist(wanted.playlistId, write) } ?: return PlaylistRefusal.Failed) {
                is PlaylistPut.Written -> {
                    record(user, wanted.copy(updatedAt = put.updatedAt))
                    return null
                }
                is PlaylistPut.Conflict -> {
                    // Refused without saying what it holds: there is nothing to merge against, and writing anyway
                    // would undo the other device's change.
                    val stored = put.stored ?: return PlaylistRefusal.Busy
                    wanted = CloudPlaylist(stored.playlistId, stored.name, PlaylistMerge.songs(ancestor, wanted.trackIds, stored.trackIds), stored.folder, 0)
                    ancestor = stored.trackIds
                    stamp = stored.updatedAt
                }
            }
        }
        return PlaylistRefusal.Busy
    }

    private fun record(user: String, playlist: CloudPlaylist) {
        library.edited(user) { it.copy(playlists = it.playlists + (playlist.playlistId to playlist)) }
    }

    private suspend fun <T> orNull(call: suspend () -> T): T? = try {
        call()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
