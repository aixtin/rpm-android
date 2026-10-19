package com.rpm.player.core.library

/** One genre on the genres list: its name, and its albums by key in the library's order. */
class GenreWallItem(
    /** One of the app's shelves, or [GenreWall.UNCLASSIFIED]. */
    val name: String,
    val albums: List<String>,
)

/**
 * The collection read by what kind of music it is. Same rules as the iOS app's `GenreWall` (Genres.swift): each
 * album is on the genre most of its tracks are tagged with, matched to a shelf ([Genres.match]); no tag, or
 * a tag no shelf answers to, is [UNCLASSIFIED]. The order is the shelves' own, with [UNCLASSIFIED] last.
 */
object GenreWall {
    /** The card for music the app cannot file. The desktop's word, shown as it is. */
    const val UNCLASSIFIED = "Other"

    fun build(albums: List<CloudAlbum>): List<GenreWallItem> {
        val filed = HashMap<String, MutableList<String>>()
        for (album in albums) filed.getOrPut(genreOf(album)) { ArrayList() }.add(album.key)
        val order = SubgenreTable.shelves.filter { it != Genres.UNKNOWN } + UNCLASSIFIED
        return order.mapNotNull { name -> filed[name]?.let { GenreWallItem(name, it) } }
    }

    /** The computer's mood lists cut to [genre] and not to a decade as well: a list is cut by one or the other,
     * never both (the iPhone's genre page). In the order the lists came. */
    fun moods(genre: String, lists: List<TrackList>): List<TrackList> =
        lists.filter { it.generated && it.genre == genre && it.decade == 0 }

    /** The genre most of an album's tracks are tagged with, a tie going to the tag met first. */
    fun genreOf(album: CloudAlbum): String {
        val counts = LinkedHashMap<String, Int>()
        for (track in album.tracks) {
            val tag = track.tags?.genre?.trim()
            if (tag.isNullOrEmpty()) continue
            counts[tag] = (counts[tag] ?: 0) + 1
        }
        var most: String? = null
        for ((tag, count) in counts) if (most == null || count > (counts[most] ?: 0)) most = tag
        return Genres.match(most) ?: UNCLASSIFIED
    }
}

/**
 * Everything one genre's page shows, built in one pass. Same as the iOS app's `GenreShelves`, with the
 * desktop page's shelf length (20).
 */
class GenreShelves(
    /** The newest albums, newest first. */
    val added: List<String>,
    /** The acts with the most of their own records on the genre, most first. */
    val acts: List<ArtistWallItem>,
    /** How many acts have a record of their own on it. */
    val actCount: Int,
    /** Related genres the collection holds, most albums first. */
    val related: List<GenreWallItem>,
    /** The albums by decade, oldest decade first, newest album first in each. */
    val eras: List<Pair<Int, List<String>>>,
) {
    companion object {
        /** How many cards a shelf holds: the home rows' figure, as on the desktop page. */
        const val LENGTH = 20

        fun of(genre: GenreWallItem, wall: List<GenreWallItem>, albums: Map<String, CloudAlbum>, artists: List<ArtistWallItem>): GenreShelves {
            val records = genre.albums.mapNotNull { albums[it] }
            val keys = genre.albums.toHashSet()

            val added = records
                .map { album -> album.key to (album.tracks.maxOfOrNull { it.addedAt } ?: 0L) }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(LENGTH)
                .map { it.first }

            // Counted on this genre: an act with four hundred rock songs and two jazz records is not who the
            // jazz page is about.
            val counted = artists.mapNotNull { artist -> artist.own.count { it in keys }.takeIf { it > 0 }?.let { artist to it } }
            val acts = counted
                .sortedWith(
                    compareByDescending<Pair<ArtistWallItem, Int>> { it.second }
                        .thenByDescending { it.first.tracks }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.first.name },
                )
                .take(LENGTH)
                .map { it.first }

            val byName = wall.associateBy { it.name }
            val related = Genres.related(genre.name).mapNotNull { byName[it] }.sortedByDescending { it.albums.size }

            val byDecade = HashMap<Int, MutableList<Pair<String, Int>>>()
            for (record in records) {
                val year = AlbumYears.yearOf(record.tracks)
                if (year <= 0) continue
                byDecade.getOrPut(year / 10 * 10) { ArrayList() }.add(record.key to year)
            }
            val eras = byDecade.keys.sorted().map { decade ->
                decade to byDecade.getValue(decade).sortedByDescending { it.second }.take(LENGTH).map { it.first }
            }

            return GenreShelves(added, acts, counted.size, related, eras)
        }
    }
}

/**
 * The artists and genres lists of one load, built once in [LibraryRepository]'s build off the main thread,
 * beside home. Equal only to itself, as [Collection] is: each load is its own.
 */
class Walls(val artists: List<ArtistWallItem>, val genres: List<GenreWallItem>) {
    private val artistByName: Map<String, ArtistWallItem> by lazy { artists.associateBy { NameMatch.squash(it.name) } }
    private val genreByName: Map<String, GenreWallItem> by lazy { genres.associateBy { it.name } }

    fun artist(name: String): ArtistWallItem? = artistByName[NameMatch.squash(name)]
    fun genre(name: String): GenreWallItem? = genreByName[name]

    companion object {
        fun of(albums: List<CloudAlbum>): Walls = Walls(ArtistWall.build(ArtistWall.rows(albums)), GenreWall.build(albums))
    }
}
