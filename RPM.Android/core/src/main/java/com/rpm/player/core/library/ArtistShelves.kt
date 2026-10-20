package com.rpm.player.core.library

import com.rpm.player.core.cloud.CloudTrack
import java.text.Collator

/**
 * An artist's records sorted onto the shelves their page shows, and every song of theirs. The rule is the
 * same as the iOS app's `ArtistShelves.swift` and the desktop's `RPM.Core/Library/ArtistShelves.cs`.
 *
 * One record, one shelf: the type the record's files carry, else what its name suggests ([ReleaseType.shelfOf]).
 * Appearances are not split by kind. Each shelf is newest year first, undated last, then by name.
 */
class ArtistShelves(
    val records: List<CloudAlbum>,
    val together: List<CloudAlbum>,
    val eps: List<CloudAlbum>,
    val live: List<CloudAlbum>,
    val mixtapes: List<CloudAlbum>,
    val soundtracks: List<CloudAlbum>,
    val compilations: List<CloudAlbum>,
    val singles: List<CloudAlbum>,
    val appearances: List<CloudAlbum>,
    /** Every song of theirs: their own records whole, in the order the page shows them, then the songs that
     * name them on somebody else's. */
    val songs: List<Pair<CloudTrack, CloudAlbum>>,
) {
    companion object {
        fun of(artist: ArtistWallItem, albums: Map<String, CloudAlbum>): ArtistShelves {
            fun found(keys: List<String>) = keys.mapNotNull { albums[it] }
            val own = found(artist.own)

            fun kind(type: ReleaseType) = newestFirst(own.filter { ReleaseType.shelfOf(it) == type })
            val eps = kind(ReleaseType.Ep)
            val live = kind(ReleaseType.Live)
            val mixtapes = kind(ReleaseType.Mixtape)
            val soundtracks = kind(ReleaseType.Soundtrack)
            val compilations = kind(ReleaseType.Compilation)
            val singles = kind(ReleaseType.Single)
            val records = newestFirst(found(artist.albums).filter { ReleaseType.shelfOf(it) == ReleaseType.Album })
            val together = newestFirst(found(artist.together).filter { ReleaseType.shelfOf(it) == ReleaseType.Album })
            val appearances = newestFirst(found(artist.appearances))

            // The desktop's ArtistShelves.OwnInPageOrder.
            val inPageOrder = records + together + eps + live + mixtapes + soundtracks + compilations + singles
            val songs = inPageOrder.flatMap { album -> album.tracks.map { it to album } } +
                appearances.flatMap { album -> album.tracks.filter { ArtistWall.names(it.artist, artist.name) }.map { it to album } }

            return ArtistShelves(records, together, eps, live, mixtapes, soundtracks, compilations, singles, appearances, songs)
        }

        private fun newestFirst(albums: List<CloudAlbum>): List<CloudAlbum> {
            val names = Collator.getInstance().apply { strength = Collator.SECONDARY }
            return albums.map { AlbumYears.yearOf(it.tracks) to it }
                .sortedWith(
                    compareByDescending<Pair<Int, CloudAlbum>> { it.first > 0 }
                        .thenByDescending { it.first }
                        .then { a, b -> names.compare(a.second.album, b.second.album) }
                        .thenBy { it.second.key },
                )
                .map { it.second }
        }
    }
}

/** A record's year: the one most of its tracks agree on, the earlier of a tie, 0 when none is tagged. Same
 * rule as `AlbumSort.yearOf` in the iOS app and `AlbumSort.YearFrom` on the desktop. */
object AlbumYears {
    fun yearOf(tracks: List<CloudTrack>): Int {
        val seen = HashMap<Int, Int>()
        for (track in tracks) {
            val year = track.tags?.year ?: continue
            if (year > 0) seen[year] = (seen[year] ?: 0) + 1
        }
        var best = 0
        var most = 0
        for ((year, count) in seen.entries.sortedBy { it.key }) {
            if (count > most || (count == most && year < best)) {
                best = year
                most = count
            }
        }
        return best
    }
}
