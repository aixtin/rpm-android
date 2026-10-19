package com.rpm.player.core.library

/**
 * The genres' lettering and colours ([GenreWordmarkTable], generated from `scripts/wordmarks.json`), as the
 * desktop's GenreWordmarks reads them. The three tones a genre is drawn at are the palette's own rule, 0.62,
 * 0.51 and 0.40 of its colour, which is the arithmetic every gradient in `RpmPalette.xaml` was made with.
 */
object GenreMarks {
    private val byGenre: Map<String, GenreWordmark> by lazy { GenreWordmarkTable.all.associateBy { it.genre.lowercase() } }

    /** The genre's lettering, or null for a genre the table has no drawing for. */
    fun of(genre: String): GenreWordmark? = byGenre[genre.lowercase()]

    /** The name that runs furthest for its letters' height: the size at which it still fits is the size every
     * name is set at, so the letters stand one height on every card (the desktop's `WidestName`). */
    val widestName: Double by lazy { GenreWordmarkTable.all.maxOf { it.aspect / it.base } }

    /**
     * The genre's photograph's name without its folder or extension: "genre-" and the genre lower cased, every run
     * of anything that is not an ASCII letter or digit one hyphen, as `scripts/gen_genre_photos.py` names them
     * ("Hip-Hop / Rap" is `genre-hip-hop-rap`).
     */
    fun photoName(genre: String): String {
        val out = StringBuilder("genre-")
        var gap = false
        for (c in genre.lowercase()) {
            if (c in 'a'..'z' || c in '0'..'9') {
                if (gap && out.length > "genre-".length) out.append('-')
                out.append(c)
                gap = false
            } else {
                gap = true
            }
        }
        return out.toString()
    }

    /** The colour as three channels from 0 to 1. */
    fun channels(mark: GenreWordmark): Triple<Float, Float, Float> =
        Triple(((mark.colour shr 16) and 0xFF) / 255f, ((mark.colour shr 8) and 0xFF) / 255f, (mark.colour and 0xFF) / 255f)

    /** The lightest of the three tones, where the name stands (the desktop's `Lit`). */
    const val LIT = 0.62f

    /** The middle tone. */
    const val MID = 0.51f

    /** The darkest, the ground the room's lobes sit on (the desktop's `Deep`). */
    const val DEEP = 0.40f
}
