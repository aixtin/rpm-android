package com.rpm.player.core.library

/**
 * The acts in this collection closest to one act, for the TV's artist page. Read from the
 * collection alone, as no service of the app's says who is related to whom: an act credited on a record with them
 * counts most ([TOGETHER] a record), then how much of their music is of the same genres (the share of each act's
 * records on each genre shelf, overlapped: 1 for two acts whose records are shelved alike, 0 for none in common).
 * A record on no shelf ([GenreWall.UNCLASSIFIED]) may still carry a genre tag no shelf answers to; the same overlap
 * of those tags as written (a record's most common one) counts too. And the lists they share, playlists and mood
 * lists, each counting for as few acts as it holds (a list of three says more than one of thirty): the share of the
 * act's own lists' weight the other is on. Most collections are tagged unevenly, and an untagged act is still
 * placed by the lists it is in. The closest of the three is taken.
 *
 * An act with nothing in common, by records or by at least [LEAST_ALIKE] of genre, is not related. Various Artists
 * is never one. At most [MOST], closest first; ties go to the act with more songs, then by name.
 */
object RelatedArtists {
    const val MOST = 12

    /** What one record made together counts, against a genre overlap of at most 1. */
    const val TOGETHER = 2.0

    /** The genre overlap an act needs without a record in common. */
    const val LEAST_ALIKE = 0.5

    fun of(artist: ArtistWallItem, walls: Walls, albums: Map<String, CloudAlbum>, lists: List<TrackList>): List<ArtistWallItem> {
        val shelfOf = HashMap<String, String>()
        for (genre in walls.genres) {
            if (genre.name == GenreWall.UNCLASSIFIED) continue
            for (key in genre.albums) shelfOf[key] = genre.name
        }
        val tagOf = HashMap<String, String>()
        for ((key, album) in albums) tag(album)?.let { tagOf[key] = it }
        val mine = (artist.own + artist.appearances).toHashSet()
        val myShares = shares(artist.own, shelfOf)
        val myTags = shares(artist.own, tagOf)
        val me = NameMatch.squash(artist.name)
        val listed = listShares(me, walls, albums, lists)
        return walls.artists.asSequence()
            .filter { NameMatch.squash(it.name) != me && !ArtistCredit.isVariousArtists(it.name) }
            .mapNotNull { other ->
                val together = (other.own + other.appearances).count { it in mine }
                val alike = maxOf(overlap(myShares, shares(other.own, shelfOf)), overlap(myTags, shares(other.own, tagOf)), listed[NameMatch.squash(other.name)] ?: 0.0)
                if (together == 0 && alike < LEAST_ALIKE) null else other to together * TOGETHER + alike
            }
            .sortedWith(compareByDescending<Pair<ArtistWallItem, Double>> { it.second }.thenByDescending { it.first.tracks }.thenBy { it.first.name })
            .take(MOST)
            .map { it.first }
            .toList()
    }

    /**
     * For every other act, the share of [me]'s list weight they are on: each list [me] is on weighs one over the
     * number of acts on it, and an act on all of them has 1. An act is on a list when one of their own records
     * holds one of its songs.
     */
    private fun listShares(me: String, walls: Walls, albums: Map<String, CloudAlbum>, lists: List<TrackList>): Map<String, Double> {
        val owners = HashMap<String, MutableSet<String>>()
        for (act in walls.artists) for (key in act.own) owners.getOrPut(key) { HashSet() }.add(NameMatch.squash(act.name))
        val albumOf = HashMap<String, String>()
        for (album in albums.values) for (track in album.tracks) albumOf[track.rpmId] = album.key
        var total = 0.0
        val shared = HashMap<String, Double>()
        for (list in lists) {
            val acts = list.tracks.flatMapTo(HashSet()) { track -> albumOf[track.rpmId]?.let { owners[it] }.orEmpty() }
            if (me !in acts || acts.size < 2) continue
            val weight = 1.0 / acts.size
            total += weight
            for (act in acts) if (act != me) shared[act] = (shared[act] ?: 0.0) + weight
        }
        return if (total == 0.0) emptyMap() else shared.mapValues { it.value / total }
    }

    /** A record's genre tag as written, the one most of its tracks carry, folded to lower case; null for none. */
    fun tag(album: CloudAlbum): String? =
        album.tracks.mapNotNull { it.tags?.genre?.trim()?.lowercase()?.takeIf { g -> g.isNotEmpty() } }
            .groupingBy { it }.eachCount().maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key })?.key

    /** The share of [albums] on each genre shelf (or tag); albums with none are left out of the count. */
    private fun shares(albums: List<String>, shelfOf: Map<String, String>): Map<String, Double> {
        val shelved = albums.mapNotNull { shelfOf[it] }
        if (shelved.isEmpty()) return emptyMap()
        return shelved.groupingBy { it }.eachCount().mapValues { it.value.toDouble() / shelved.size }
    }

    /** How much two shares have in common: the sum, over each shelf, of the smaller share. */
    private fun overlap(a: Map<String, Double>, b: Map<String, Double>): Double =
        a.entries.sumOf { (shelf, share) -> minOf(share, b[shelf] ?: 0.0) }
}
