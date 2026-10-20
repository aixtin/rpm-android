package com.rpm.player.core.library

import com.rpm.player.core.cloud.CloudTrack
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * One song a radio can play or start from.
 *
 * [record] is the album's key: a radio spaces records by it. [song] is the act and the title with the edition notes
 * taken off, lower cased: two tracks with the same value are one song on two records. [genre] is the shelf the
 * record is filed on, [styles] the styles its tags name, [moods] the moods the computer put it in.
 */
data class RadioSong(
    val id: String,
    val record: String,
    val artist: String,
    val song: String,
    val year: Int,
    val genre: String?,
    val styles: Set<String>,
    val moods: Set<String>,
    val offered: Boolean,
    val plays: Int,
)

/**
 * Every song in one collection that a radio can pick from or start from. Built once per collection, off the main
 * thread, and held by the radios on it, so picking reads nothing.
 */
class RadioCrate(val songs: List<RadioSong>) {
    private val byId: Map<String, RadioSong> = songs.associateBy { it.id }

    private val favourites: Set<String> = songs
        .filter { it.offered && it.plays >= FAVOURITE_LEAST_PLAYS }
        .sortedWith(compareByDescending<RadioSong> { it.plays }.thenBy { it.id })
        .take(FAVOURITE_POOL)
        .mapTo(HashSet()) { it.id }

    fun find(id: String): RadioSong? = byId[id]

    /** Whether the song is one of the [FAVOURITE_POOL] the listener plays most. */
    fun isFavourite(song: RadioSong): Boolean = song.id in favourites

