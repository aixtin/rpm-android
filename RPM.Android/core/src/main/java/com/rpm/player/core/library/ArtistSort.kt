package com.rpm.player.core.library

import java.text.Collator

/** The orders the artists list can be put in. They match the iOS app's `ArtistSort.swift`. */
enum class ArtistOrdering {
    /** Alphabetical, the '#' bucket last. */
    Name,

    /** Most records of their own first. */
    Albums,

    /** Most songs first. */
    Tracks,

    /** The artist whose newest own record arrived most recently first; undated artists last. */
    Added,
}

/** Puts a wall of artists in an order. Every order falls back to the name order, which breaks its own ties
 * by an ordinal comparison, so two builds over one collection always give the same order. */
object ArtistSort {
    /** The wall in [ordering]. [albums] is every album by key; the Added order reads arrival out of it. */
    fun by(artists: List<ArtistWallItem>, ordering: ArtistOrdering, albums: Map<String, CloudAlbum>): List<ArtistWallItem> {
        val names = collator()
        val seats = artists.map { Seat(it, it.initial == '#', if (ordering == ArtistOrdering.Added) arrival(it, albums) else 0) }
        val byOrder: Comparator<Seat> = when (ordering) {
            ArtistOrdering.Albums -> compareByDescending { it.artist.own.size }
            ArtistOrdering.Tracks -> compareByDescending { it.artist.tracks }
            ArtistOrdering.Added -> compareByDescending { it.arrived }
            ArtistOrdering.Name -> Comparator { _, _ -> 0 }
        }
        return seats.sortedWith(byOrder.then(byName(names))).map { it.artist }
    }

    /** The wall in name order, the '#' bucket last. */
    fun byName(artists: List<ArtistWallItem>): List<ArtistWallItem> =
        artists.map { Seat(it, it.initial == '#', 0) }.sortedWith(byName(collator())).map { it.artist }

    /** When an artist arrived: the newest addedAt among their own records, 0 where none can be dated. */
    fun arrival(artist: ArtistWallItem, albums: Map<String, CloudAlbum>): Long =
        artist.own.mapNotNull { albums[it] }.maxOfOrNull { album -> album.tracks.maxOfOrNull { it.addedAt } ?: 0L } ?: 0L

    private class Seat(val artist: ArtistWallItem, val last: Boolean, val arrived: Long)

    /** By the letter each act files under ([ArtistWall.initial]), A to Z with '#' last, then by name within it. The
     * letter comes first because the name alone does not keep the letters together: the initial skips a name's
     * leading punctuation, the collator does not, so "(hed) p.e." files under H but sorts ahead of every A, and the
     * ladder down the side would run H, A, B... */
    private fun byName(names: Collator): Comparator<Seat> =
        compareBy<Seat> { it.last }
            .thenBy { it.artist.initial }
            .then { a, b -> names.compare(a.artist.name, b.artist.name) }
            .thenBy { it.artist.name }

    /** Case-insensitive, accent-sensitive, in the device's language. */
    private fun collator(): Collator = Collator.getInstance().apply { strength = Collator.SECONDARY }
}
