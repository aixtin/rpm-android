package com.rpm.player.core.library

import com.rpm.player.core.cloud.CloudTrack
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * The library tab's search as on the phone (the iPhone's `SongSearch`). The words are
 * looked for, lower cased, anywhere in a record's title or artist and a song's title or artist. The records the words
 * name and the songs they find are two answers, shown apart. Both walk the whole library, so both run off the main
 * thread and stop at the next record once the coroutine asking is cancelled (a new keystroke, or a new library).
 *
 * The TV searches with [LibrarySearch], which ranks its answers; the phone's search is the iPhone's.
 */
object SongSearch {
    /** One song the search found, and the record it is on, so a row draws the sleeve without looking for it. */
    data class SongHit(val track: CloudTrack, val album: CloudAlbum)

    /** The most songs one search returns. Somebody given three hundred rows needs more words, not a longer list. */
    const val CEILING = 300

    /** The most records one search returns: more than this and the words have named an artist, not a record. */
    const val RECORD_CEILING = 60

    /**
     * Every song that matches, up to [CEILING], in the order of [albums]: a song matches on its own title and artist
     * and on its record's title and artist, because a compilation names the two differently and somebody looking for
     * the record types the one on the spine.
     */
    suspend fun through(albums: List<CloudAlbum>, asked: String): List<SongHit> {
        val asking = asked.trim().lowercase()
        if (asking.isEmpty()) return emptyList()
        val found = ArrayList<SongHit>()
        for (album in albums) {
            currentCoroutineContext().ensureActive()
            // The record's two fields once for the record, not once for each of its songs.
            val sleeve = has(album.album, asking) || has(album.artist, asking)
            for (track in album.tracks) {
                if (sleeve || has(track.title, asking) || has(track.artist, asking)) {
                    found += SongHit(track, album)
                    if (found.size >= CEILING) return found
                }
            }
        }
        return found
    }

    /**
     * Every record whose title or artist matches, up to [RECORD_CEILING]. Not the songs' answer narrowed: a record
     * listed because one of its songs matched could not be told from a wrong answer, so the songs' titles are not
     * looked at.
     */
    suspend fun records(albums: List<CloudAlbum>, asked: String): List<CloudAlbum> {
        val asking = asked.trim().lowercase()
        if (asking.isEmpty()) return emptyList()
        val found = ArrayList<CloudAlbum>()
        for (album in albums) {
            currentCoroutineContext().ensureActive()
            if (!has(album.album, asking) && !has(album.artist, asking)) continue
            found += album
            if (found.size >= RECORD_CEILING) return found
        }
        return found
    }

    private fun has(field: String?, asking: String): Boolean =
        !field.isNullOrEmpty() && field.lowercase().contains(asking)
}