    companion object {
        /** How many plays make a favourite. Once or twice is a song that was tried. */
        const val FAVOURITE_LEAST_PLAYS = 3

        /** How many of the most played songs count as favourites. */
        const val FAVOURITE_POOL = 150

        /** A track shorter than this is an interlude: it can start a radio but is never picked. The desktop's
         * `Moods.LeastSeconds`. */
        const val LEAST_SECONDS = 60.0

        val EMPTY = RadioCrate(emptyList())

        /**
         * The crate for [collection]. A record's shelf is the one most of its tracks' tags name, as the desktop
         * files a record by its majority, so one oddly tagged track does not move it.
         */
        fun of(collection: Collection): RadioCrate {
            val shelfOf = HashMap<String, String?>()
            for (album in collection.albums) {
                shelfOf[album.key] = album.tracks.mapNotNull { Genres.match(it.tags?.genre) }
                    .groupingBy { it }.eachCount()
                    .maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key })?.key
            }
            val stylesOf = HashMap<String, Set<String>>()
            val songs = collection.tracksById.values.map { track ->
                val record = collection.albumOf[track.rpmId]?.key ?: track.rpmId
                val tag = track.tags?.genre
                val styles = if (tag == null) emptySet() else stylesOf.getOrPut(tag) { Genres.styles(tag) }
                RadioSong(
                    id = track.rpmId,
                    record = record,
                    artist = (track.tags?.albumArtist?.takeIf { it.isNotBlank() } ?: track.artist).orEmpty().trim(),
                    song = songOf(track),
                    year = track.tags?.year ?: 0,
                    genre = shelfOf[record] ?: Genres.match(tag),
                    styles = styles,
                    moods = collection.moodsById[track.rpmId].orEmpty(),
                    offered = (track.tags?.durationSeconds ?: 0.0) >= LEAST_SECONDS,
                    plays = collection.plays[track.rpmId]?.count ?: 0,
                )
            }
            return RadioCrate(songs)
        }

        /** Notes about which recording this is: "(2011 Remaster)", "[Live]", "(feat. …)". */
        private val ASIDE = Regex("""\s*[(\[][^)\]]*\b(remaster\w*|live|edit|version|mix|mono|stereo|deluxe|bonus|demo|feat\.?|ft\.?|featuring|with)\b[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)

        /** The value two copies of one song share. An untitled track keeps its own id, so it is never taken for
         * another untitled one. */
        internal fun songOf(track: CloudTrack): String {
            val title = (track.title ?: "").replace(ASIDE, "").trim()
            return if (title.isEmpty()) track.rpmId else (track.artist ?: "").trim().lowercase() + "\n" + title.lowercase()
        }
    }
}

/**
 * Picks the songs a song radio plays next, as the desktop's `SongRadio` does: songs close to the one playing in mood,
 * era and genre, down to the style (trip hop is not electro), with the song the radio started from counting for half
 * as much again so a long session does not drift. Every [FAVOURITE_EVERY]th song is one the listener plays most,
 * when one fits as well as the closest few percent of the collection; one that does not fit is not forced in.
 *
 * The phone has less to go on than the computer. It has no mood readings and no tempo, only which of the
 * computer's mood lists a song is in, and no styles but the ones a song's own genre tag names. So the mood term is
 * the lists alone, and tempo is not asked.
 *
 * Pure: the crate, the queue and the history are handed in, and the answer is a list of rpmIds.
 */
object SongRadio {
    /** How many songs a radio keeps queued after the one playing. */
    const val AHEAD = 10

    /** Every this many songs the radio queues, one is a favourite where one fits. Counted from the song the radio
     * started from. */
    const val FAVOURITE_EVERY = 5

    internal const val ORIGIN_WEIGHT = 0.5
    internal const val SHORTLIST = 6
    internal const val ARTIST_GAP = 5
    internal const val RECORD_GAP = 12
    internal const val ERA_SPAN = 15.0

    /** What a term gives when either song lacks the fact it compares: halfway, so an unknown is never taken for a
     * match. */
    internal const val UNKNOWN = 0.5
    internal const val RELATED_GENRE = 0.5
    internal const val KIN_STYLE = 0.3
    internal const val OTHER_STYLE = 0.6
    internal const val ONE_STYLE = 0.15

    /** The mood term when one song is in a mood list and the other in none: nearer apart than together. */
    internal const val ONE_IN_A_MOOD = 0.75

    /** How close a favourite must be: among this share of what the radio could play now, never fewer than a
     * shortlist. */
    internal const val FAVOURITE_FIT = 0.05

    /** Styles that sit next to one another. The desktop's `SongRadio.StyleFamilies`; change both together. */
    internal val STYLE_FAMILIES: List<List<String>> = listOf(
        listOf("Downtempo", "Trip Hop", "Chillout", "Chillwave", "Lo-fi Beats", "Ambient", "New Age", "Folktronica",
            "Vaporwave", "Ambient Techno", "Ambient Classical"),
        listOf("IDM", "Leftfield", "Electronica", "Progressive Electronic", "Downtempo", "Trip Hop"),
        listOf("Electro", "Electro House", "Trance", "Psytrance", "Eurodance", "Italo Dance", "Hardstyle", "Future Bass",
            "Alternative Dance", "Deep House", "Tech House", "Progressive House", "Future House", "French House",
            "Garage House", "Tribal House", "Acid House", "Chicago House", "Afro House", "Euro House",
            "Detroit Techno", "Minimal Techno", "Acid Techno", "Hard Techno", "Industrial Techno"),
        listOf("Drum and Bass", "Jungle", "Breakbeat", "Dubstep", "Post-Dubstep", "UK Garage", "Speed Garage", "Future Bass"),
        listOf("Synthwave", "Dark Wave", "Ethereal Wave", "Minimal Wave", "Witch House", "Industrial", "Post-Industrial",
            "Dark Ambient"),
        listOf("Medieval", "Renaissance", "Early Music", "Baroque"),
        listOf("Baroque", "Classical Period"),
        listOf("Classical Period", "Romantic Classical"),
        listOf("Romantic Classical", "Impressionist"),
        listOf("Impressionist", "Contemporary Classical", "Modern Classical", "Minimalism", "Post-Minimalism",
            "Ambient Classical"),
    )

    private val familiesOf: Map<String, Set<Int>> = buildMap<String, MutableSet<Int>> {
        STYLE_FAMILIES.forEachIndexed { at, family -> for (style in family) getOrPut(style.lowercase()) { HashSet() }.add(at) }
    }

    /** A classical record's year is when it was recorded, not when the music is from, so two of them are not
     * compared by era. */
    private val CLASSICAL = setOf("classical", "neo-classical", "opera")

    /**
     * The next songs, in the order they should be queued.
     *
     * [seed] is the song playing, [origin] the one the radio started from, [inQueue] every song queued now,
     * [history] every song this radio has queued, oldest first, the origin included. Empty when neither the seed nor
     * the origin is in the crate.
     */
    fun pick(
        crate: RadioCrate,
        seed: String,
        origin: String,
        inQueue: Set<String>,
        history: List<String>,
        count: Int,
        random: Random,
    ): List<String> {
        val playing = crate.find(seed) ?: crate.find(origin) ?: return emptyList()
        if (count <= 0) return emptyList()
        val start = crate.find(origin) ?: playing

        var ranked = ranked(crate, playing, start, blocked(crate, inQueue, history, seed, origin))
        if (ranked.size < count && history.size > 1) {
            // Nearly everything has been queued: the older half of the history may come back.
            ranked = ranked(crate, playing, start, blocked(crate, inQueue, history.drop(history.size / 2), seed, origin))
        }

        val recent = history.takeLast(RECORD_GAP).mapNotNullTo(ArrayList()) { crate.find(it) }
        recent.add(playing)

        val picked = ArrayList<String>(count)
        val takenIds = HashSet<String>()
        val takenSongs = HashSet<String>()
        val shortlist = ArrayList<RadioSong>(SHORTLIST)
        val fitDepth = min(ranked.size, max(SHORTLIST, ceil(ranked.size * FAVOURITE_FIT).toInt()))

        fun free(song: RadioSong) = song.id !in takenIds && song.song !in takenSongs

        while (picked.size < count) {
            shortlist.clear()
            val slot = history.size + picked.size
            if (slot > 0 && slot % FAVOURITE_EVERY == 0) {
                for (i in 0 until fitDepth) {
                    val song = ranked[i]
                    if (crate.isFavourite(song) && free(song) && spaced(song, recent)) shortlist.add(song)
                    if (shortlist.size == SHORTLIST) break
                }
            }
            if (shortlist.isEmpty()) fill(ranked, shortlist) { free(it) && spaced(it, recent) }
            // Nothing passes the spacing rules: spacing gives way rather than the radio going quiet.
            if (shortlist.isEmpty()) fill(ranked, shortlist) { free(it) }
            if (shortlist.isEmpty()) break

            val pick = shortlist[random.nextInt(shortlist.size)]
            picked.add(pick.id)
            takenIds.add(pick.id)
            takenSongs.add(pick.song)
            recent.add(pick)
        }
        return picked
    }

    private inline fun fill(ranked: List<RadioSong>, into: MutableList<RadioSong>, ok: (RadioSong) -> Boolean) {
        for (song in ranked) {
            if (!ok(song)) continue
            into.add(song)
            if (into.size == SHORTLIST) return
        }
    }

    /** How far apart two songs are in mood, era and genre, from 0 to 1. */
    internal fun apart(song: RadioSong, to: RadioSong, related: Set<String> = relatedTo(to.genre)): Double =
        (moodApart(song, to) + eraApart(song, to) + genreApart(song, to, related)) / 3

    /** What a pick is ranked by: the distance to the song playing plus half the distance to the origin. */
    internal fun score(song: RadioSong, playing: RadioSong, origin: RadioSong): Double =
        Scorer(playing, origin).score(song)

    /** [score] with the related shelves worked out once per pick rather than once per song. */
    private class Scorer(val playing: RadioSong, val origin: RadioSong) {
        private val playingRelated = relatedTo(playing.genre)
        private val originRelated = relatedTo(origin.genre)
        fun score(song: RadioSong): Double =
            apart(song, playing, playingRelated) + ORIGIN_WEIGHT * apart(song, origin, originRelated)
    }

    /** The shelves next to this one, lower cased. */
    private fun relatedTo(genre: String?): Set<String> =
        if (genre == null) emptySet() else Genres.related(genre).mapTo(HashSet()) { it.lowercase() }

    internal fun moodApart(a: RadioSong, b: RadioSong): Double = when {
        a.moods.isEmpty() && b.moods.isEmpty() -> UNKNOWN
        a.moods.isEmpty() || b.moods.isEmpty() -> ONE_IN_A_MOOD
        a.moods.any { it in b.moods } -> 0.0
        else -> 1.0
    }

    internal fun eraApart(a: RadioSong, b: RadioSong): Double {
        if (a.genre?.lowercase() in CLASSICAL && b.genre?.lowercase() in CLASSICAL) return 0.0
        return if (a.year > 0 && b.year > 0) min(abs(a.year - b.year) / ERA_SPAN, 1.0) else UNKNOWN
    }

    /** The shelves first, then the styles within them. See the desktop's `SongRadio.GenreApart`. */
    internal fun genreApart(song: RadioSong, to: RadioSong, related: Set<String> = relatedTo(to.genre)): Double {
        val styles = styleApart(song.styles, to.styles)
        val genre = song.genre
        val other = to.genre
        if (genre == null || other == null) return if (styles != null && styles <= KIN_STYLE) styles else UNKNOWN
        if (genre.equals(other, ignoreCase = true)) {
            return styles ?: if (song.styles.isEmpty() && to.styles.isEmpty()) 0.0 else ONE_STYLE
        }
        val shelves = if (genre.lowercase() in related) RELATED_GENRE else 1.0
        return if (styles != null && styles <= KIN_STYLE) min(shelves, styles) else shelves
    }

    /** 0 for a style in common, [KIN_STYLE] for kin, [OTHER_STYLE] for neither, null when either has none. */
    internal fun styleApart(a: Set<String>, b: Set<String>): Double? {
        if (a.isEmpty() || b.isEmpty()) return null
        if (a.any { x -> b.any { it.equals(x, ignoreCase = true) } }) return 0.0
        for (x in a) {
            val ofX = familiesOf[x.lowercase()] ?: continue
            for (y in b) if (familiesOf[y.lowercase()]?.any { it in ofX } == true) return KIN_STYLE
        }
        return OTHER_STYLE
    }

    /** Whether the act and the record have been away long enough. An empty credit names nobody. */
    private fun spaced(song: RadioSong, recent: List<RadioSong>): Boolean {
        var i = recent.size - 1
        while (i >= 0 && i >= recent.size - RECORD_GAP) {
            val other = recent[i]
            if (other.record == song.record) return false
            if (i >= recent.size - ARTIST_GAP && song.artist.isNotEmpty() && other.artist.equals(song.artist, ignoreCase = true)) return false
            i--
        }
        return true
    }

    private class Blocked(val ids: Set<String>, val songs: Set<String>)

    private fun blocked(crate: RadioCrate, inQueue: Set<String>, history: List<String>, seed: String, origin: String): Blocked {
        val ids = HashSet(inQueue).apply { addAll(history); add(seed); add(origin) }
        return Blocked(ids, ids.mapNotNullTo(HashSet()) { crate.find(it)?.song })
    }

    /** Every song that may be picked, closest first, the id breaking a tie so one crate always ranks the same way. */
    private fun ranked(crate: RadioCrate, playing: RadioSong, origin: RadioSong, blocked: Blocked): List<RadioSong> {
        val scorer = Scorer(playing, origin)
        return crate.songs.asSequence()
            .filter { it.offered && it.id !in blocked.ids && it.song !in blocked.songs }
            .map { it to scorer.score(it) }
            .sortedWith(compareBy<Pair<RadioSong, Double>> { it.second }.thenBy { it.first.id })
            .map { it.first }
            .toList()
    }
}
