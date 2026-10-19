package com.rpm.player.core.library

import com.rpm.player.core.cloud.CloudTrack

/**
 * The songs the person marked as favourites, for the Favourite tracks row. Same rule as the iPhone's
 * `FavoriteMarks.marked(in:)`. The newest mark first; marks made at the same instant (most of those read off one
 * listing) by title, so the list does not shuffle itself between launches.
 */
object MarkedSongs {
    fun of(albums: List<CloudAlbum>): List<CloudTrack> {
        val titles = collator()
        return albums.asSequence()
            .flatMap { it.tracks }
            .filter { it.tags?.favorite == true }
            .sortedWith(compareByDescending<CloudTrack> { it.tags?.favoritedAt ?: 0L }.thenBy(titles) { it.title.orEmpty() })
            .toList()
    }
}
