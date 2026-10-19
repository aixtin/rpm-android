package com.rpm.player.core.library

import java.text.Normalizer

/**
 * Home's two rows that are worked out rather than read: "Recommended for you" and "Based on your
 * listening" (RPM.Core/Library/HomeShelf.cs, Suggested and FromTaste, and RPM.Core/Metadata/
 * SimilarAlbums.cs), from what the cloud holds: the tags (artist, genre, year) and the play counts.
 *
 * The desktop folds genre tags into its own closed list of genres; this folds them to lower case and
 * splits them at the separators a tag uses, so two records share a genre when they share a word list
 * entry. The desktop's listening profile reads its local play log; this reads the play counts, which
 * every device sends to the cloud.
 */
object Taste {
    /** What an album is, for comparing: its artist folded, its genres, its year. */
    data class Traits(val album: CloudAlbum, val artist: String, val title: String, val genres: Set<String>, val year: Int)

    /** Every album's traits, each tag folded once: a genre tag is usually the same few words across a
     * whole library, so the folded words are kept per tag. */
    fun traitsOf(albums: List<CloudAlbum>): List<Traits> {
        val folded = HashMap<String, List<String>>()
        return albums.map { album ->
            val genres = HashSet<String>()
            for (track in album.tracks) {
                val tag = track.tags?.genre ?: continue
                genres += folded.getOrPut(tag) { genresIn(tag).toList() }
            }
            Traits(
                album = album,
                artist = fold(album.artist),
                title = fold(album.album),
                genres = genres,
                year = album.tracks.firstNotNullOfOrNull { it.tags?.year?.takeIf { y -> y > 0 } } ?: 0,
            )
        }
    }

    /** The genre words in a tag: split at `;`, `/` and `,`, trimmed, folded. */
    fun genresIn(tag: String): Sequence<String> =
        tag.split(';', '/', ',').asSequence().map { fold(it) }.filter { it.isNotEmpty() }

    /**
     * Records like the ones listened to last (HomeShelf.Suggested): each seed's similar records, the best
     * [POOL] times [count], shuffled by [draw] with the seed's key as salt, dealt round-robin; none in
     * [exclude], none twice.
     */
    fun suggested(seeds: List<Traits>, traits: List<Traits>, exclude: Set<String>, count: Int, draw: Int): List<CloudAlbum> {
        if (count <= 0 || seeds.isEmpty()) return emptyList()
        val taken = HashSet(exclude)
        val byArtist = HashMap<String, MutableSet<String>>()
        for (t in traits) if (t.genres.isNotEmpty()) byArtist.getOrPut(t.artist) { HashSet() }.addAll(t.genres)
        val answers = seeds.map { seed -> shuffled(similar(seed, traits, byArtist, count * POOL).map { it.album }.toMutableList(), draw, seed.album.key) }
        val row = ArrayList<CloudAlbum>(count)
        var round = 0
        while (row.size < count) {
            var any = false
            for (answer in answers) {
                if (round >= answer.size) continue
                any = true
                if (taken.add(answer[round].key)) {
                    row += answer[round]
                    if (row.size == count) return row
                }
            }
            if (!any) break
            round++
        }
        return row
    }

