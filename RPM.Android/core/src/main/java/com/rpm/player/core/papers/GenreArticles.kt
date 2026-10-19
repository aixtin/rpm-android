package com.rpm.player.core.papers

/**
 * The English Wikipedia article that describes each genre the app shelves by. It is the desktop's
 * table (`EncyclopediaLookup.Encyclopedias`, RPM.Core/Metadata/Providers/EncyclopediaLookup.cs),
 * copied unchanged. A genre is never searched for by name, because "Club" or "World" would find the
 * wrong page. The two shelves that are not kinds of music, and any genre added to the app without an
 * entry here, have no article.
 */
object GenreArticles {
    private val BY_GENRE: Map<String, String> = listOf(
        "Rock" to "Rock music",
        "Indie" to "Indie rock",
        "Pop" to "Pop music",
        "Disco" to "Disco",
        "Funk" to "Funk",
        "Soul" to "Soul music",
        "R&B" to "Contemporary R&B",
        "Rap" to "Hip hop music",
        "Trap" to "Trap music",
        "Old School Hip Hop" to "Old-school hip hop",
        "Hip-Hop / Rap" to "Hip hop music",
        "House" to "House music",
        "Techno" to "Techno",
        "Club" to "Club music",
        "Electronic / Dance" to "Electronic dance music",
        "Jazz" to "Jazz",
        "Blues" to "Blues",
        "Country" to "Country music",
        "Folk" to "Folk music",
        "Classical" to "Classical music",
        "Neo-Classical" to "Neoclassicism (music)",
        "Opera" to "Opera",
        "Metal" to "Heavy metal music",
        "Punk" to "Punk rock",
        "Noise" to "Noise music",
        "Avant-Garde" to "Avant-garde music",
        "Reggae" to "Reggae",
        "Latin" to "Latin music",
        "World" to "World music",
        "Religious" to "Religious music",
        "Soundtrack" to "Film score",
        "Video Game" to "Video game music",
        "Musical" to "Musical theatre",
        "Spoken Word" to "Spoken word",
    ).associate { (genre, title) -> genre.lowercase() to title }

    /** The article's title for [genre], matched without regard to case. Null where there is none. */
    fun title(genre: String): String? = BY_GENRE[genre.trim().lowercase()]
}
