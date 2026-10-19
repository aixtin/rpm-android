package com.rpm.player.core.library

import com.rpm.player.core.cloud.CloudTrack
import java.text.Collator

/**
 * An artist's records sorted onto the shelves their page shows, and every song of theirs. The rule is the
 * same as the iOS app's `ArtistShelves.swift` and the desktop's `RPM.Core/Library/ArtistShelves.cs`.
 *
 * One record, one shelf; where a name answers more than once the order decides: single, then live, then
 * soundtrack, then compilation. Appearances are not split by kind. Each shelf is newest year first,
 * undated last, then by name.
 */
class ArtistShelves(
    val records: List<CloudAlbum>,
    val together: List<CloudAlbum>,
    val live: List<CloudAlbum>,
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

            val singles = own.filter { AlbumKind.isSingle(it.album) }
            val live = own.filter { AlbumKind.isLive(it.album) && !AlbumKind.isSingle(it.album) }
            val soundtracks = own.filter {
                AlbumKind.isSoundtrack(it.album) && !AlbumKind.isSingle(it.album) && !AlbumKind.isLive(it.album)
            }
            val compilations = own.filter {
                AlbumKind.isCompilation(it.album) && !AlbumKind.isSingle(it.album) && !AlbumKind.isLive(it.album) &&
                    !AlbumKind.isSoundtrack(it.album)
            }
            val records = newestFirst(found(artist.albums).filter(::isOrdinary))
            val together = newestFirst(found(artist.together).filter(::isOrdinary))
            val appearances = newestFirst(found(artist.appearances))

            val inPageOrder = records + together + newestFirst(live) + newestFirst(soundtracks) + newestFirst(compilations) + newestFirst(singles)
            val songs = inPageOrder.flatMap { album -> album.tracks.map { it to album } } +
                appearances.flatMap { album -> album.tracks.filter { ArtistWall.names(it.artist, artist.name) }.map { it to album } }

            return ArtistShelves(
                records, together, newestFirst(live), newestFirst(soundtracks), newestFirst(compilations), newestFirst(singles),
                appearances, songs,
            )
        }

        private fun isOrdinary(album: CloudAlbum): Boolean =
            !AlbumKind.isLive(album.album) && !AlbumKind.isSingle(album.album) &&
                !AlbumKind.isSoundtrack(album.album) && !AlbumKind.isCompilation(album.album)

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
