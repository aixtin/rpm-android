package com.rpm.player.core.library

/**
 * One artist tag, read as the list of acts it names. The separators and limits must match the iOS app
 * (`ArtistCredit.swift`) and the desktop (`RPM.Core/Metadata/Providers/ArtistCredit.cs`), so every app
 * reads one credit the same way.
 */
object ArtistCredit {
    /** The app's spelling of the compilation marker (the desktop's `AlbumResolver.VariousArtists`). */
    const val VARIOUS_ARTISTS = "Various Artists"

    /** Where a credit stops being a credit: a field naming more acts than this is a track list. */
    const val MAX_CREDITED = 8

    /** The names in a credit, cut on a comma, a slash, an ampersand, a semicolon or "feat". */
    fun split(credit: String?): List<String> = cut(credit, SEPARATORS)

    /** The same, with "with", "and" and "x" as separators too. Asked only by a caller that can check the
     * answer against acts it already knows (see [ArtistWall.build]). */
    fun splitWide(credit: String?): List<String> = cut(credit, WIDE_SEPARATORS)

    /**
     * The same names as [split], grouped by the guest markers rather than run together: "Tyler, The Creator
     * feat. Frank Ocean" is `[["Tyler", "The Creator"], ["Frank Ocean"]]` (the desktop's `SplitGuests`).
     *
     * For a caller that puts split names back together ([ArtistWall]'s rejoin). "feat" says outright that what
     * follows is somebody else; a comma does not, and many names with a comma inside are one act. The cap and
     * the dropped duplicates are counted across the whole credit, not per group.
     */
    fun splitGuests(credit: String?): List<List<String>> {
        if (credit.isNullOrBlank()) return emptyList()
        val groups = ArrayList<List<String>>()
        val all = ArrayList<String>()
        for (group in GUEST_SEPARATORS.split(credit)) {
            val names = ArrayList<String>()
            for (part in NAME_SEPARATORS.split(group)) {
                val name = named(part, all) ?: continue
                all.add(name)
                names.add(name)
                if (all.size == MAX_CREDITED) break
            }
            if (names.isNotEmpty()) groups.add(names)
            if (all.size == MAX_CREDITED) break
        }
        return groups
    }

    /** Whether a name is the compilation marker rather than somebody. */
    fun isVariousArtists(name: String?): Boolean {
        if (name.isNullOrEmpty()) return false
        return NameMatch.squash(name) in VARIOUS_NAMES
    }

    private fun cut(credit: String?, separators: Regex): List<String> {
        if (credit.isNullOrBlank()) return emptyList()
        val names = ArrayList<String>()
        for (part in separators.split(credit)) {
            val name = named(part, names) ?: continue
            names.add(name)
            if (names.size == MAX_CREDITED) break
        }
        return names
    }

    /** One cut piece as a name, or null where it is not one. A single character is what is left of a name cut
     * in the wrong place; a name twice is one act typed twice; the compilation marker is not a person. */
    private fun named(part: String, already: List<String>): String? {
        val name = part.trim().trim('(', ')', '[', ']', '-', '\u2013').trim()
        return if (name.length < 2 || isVariousArtists(name) || already.any { it.equals(name, ignoreCase = true) }) null else name
    }

    private val VARIOUS_NAMES = setOf(NameMatch.squash("Various"), NameMatch.squash("Various Artist"), NameMatch.squash(VARIOUS_ARTISTS))

    /** "feat" needs a bracket or a space in front of it, so "Defeat Ed" is not cut at the "feat". */
    private val SEPARATORS = Regex("""\s*[,;/&]\s*|[\s(\[]+(?:feat\.?|featuring|ft\.?)\s+""", RegexOption.IGNORE_CASE)
    /** The two halves of [SEPARATORS], for [splitGuests]: the guest markers alone, and the punctuation alone. */
    private val GUEST_SEPARATORS = Regex("""[\s(\[]+(?:feat\.?|featuring|ft\.?)\s+""", RegexOption.IGNORE_CASE)
    private val NAME_SEPARATORS = Regex("""\s*[,;/&]\s*""")
    private val WIDE_SEPARATORS =
        Regex("""\s*[,;/&]\s*|[\s(\[]+(?:feat\.?|featuring|ft\.?)\s+|\s+(?:with|and|x)\s+""", RegexOption.IGNORE_CASE)
}
