package com.rpm.player.core.library

import com.rpm.player.core.cloud.CloudTrack
import com.rpm.player.core.papers.TrackPlace

/**
 * A genre's popular songs, from how often each has been listened to across everybody's listening, over the whole
 * genre rather than one act (it follows [ArtistPopular]'s rules). The world's plays are asked per record,
 * so a genre of hundreds of records is asked about its [ASKED] most played here; and so one famous act does not
 * fill the list, each act keeps at most [PER_ACT] songs in it.
 */
object GenrePopular {
    /** How many of the genre's records are asked about. */
    const val ASKED = 40

    /** The most songs one act (a record's album artist) has in the list. */
    const val PER_ACT = 3

    /** The records to ask about: the ones played most here first, then in the genre's order. */
    fun records(albums: List<CloudAlbum>, playsOf: (CloudAlbum) -> Int): List<CloudAlbum> =
        albums.sortedByDescending(playsOf).take(ASKED)

    /** Every song of [records], each with its record: what [ranked] ranks. */
    fun songs(records: List<CloudAlbum>): List<Pair<CloudTrack, CloudAlbum>> =
        records.flatMap { album -> album.tracks.map { it to album } }

    /** The songs by listens, most first, [ArtistPopular.ranked]'s way (one per title, none under its fewest), with
     * no act past [PER_ACT], at most [most]. */
    fun ranked(most: Int, songs: List<Pair<CloudTrack, CloudAlbum>>, plays: Map<String, Map<TrackPlace, Long>>): List<Pair<CloudTrack, CloudAlbum>> {
        val perAct = HashMap<String, Int>()
        val kept = ArtistPopular.ranked(Int.MAX_VALUE, songs, plays).filter { (_, album) ->
            val act = NameMatch.squash(album.artist)
            val had = perAct[act] ?: 0
            if (had >= PER_ACT) false else { perAct[act] = had + 1; true }
        }.take(most)
        return if (kept.size >= ArtistPopular.FEWEST) kept else emptyList()
    }
}
