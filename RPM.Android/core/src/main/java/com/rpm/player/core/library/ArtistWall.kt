package com.rpm.player.core.library

import java.text.Normalizer

/** One album's credits: the record's own artist, one of its tracks' artists, and how many of its tracks
 * carry that pairing. [album] is the album's wall key. */
data class ArtistCreditRow(val album: String, val albumArtist: String?, val artist: String?, val tracks: Int)

/**
 * One act on the artists wall: their name, the records the collection files under them, and the records
 * they only turn up on. Each list holds album keys.
 */
class ArtistWallItem(
    /** The spelling the most rows carry. */
    val name: String,
    /** Albums filed under this act alone. */
    val albums: List<String>,
    /** Albums filed under a credit naming this act and somebody else: a record the two made. */
    val together: List<String>,
    /** Somebody else's albums they are credited on. */
    val appearances: List<String>,
    /** How many tracks name them, anywhere. */
    val tracks: Int,
) {
    /** Their own records, alone or shared. */
    val own: List<String> get() = albums + together

    /** The letter of the alphabet they file under, or '#'. */
    val initial: Char get() = ArtistWall.initial(name)
}

/**
 * The collection read by who made it: one entry per act, not per credit. The rules match the iOS app
 * (`ArtistWall.swift`) and the desktop (`RPM.Core/Library/ArtistWall.cs`), so all three show the same wall.
 *
 * A track's artist field is a credit, not a name: "Calvin Harris feat. Ellie Goulding" names two acts.
 * Every credit is split into the acts it names, except where the credit is a name the collection files a
 * record under ("Simon & Garfunkel"), and a credit filed as a record's artist whose every part is an act
 * the collection already knows is two acts working together. An act with no record of their own gets no
 * card. Every spelling of the compilation marker is one act.
 */
object ArtistWall {
    /** The one name every spelling of the compilation marker is shown under. */
    const val COMPILATIONS = ArtistCredit.VARIOUS_ARTISTS

    /** The credit rows a list of albums makes: one per (album, track artist), and one with no track artist
     * for an album no track of which credits anybody. */
    fun rows(albums: List<CloudAlbum>): List<ArtistCreditRow> {
        val rows = ArrayList<ArtistCreditRow>(albums.size)
        for (album in albums) {
            val counts = LinkedHashMap<String, Int>()
            for (track in album.tracks) {
                val credit = track.artist?.trim().orEmpty()
                if (credit.isEmpty()) continue
                counts[credit] = (counts[credit] ?: 0) + 1
            }
            if (counts.isEmpty()) {
                rows.add(ArtistCreditRow(album.key, album.artist, null, album.tracks.size))
                continue
            }
            for ((credit, tracks) in counts) rows.add(ArtistCreditRow(album.key, album.artist, credit, tracks))
        }
        return rows
    }

    /** The wall, in name order with the '#' bucket last. */
    fun build(credits: List<ArtistCreditRow>): List<ArtistWallItem> {
        // What the collection files records under: the squashed name, and the spelling as filed, so a run of
        // split parts that spells one of these is shown under the collection's spelling (see rejoined).
        val bands = HashMap<String, String>()
        val filed = ArrayList<String>()
        for (row in credits) {
            val credit = row.albumArtist
            if (credit.isNullOrEmpty() || ArtistCredit.isVariousArtists(credit)) continue
            if (bands.putIfAbsent(NameMatch.squash(credit), credit.trim()) == null) filed.add(credit.trim())
        }

        val joint = collaborations(credits, bands, filed)
        val acts = HashMap<String, Act>()

        credits.forEachIndexed { index, row ->
            val leads = names(row.albumArtist, bands, joint)
            for (name in leads) act(acts, name).lead(row.album, index, row.tracks, shared = leads.size > 1)
            for (name in names(row.artist, bands, joint)) act(acts, name).guest(row.album, index, row.tracks)
        }

        val wall = acts.values.map { it.settle() }.filter { it.albums.isNotEmpty() || it.together.isNotEmpty() }
        return ArtistSort.byName(wall)
    }

    /** Whether one credit as typed names this act: the credit whole, then each act it names, read wide. */
    fun names(credit: String?, artist: String?): Boolean {
        if (credit.isNullOrBlank() || artist.isNullOrBlank()) return false
        return NameMatch.same(credit, artist) || ArtistCredit.splitWide(credit).any { NameMatch.same(it, artist) }
    }

    /** The letter a name files under: its first letter with the accent taken off, or '#' for a name that
     * starts with a digit, a symbol or a letter outside A to Z. */
    fun initial(name: String?): Char {
        for (character in name.orEmpty().trim()) {
            if (!character.isLetterOrDigit()) continue
            val folded = Normalizer.normalize(character.uppercase(), Normalizer.Form.NFD).firstOrNull { it in 'A'..'Z' }
            return folded ?: '#'
        }
        return '#'
    }

