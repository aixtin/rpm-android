package com.rpm.player.core.library

import java.util.Locale

/**
 * Who is on a list: the acts its songs credit, most songs first, for the "Featuring" line under a list's name.
 * It matches the iPhone's `PlaylistFeatures` and the desktop's line under a playlist's name.
 */
object PlaylistFeatures {
    /** How many acts the line names before it counts the rest. */
    const val NAMED = 3

    /** The acts named, in order, and how many more there are. */
    data class Featured(val named: List<String>, val more: Int)

    /**
     * The acts in [credits] by how many songs credit them, then by name, so the line is the same every time it is
     * drawn. Counted ignoring case and kept by the first spelling seen: "Blur" and "BLUR" are one act to a reader,
     * and the list's own order is the only way to choose which spelling is printed.
     */
    fun of(credits: List<String?>, most: Int): Featured {
        val songs = HashMap<String, Int>()
        val spelling = HashMap<String, String>()
        for (credit in credits) {
            for (name in ArtistCredit.split(credit)) {
                val key = name.lowercase(Locale.ROOT)
                songs[key] = (songs[key] ?: 0) + 1
                spelling.getOrPut(key) { name }
            }
        }
        if (songs.isEmpty()) return Featured(emptyList(), 0)
        val byName = collator()
        val named = songs.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy(byName) { it.key })
            .take(most.coerceAtLeast(0))
            .mapNotNull { spelling[it.key] }
        return Featured(named, songs.size - named.size)
    }
}