    /**
     * Records that match what is played most (HomeShelf.FromTaste): each record scores its artist's share
     * of the plays of the top twelve artists times 1.6, plus the shares of the top six genres it carries;
     * a record already played scores 0.3 of that. The best [POOL] times [count], shuffled by [draw].
     */
    fun fromTaste(plays: Map<String, Int>, traits: List<Traits>, exclude: Set<String>, count: Int, draw: Int): List<CloudAlbum> {
        if (count <= 0) return emptyList()
        val artistPlays = HashMap<String, Long>()
        val genrePlays = HashMap<String, Long>()
        for (t in traits) {
            val plays = (plays[t.album.key] ?: 0).toLong()
            if (plays <= 0) continue
            if (t.artist.isNotEmpty()) artistPlays.merge(t.artist, plays, Long::plus)
            for (g in t.genres) genrePlays.merge(g, plays, Long::plus)
        }
        if (artistPlays.isEmpty()) return emptyList()
        val top = artistPlays.entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key }).take(LEANING_ARTISTS)
        val topPlays = top.sumOf { it.value }.toDouble()
        val acts = top.associate { it.key to (if (topPlays > 0) it.value / topPlays else 1.0 / top.size) }
        val genreTotal = genrePlays.values.sum().toDouble()
        val kinds = genrePlays.entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .take(LEANING_GENRES).associate { it.key to it.value / genreTotal }

        val scored = traits.mapNotNull { t ->
            if (t.album.key in exclude) return@mapNotNull null
            var score = (acts[t.artist] ?: 0.0) * ACT_LEAN
            for (g in t.genres) score += kinds[g] ?: 0.0
            if (score <= 0) return@mapNotNull null
            t.album to if ((plays[t.album.key] ?: 0) > 0) score * PLAYED_SHARE else score
        }.sortedWith(compareByDescending<Pair<CloudAlbum, Double>> { it.second }.thenBy { it.first.key.lowercase() })
        return shuffled(scored.take(count * POOL).map { it.first }.toMutableList(), draw, "taste").take(count)
    }

    /** SimilarAlbums.Pick: every other record by another artist, scored on the artists' pooled genres,
     * the records' own genres and their years, best first, one per artist before any artist's second. */
    private fun similar(subject: Traits, library: List<Traits>, byArtist: Map<String, Set<String>>, count: Int): List<Traits> {
        // The subject's own genres are in its artist's pool already: the pool is built from every record.
        val subjectArtistGenres = byArtist[subject.artist].orEmpty()
        val seen = hashSetOf(subject.artist + "|" + subject.title)
        val scored = library.mapNotNull { other ->
            if (other.album.album.isBlank() || other.artist == subject.artist) return@mapNotNull null
            if (!seen.add(other.artist + "|" + other.title)) return@mapNotNull null
            other to score(subject, other, subjectArtistGenres, byArtist[other.artist].orEmpty())
        }.sortedWith(compareByDescending<Pair<Traits, Double>> { it.second }.thenBy { it.first.album.key })
        val picked = ArrayList<Traits>(count)
        val acts = HashMap<String, Int>()
        val taken = HashSet<String>()
        for (round in 0 until 2) {
            for ((album, _) in scored) {
                if (picked.size >= count) break
                if (album.album.key in taken) continue
                val already = acts[album.artist] ?: 0
                if (round == 0 && already >= 1) continue
                acts[album.artist] = already + 1
                taken += album.album.key
                picked += album
            }
        }
        return picked
    }

    private fun score(subject: Traits, other: Traits, subjectArtistGenres: Set<String>, otherArtistGenres: Set<String>): Double {
        var total = 0.0
        var asked = 0.0
        if (subjectArtistGenres.isNotEmpty() && otherArtistGenres.isNotEmpty()) {
            total += 1.5 * overlap(subjectArtistGenres, otherArtistGenres)
            asked += 1.5
        }
        if (subject.genres.isNotEmpty() && other.genres.isNotEmpty()) {
            total += 2.0 * overlap(subject.genres, other.genres)
            asked += 2.0
        }
        if (subject.year > 0 && other.year > 0) {
            total += maxOf(0.0, 1 - kotlin.math.abs(subject.year - other.year) / ERA_SPAN)
            asked += 1.0
        }
        return if (asked > 0) total / asked else 0.0
    }

    /** Shared genres over all the genres of the two: |a and b| / |a or b|. */
    private fun overlap(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        var shared = 0
        for (g in b) if (g in a) shared++
        return shared.toDouble() / (a.size + b.size - shared)
    }

    /** HomeShelf.Shuffled: a Fisher-Yates shuffle on an LCG seeded from [draw] and [salt], so a launch
     * keeps one order and the next launch has another. */
    fun <T> shuffled(items: MutableList<T>, draw: Int, salt: String): MutableList<T> {
        var state = draw
        for (c in salt) state = state * 31 + c.code
        state *= -1640531535
        for (i in items.lastIndex downTo 1) {
            state = state * 1103515245 + 12345
            val j = ((state shr 8).toUInt() % (i + 1).toUInt()).toInt()
            items[i] = items[j].also { items[j] = items[i] }
        }
        return items
    }

    /** Case, accents and punctuation folded away, runs of space made one. */
    fun fold(text: String?): String {
        if (text.isNullOrBlank()) return ""
        // Most names are plain ASCII, which needs neither the decomposition nor a pattern. The long way
        // takes about a second for 3,000 names on a TV box.
        val plain = if (text.all { it.code < 128 }) text else MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "")
        val out = StringBuilder(plain.length)
        var space = false
        for (ch in plain) {
            if (ch.isLetterOrDigit()) {
                if (space && out.isNotEmpty()) out.append(' ')
                out.append(ch.lowercaseChar())
                space = false
            } else {
                space = true
            }
        }
        return out.toString()
    }

    private val MARKS = Regex("\\p{M}+")

    private const val POOL = 3
    private const val LEANING_ARTISTS = 12
    private const val LEANING_GENRES = 6
    private const val ACT_LEAN = 1.6
    private const val PLAYED_SHARE = 0.3
    private const val ERA_SPAN = 25.0
}