    /** A name as a search compares it: lower case, accents off, only letters and digits. */
    fun searchKey(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        val folded = StringBuilder()
        for (character in decomposed) if (character.isLetterOrDigit()) folded.append(character.lowercaseChar())
        return folded.toString()
    }

    // ── reading a credit ──

    private fun names(credit: String?, bands: Map<String, String>, joint: Map<String, List<String>>): List<String> {
        if (credit.isNullOrBlank()) return emptyList()
        if (ArtistCredit.isVariousArtists(credit)) return listOf(COMPILATIONS)
        val whole = credit.trim()
        val key = NameMatch.squash(whole)
        joint[key]?.let { return it }
        if (key in bands) return listOf(whole)
        return rejoined(ArtistCredit.splitGuests(whole), bands)
    }

    /**
     * A split credit with any run of its parts that spells a name the collection files records under put back
     * together (the desktop's `ArtistWall.Rejoined`). The split knows where a separator is, not which ones are
     * inside a name: "A$AP Rocky; Tyler, The Creator; Jozzy" cuts into four, and two of the four are one act.
     * Only a run that squashes to a filed credit is rejoined, so this never invents an act.
     *
     * This also keeps [collaborations] right. It reads the names a collection knows from split track
     * credits. If "Tyler" and "The Creator" were in that set, "Tyler, The Creator" would pass as two people and
     * get two cards. Parts are never joined across a "feat", which is the one cut a credit is explicit
     * about. The longest run wins, under the collection's spelling.
     */
    private fun rejoined(groups: List<List<String>>, bands: Map<String, String>): List<String> {
        val kept = ArrayList<String>()
        for (parts in groups) {
            var i = 0
            while (i < parts.size) {
                // Squashes concatenate the way the names do, so a run never has to be spelled back out.
                var run = NameMatch.squash(parts[i])
                var joined = run
                var last = i
                for (j in i + 1 until parts.size) {
                    joined += NameMatch.squash(parts[j])
                    if (joined in bands) {
                        run = joined
                        last = j
                    }
                }
                if (last == i) {
                    kept.add(parts[i])
                } else if (kept.none { NameMatch.squash(it) == run }) {
                    // A rejoin can give a name that the credit already has elsewhere.
                    kept.add(bands.getValue(run))
                }
                i = last + 1
            }
        }
        return kept
    }

    /** The filed credits whose every part is an act the collection knows on their own: records two acts
     * made together. Every part, not most, so "Nick Cave & The Bad Seeds" stays a band beside his solo
     * records. */
    private fun collaborations(credits: List<ArtistCreditRow>, bands: Map<String, String>, filed: List<String>): Map<String, List<String>> {
        val known = HashSet<String>()
        val none = emptyMap<String, List<String>>()
        for (row in credits) {
            for (name in names(row.albumArtist, bands, none)) known.add(NameMatch.squash(name))
            for (name in names(row.artist, bands, none)) known.add(NameMatch.squash(name))
        }
        val joint = HashMap<String, List<String>>()
        for (credit in filed) {
            val parts = ArtistCredit.splitWide(credit)
            if (parts.size > 1 && parts.all { NameMatch.squash(it) in known }) joint[NameMatch.squash(credit)] = parts
        }
        return joint
    }

    private fun act(acts: HashMap<String, Act>, name: String): Act {
        val act = acts.getOrPut(NameMatch.squash(name)) { Act() }
        act.spellings[name] = (act.spellings[name] ?: 0) + 1
        return act
    }

    /** One act while the rows are still being read. */
    private class Act {
        val spellings = HashMap<String, Int>()
        private val albums = ArrayList<String>()
        private val together = ArrayList<String>()
        private val appearances = ArrayList<String>()
        private val leads = HashSet<String>()
        private val guests = HashSet<String>()
        private val counted = HashSet<Int>()
        private var tracks = 0

        fun lead(album: String, row: Int, tracks: Int, shared: Boolean) {
            count(row, tracks)
            if (!leads.add(album.lowercase())) return
            // It is filed under them after all, so drop the appearance an earlier guest credit may have added.
            if (guests.remove(album.lowercase())) appearances.removeAll { it.lowercase() == album.lowercase() }
            if (shared) together.add(album) else albums.add(album)
        }

        fun guest(album: String, row: Int, tracks: Int) {
            count(row, tracks)
            if (album.lowercase() in leads || !guests.add(album.lowercase())) return
            appearances.add(album)
        }

        /** One row's tracks, once: a row names an act twice when the record is filed under the name its
         * tracks are credited to. */
        private fun count(row: Int, tracks: Int) {
            if (counted.add(row)) this.tracks += tracks
        }

        fun settle(): ArtistWallItem {
            // The spelling on the most rows, and the alphabetically first of a tie.
            val name = spellings.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .firstOrNull()?.key.orEmpty()
            val order = compareBy<String, String>(String.CASE_INSENSITIVE_ORDER) { it }.thenBy { it }
            return ArtistWallItem(name, albums.sortedWith(order), together.sortedWith(order), appearances.sortedWith(order), tracks)
        }
    }
}
