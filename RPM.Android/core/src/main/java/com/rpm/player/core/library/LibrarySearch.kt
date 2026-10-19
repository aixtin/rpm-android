package com.rpm.player.core.library

import com.rpm.player.core.cloud.CloudTrack

/** A track the search found, with the album the wall files it under. */
data class TrackHit(val track: CloudTrack, val album: CloudAlbum)

/** What one query found in the collection: each kind best first and cut to its most ([LibrarySearch]). */
data class LibraryHits(val artists: List<ArtistWallItem>, val albums: List<CloudAlbum>, val tracks: List<TrackHit>) {
    val isEmpty: Boolean get() = artists.isEmpty() && albums.isEmpty() && tracks.isEmpty()

    companion object {
        val NONE = LibraryHits(emptyList(), emptyList(), emptyList())
    }
}

/**
 * The search over one collection's artists, albums and tracks. Every name is folded once ([Taste.fold]: case,
 * accents and punctuation) when this is built, so a query only compares strings. [of] walks every track and
 * [find] every name, so both run off the main thread.
 *
 * A query matches where every word of it is in the folded text: an artist's name; an album's title and artist; a
 * track's title and artist. Within each kind, a name that starts with the whole query comes first, then a name
 * with a word that starts with the query's first word, then the rest, each group in the order the collection
 * lists them.
 */
class LibrarySearch private constructor(
    private val artists: List<Folded<ArtistWallItem>>,
    private val albums: List<Folded<CloudAlbum>>,
    private val tracks: List<Folded<TrackHit>>,
) {
    /** An item, its name folded, and all the text a query is looked for in. */
    private class Folded<T>(val item: T, val name: String, val text: String)

    fun find(query: String): LibraryHits {
        val folded = Taste.fold(query)
        if (folded.isEmpty()) return LibraryHits.NONE
        val words = folded.split(' ')
        return LibraryHits(
            artists = best(artists, folded, words, ARTISTS_MOST),
            albums = best(albums, folded, words, ALBUMS_MOST),
            tracks = best(tracks, folded, words, TRACKS_MOST),
        )
    }

    private fun <T> best(all: List<Folded<T>>, query: String, words: List<String>, most: Int): List<T> {
        val first = words.first()
        val starts = ArrayList<T>()
        val wordStarts = ArrayList<T>()
        val rest = ArrayList<T>()
        for (entry in all) {
            if (!words.all { it in entry.text }) continue
            when {
                entry.name.startsWith(query) -> starts += entry.item
                entry.name.startsWith(first) || entry.name.contains(" $first") -> wordStarts += entry.item
                else -> rest += entry.item
            }
            // Nothing found later can come before a full first group.
            if (starts.size >= most) break
        }
        return (starts + wordStarts + rest).take(most)
    }

    companion object {
        const val ARTISTS_MOST = 10
        const val ALBUMS_MOST = 20
        const val TRACKS_MOST = 30

        fun of(albums: List<CloudAlbum>, artists: List<ArtistWallItem>): LibrarySearch = LibrarySearch(
            artists = artists.map { artist -> Taste.fold(artist.name).let { Folded(artist, it, it) } },
            albums = albums.map { album -> Taste.fold(album.album).let { Folded(album, it, it + " " + Taste.fold(album.artist)) } },
            tracks = albums.flatMap { album ->
                album.tracks.map { track ->
                    Taste.fold(track.title).let { Folded(TrackHit(track, album), it, it + " " + Taste.fold(track.artist)) }
                }
            },
        )
    }
}
