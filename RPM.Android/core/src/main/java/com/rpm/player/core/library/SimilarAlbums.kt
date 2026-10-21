package com.rpm.player.core.library

/**
 * The records to show under a record: other records **on this shelf** to play next. It is the iPhone's
 * `SimilarAlbums.row(for:on:)` and the desktop's `RPM.Core/Metadata/SimilarAlbums.cs`, with the same rules, so a
 * record shows the same row on every head.
 *
 * Three axes, weighted and added: the two records' genres, the genres of the two acts pooled over everything they
 * made, and the era. Each axis counts only when both records can answer it, so a record with missing tags is not
 * ranked down for what it lacks. The open record's own act is left out, and no act has a second record in the row
 * while another act is still waiting for its first.
 *
 * It reads every track on the shelf, so it is run off the main thread.
 */
object SimilarAlbums {
    /** How many records the row holds at most. */
    const val COUNT = 9

    private const val ARTIST_GENRES = 1.5
    private const val ALBUM_GENRES = 2.0
    private const val ERA = 1.0

    /** The gap between two years at which the era axis reaches 0. */
    private const val ERA_SPAN = 25.0

    /** A record as the ranking reads it. */
    private class Traits(val album: CloudAlbum, val artist: String, val title: String, val genres: Set<String>, val year: Int)

    /** The records to suggest under [album], highest score first, at most [COUNT]. */
    fun row(album: CloudAlbum, shelf: List<CloudAlbum>): List<CloudAlbum> {
        val matched = HashMap<String, String?>()
        val library = shelf.map { traits(it, matched) }
        val subject = library.firstOrNull { it.album.key == album.key } ?: traits(album, matched)

        val byArtist = HashMap<String, MutableSet<String>>()
        for (t in library) if (t.genres.isNotEmpty()) byArtist.getOrPut(t.artist) { HashSet() }.addAll(t.genres)
        if (subject.genres.isNotEmpty()) byArtist.getOrPut(subject.artist) { HashSet() }.addAll(subject.genres)
        val subjectArtistGenres = byArtist[subject.artist].orEmpty()

        // The same record can be on the shelf twice (imported and also downloaded); it is still one record.
        val seen = hashSetOf(subject.artist + "|" + subject.title)
        val scored = library.mapNotNull { other ->
            if (other.album.album.isBlank() || other.artist == subject.artist) return@mapNotNull null
            if (!seen.add(other.artist + "|" + other.title)) return@mapNotNull null
            other to score(subject, other, subjectArtistGenres, byArtist[other.artist].orEmpty())
        }.sortedWith(compareByDescending<Pair<Traits, Double>> { it.second }.thenBy { it.first.album.key })

        // Two rounds: the best record of each act first, then whatever places a short shelf leaves, best first.
        val picked = ArrayList<CloudAlbum>(COUNT)
        val acts = HashMap<String, Int>()
        val taken = HashSet<String>()
        for (round in 0 until 2) {
            for ((other, _) in scored) {
                if (picked.size >= COUNT) break
                if (other.album.key in taken) continue
                val already = acts[other.artist] ?: 0
                if (round == 0 && already >= 1) continue
                acts[other.artist] = already + 1
                taken += other.album.key
                picked += other.album
            }
        }
        return picked
    }

    /**
     * The genres are every genre its tracks carry, matched to the app's own list ([Genres.match]); a tag that matches
     * none is dropped, because two records sharing an unknown tag proves nothing. The year is the one most tracks
     * agree on.
     */
    private fun traits(album: CloudAlbum, matched: HashMap<String, String?>): Traits {
        val genres = LinkedHashSet<String>()
        for (track in album.tracks) {
            val tag = track.tags?.genre ?: continue
            val genre = if (matched.containsKey(tag)) matched[tag] else Genres.match(tag).also { matched[tag] = it }
            if (genre != null) genres += genre
        }
        return Traits(album, foldName(album.artist), foldName(album.album), genres, AlbumSort.yearOf(album.tracks))
    }

    private fun score(subject: Traits, other: Traits, subjectArtistGenres: Set<String>, otherArtistGenres: Set<String>): Double {
        var total = 0.0
        var asked = 0.0
        if (subjectArtistGenres.isNotEmpty() && otherArtistGenres.isNotEmpty()) {
            total += ARTIST_GENRES * overlap(subjectArtistGenres, otherArtistGenres)
            asked += ARTIST_GENRES
        }
        if (subject.genres.isNotEmpty() && other.genres.isNotEmpty()) {
            total += ALBUM_GENRES * overlap(subject.genres, other.genres)
            asked += ALBUM_GENRES
        }
        if (subject.year > 0 && other.year > 0) {
            total += ERA * maxOf(0.0, 1 - kotlin.math.abs(subject.year - other.year) / ERA_SPAN)
            asked += ERA
        }
        return if (asked > 0) total / asked else 0.0
    }

    private fun overlap(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        var shared = 0
        for (g in b) if (g in a) shared++
        return shared.toDouble() / (a.size + b.size - shared)
    }

    /** Case, accents, punctuation and a leading "the" ignored, as the iPhone's `SimilarAlbums.fold`. */
    private fun foldName(text: String): String = Taste.fold(text).removePrefix("the ")
}
