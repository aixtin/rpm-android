package com.rpm.player.core.library

/**
 * What a record's own name says it is: live, a single, a soundtrack or a compilation. Each is a shelf on
 * an artist's page. The patterns must match the iOS app (`AlbumKind.swift`) and the desktop
 * (`RPM.Core/Library/AlbumKind.cs`). Asked of the name as written, never of a tidied one.
 */
object AlbumKind {
    fun isLive(title: String?): Boolean = matches(title, LIVE)
    fun isSingle(title: String?): Boolean = matches(title, SINGLE)
    fun isCompilation(title: String?): Boolean = matches(title, COMPILATION)
    fun isSoundtrack(title: String?): Boolean = matches(title, SOUNDTRACK)

    private fun matches(title: String?, pattern: Regex): Boolean = !title.isNullOrBlank() && pattern.containsMatchIn(title)

    private val LIVE = Regex("""\b(?:live|sessions|unplugged|storytellers)\b""", RegexOption.IGNORE_CASE)
    private val SINGLE = Regex("""[-\u2013\u2014]\s*singles?\b""", RegexOption.IGNORE_CASE)
    private val SOUNDTRACK = Regex(
        """\b(?:soundtracks?|(?:original|film|motion\s+picture)\s+scores?|motion\s+picture|music\s+from|cast\s+recording|ost)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val COMPILATION = Regex(
        """\b(?:greatest\s+hits|best\s+of|essentials?|collections?|anthology|anthologies|compilations?|retrospective|definitive|hits)\b""",
        RegexOption.IGNORE_CASE,
    )
}

/** What kind of release a record is: the shelf it stands on on an artist's page. The desktop's `ReleaseType`; the
 * names are its stored ones (`ReleaseTypes.ToStored`), which the cloud's listing carries as `releaseType`. */
enum class ReleaseType {
    Album, Ep, Single, Live, Mixtape, Compilation, Soundtrack;

    companion object {
        /** The type a stored name means, or null for a name that is none of them. Any case. */
        fun fromStored(stored: String?): ReleaseType? =
            if (stored.isNullOrBlank()) null else entries.firstOrNull { it.name.equals(stored.trim(), ignoreCase = true) }

        /** What the record's name suggests, in the desktop's order (`ReleaseTypes.FromTitle`): single, live,
         * soundtrack, compilation. Null where it suggests nothing. */
        fun fromTitle(title: String?): ReleaseType? = when {
            AlbumKind.isSingle(title) -> Single
            AlbumKind.isLive(title) -> Live
            AlbumKind.isSoundtrack(title) -> Soundtrack
            AlbumKind.isCompilation(title) -> Compilation
            else -> null
        }

        /**
         * The shelf a record goes on: the type its tracks carry, else what its name suggests, else an album. A set
         * type always wins, which is the point of setting it: "Live Through This" set to Album leaves the live shelf.
         * The desktop's `ReleaseTypes.ShelfOf`. Where the tracks disagree, most of them decide.
         */
        fun shelfOf(album: CloudAlbum): ReleaseType {
            val set = album.tracks.mapNotNull { fromStored(it.tags?.releaseType) }
                .groupingBy { it }.eachCount()
                .maxWithOrNull(compareBy<Map.Entry<ReleaseType, Int>> { it.value }.thenByDescending { it.key.ordinal })?.key
            return set ?: fromTitle(album.album) ?: Album
        }
    }
}
