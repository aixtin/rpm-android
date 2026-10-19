package com.rpm.player.core.library

import com.rpm.player.core.cloud.CloudTrack
import java.text.Collator

/** What the All tracks listing on an artist's page groups its songs under, once it holds more than
 * [ArtistSongListing.GROUP_ABOVE]. One choice for every artist's page, as on the desktop and the iPhone. The names
 * are the values it is stored as. */
enum class ArtistTrackGrouping(val stored: String) {
    /** By the first letter of the title, A to Z, then # for the rest. The default. */
    Letter("letter"),

    /** By the track's year, newest first; songs with no year last. */
    Year("year");

    companion object {
        fun of(stored: String?): ArtistTrackGrouping? = entries.firstOrNull { it.stored == stored }
    }
}

/**
 * The songs under All tracks on an artist's page, in the order they are shown. Same rules as the iPhone's and
 * the desktop's `ArtistSongListing`.
 *
 * Up to [GROUP_ABOVE] songs are one group with no heading, in the order given. Past that they are grouped by the
 * title's first letter (A to Z then #, each group by title) or by year (newest first, no year last, each group in
 * the order given, which is record by record in track order).
 *
 * What is shown and what plays are one list: a row plays [songs] from where it stands, so the song after the one
 * tapped is the one under it on the screen.
 *
 * @param noYear the heading of the songs with no year.
 */
class ArtistSongListing(given: List<Pair<CloudTrack, CloudAlbum>>, val grouping: ArtistTrackGrouping, noYear: String) {

    class Group(val heading: String?, val songs: List<Pair<CloudTrack, CloudAlbum>>)

    val groups: List<Group> = when {
        given.isEmpty() -> emptyList()
        given.size <= GROUP_ABOVE -> listOf(Group(null, given))
        grouping == ArtistTrackGrouping.Letter -> byLetter(given)
        else -> byYear(given, noYear)
    }

    val songs: List<Pair<CloudTrack, CloudAlbum>> get() = groups.flatMap { it.songs }

    val isGrouped: Boolean get() = groups.firstOrNull()?.heading != null

    companion object {
        const val GROUP_ABOVE = 20

        private fun byLetter(given: List<Pair<CloudTrack, CloudAlbum>>): List<Group> {
            // Title order as a reader expects it: case, accents and punctuation are ignored, so "(I Can't Get No)
            // Satisfaction" sorts among the I's, the group ArtistWall.initial puts it in. A tie keeps the order given.
            val titles = Collator.getInstance()
            return given.withIndex()
                .groupBy { ArtistWall.initial(it.value.first.title) }
                .toSortedMap(compareBy { if (it == '#') 26 else it - 'A' })
                .map { (letter, songs) ->
                    val inOrder = songs.map { Triple(it.index, ArtistWall.searchKey(it.value.first.title), it.value) }
                        .sortedWith { a, b -> titles.compare(a.second, b.second).takeIf { it != 0 } ?: a.first.compareTo(b.first) }
                    Group(letter.toString(), inOrder.map { it.third })
                }
        }

        private fun byYear(given: List<Pair<CloudTrack, CloudAlbum>>, noYear: String): List<Group> =
            given.groupBy { (it.first.tags?.year ?: 0).coerceAtLeast(0) }
                .toSortedMap(compareBy<Int> { it == 0 }.thenByDescending { it })
                .map { (year, songs) -> Group(if (year > 0) year.toString() else noYear, songs) }
    }
}
