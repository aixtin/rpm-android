package com.rpm.player.core.library

import com.rpm.player.core.cloud.CloudTrack
import com.rpm.player.core.papers.TrackPlace

/**
 * An artist's popular songs, from how often each has been played across all listening. Same rules as the iOS
 * app's `ArtistPopular`. One song per title, at most what the screen asks for ([MOST] on the phone; the TV shows
 * eight columns of four, like Quick selection on home) and at most half of the artist's songs, so a short
 * listing is not repeated above itself. Fewer than [FEWEST] songs is no section.
 */
object ArtistPopular {
    const val MOST = 12
    const val FEWEST = 4

    /** How many of their records are asked about. */
    const val ASKED = 40

    /** The records to ask about: those holding most of the artist's songs first, keeping the listing's order. */
    fun records(songs: List<Pair<CloudTrack, CloudAlbum>>): List<CloudAlbum> {
        val order = LinkedHashMap<String, CloudAlbum>()
        val count = HashMap<String, Int>()
        for ((_, album) in songs) {
            order.putIfAbsent(album.key, album)
            count[album.key] = (count[album.key] ?: 0) + 1
        }
        // A stable sort: records with the same number of songs keep the listing's order.
        return order.values.sortedByDescending { count[it.key] ?: 0 }.take(ASKED)
    }

    /** The songs by listens, most first, ties by id; one per title; at most [most] and half of [songs]; none under
     * [FEWEST]. A song with no track number, or on a record with no answer, is left out. */
    fun ranked(most: Int, songs: List<Pair<CloudTrack, CloudAlbum>>, plays: Map<String, Map<TrackPlace, Long>>): List<Pair<CloudTrack, CloudAlbum>> {
        val counted = songs.mapNotNull { song ->
            val (track, album) = song
            val position = track.tags?.trackNumber ?: 0
            if (position <= 0) return@mapNotNull null
            val record = plays[album.key] ?: return@mapNotNull null
            val disc = maxOf(1, track.tags?.discNumber ?: 0)
            val listens = record[TrackPlace(disc, position)]?.takeIf { it > 0 } ?: return@mapNotNull null
            song to listens
        }
        val ordered = counted.sortedWith(compareByDescending<Pair<Pair<CloudTrack, CloudAlbum>, Long>> { it.second }.thenBy { it.first.first.rpmId })
        val titles = HashSet<String>()
        val distinct = ordered.filter { titles.add(title(it.first.first)) }.map { it.first }
        val listed = distinct.take(minOf(most, songs.size / 2))
        return if (listed.size >= FEWEST) listed else emptyList()
    }

    /** A title as one song: lower case, with bracketed asides taken off, so "Hello (Live at the BBC)" is "hello". */
    fun title(track: CloudTrack): String {
        var text = track.title.orEmpty().lowercase()
        while (true) {
            val open = text.indexOfFirst { it == '(' || it == '[' }
            if (open < 0) break
            val end = text.indexOf(if (text[open] == '(') ')' else ']', open)
            if (end < 0) break
            text = text.removeRange(open, end + 1)
        }
        val plain = text.trim()
        return plain.ifEmpty { track.title.orEmpty().lowercase() }
    }
}
