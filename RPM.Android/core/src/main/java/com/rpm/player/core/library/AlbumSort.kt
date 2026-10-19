package com.rpm.player.core.library

import com.rpm.player.core.cloud.CloudTrack
import java.text.Collator

/** The orders a wall of albums can be put in. Matches `AlbumOrdering` in the iOS app. The names are the
 * stored values, so a choice written by one app can be read by another. */
enum class AlbumOrdering(val stored: String) {
    /** By whose record it is, then by its name. The order a shelf is in, and the default. */
    Artist("Artist"),

    /** By the record's own name, whoever made it. */
    Album("Album"),

    /** Most recently added first. */
    Added("Added"),

    /** Newest release first; records with no year at the end, not at 1900. */
    Year("Year"),

    /** Most played first; records never played at the end. */
    Plays("Plays");

    companion object {
        fun of(stored: String?): AlbumOrdering? = entries.firstOrNull { it.stored == stored }
    }
}

/** What an ordering needs to know about one album. [addedAt] is only compared, never read; [year] is 0 where
 * nothing on the record is tagged. */
data class AlbumFacts(val album: String, val artist: String, val addedAt: Long = 0, val year: Int = 0, val plays: Int = 0)

/**
 * Puts a wall of albums in the order that was asked for, with the same rules as `AlbumSort` in the iOS app. Every
 * order falls back to artist and then album, so a wall keeps the same order when two records tie. Untagged
 * years and unplayed records go to the end.
 */
object AlbumSort {
    /** [albums] in [ordering]. [factsOf] is read once per album rather than once per comparison. */
    fun <T> by(albums: List<T>, ordering: AlbumOrdering, factsOf: (T) -> AlbumFacts): List<T> {
        val names = Collator.getInstance().apply { strength = Collator.SECONDARY }
        fun name(a: String, b: String): Int = names.compare(a, b)

        val shelf = Comparator<Pair<T, AlbumFacts>> { a, b ->
            name(a.second.artist, b.second.artist).takeIf { it != 0 } ?: name(a.second.album, b.second.album)
        }
        val first: Comparator<Pair<T, AlbumFacts>> = when (ordering) {
            AlbumOrdering.Artist -> Comparator { _, _ -> 0 }
            AlbumOrdering.Album -> Comparator { a, b ->
                name(a.second.album, b.second.album).takeIf { it != 0 } ?: name(a.second.artist, b.second.artist)
            }
            AlbumOrdering.Added -> compareByDescending { it.second.addedAt }
            AlbumOrdering.Year -> compareBy<Pair<T, AlbumFacts>> { it.second.year == 0 }.thenByDescending { it.second.year }
            AlbumOrdering.Plays -> compareByDescending { it.second.plays }
        }
        return albums.map { it to factsOf(it) }.sortedWith(first.then(shelf)).map { it.first }
    }

    /** When an album arrived: the newest of its tracks, so a record that is still uploading counts as new. */
    fun addedOf(tracks: List<CloudTrack>): Long = tracks.maxOfOrNull { it.addedAt } ?: 0

    /** The record's year: the one most of its tracks agree on, the earlier one on a tie; 0 when nothing is tagged.
     * Most rather than first or highest, because a compilation carries each original's year on its row. */
    fun yearOf(tracks: List<CloudTrack>): Int {
        val seen = HashMap<Int, Int>()
        for (track in tracks) {
            val year = track.tags?.year?.takeIf { it > 0 } ?: continue
            seen[year] = (seen[year] ?: 0) + 1
        }
        var best = 0
        var most = 0
        for ((year, count) in seen.entries.sortedBy { it.key }) {
            if (count > most) {
                best = year
                most = count
            }
        }
        return best
    }

    /** The facts of one album as the wall orders it. Plays are not read here: the wall has no plays
     * order, and reading them means walking the whole plays map. */
    fun factsOf(album: CloudAlbum): AlbumFacts =
        AlbumFacts(album.album, album.artist, addedAt = addedOf(album.tracks), year = yearOf(album.tracks))
}
