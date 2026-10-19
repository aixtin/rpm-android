package com.rpm.player.core.library

import java.text.Collator
import java.util.Locale

/**
 * One folder as a row. Matches the iPhone's `PlaylistFolderRow`.
 *
 * @param name the last step of its own path.
 * @param path the whole way down to it, outermost first: what stepping into it makes the open folder.
 * @param playlists how many rows opening it shows: the folders directly in it and the playlists directly in it.
 */
data class PlaylistFolderRow(val name: String, val path: List<String>, val playlists: Int)

/**
 * Where playlists sit in their folders, as the iPhone reads it (`PlaylistFolderTree`). A folder is only ever a
 * step in some playlist's chain; there is no folder holding nothing. Folder names are compared ignoring case, so
 * "rock" and "Rock" are one folder, and the first spelling seen is the one shown.
 */
object PlaylistFolderTree {

    /** Whether a playlist filed under [folder] is directly in [at]. */
    fun isIn(folder: List<String>, at: List<String>): Boolean = folder.size == at.size && beneath(folder, at)

    /** Whether a playlist filed under [folder] is in [at] or in any folder inside it. */
    fun isUnder(folder: List<String>, at: List<String>): Boolean = folder.size >= at.size && beneath(folder, at)

    /** The folders directly in [at], by name. */
    fun foldersIn(folders: List<List<String>>, at: List<String>): List<PlaylistFolderRow> {
        val spelling = LinkedHashMap<String, String>()
        for (folder in folders) {
            if (folder.size <= at.size || !beneath(folder, at)) continue
            val name = folder[at.size]
            spelling.getOrPut(fold(name)) { name }
        }
        return spelling.values
            .map { name -> (at + name).let { path -> PlaylistFolderRow(name, path, rowsIn(folders, path)) } }
            .sortedWith(compareBy(collator()) { it.name })
    }

    /** How many rows [at] shows: the folders directly in it, and the playlists directly in it. */
    fun rowsIn(folders: List<List<String>>, at: List<String>): Int {
        val children = HashSet<String>()
        var here = 0
        for (folder in folders) {
            if (folder.size < at.size || !beneath(folder, at)) continue
            if (folder.size == at.size) here++ else children += fold(folder[at.size])
        }
        return children.size + here
    }

    /**
     * Where somebody standing in [at] stands now that the playlists are filed under [folders]: [at] while
     * anything is still under it, else the nearest folder above it that still holds something, else the top.
     * A folder whose last playlist went elsewhere is not left open and empty.
     */
    fun stillOpen(at: List<String>, folders: List<List<String>>): List<String> {
        if (at.isEmpty()) return at
        for (depth in at.size downTo 1) {
            val here = at.subList(0, depth).toList()
            if (folders.any { isUnder(it, here) }) return here
        }
        return emptyList()
    }

    private fun beneath(folder: List<String>, at: List<String>): Boolean = at.indices.all { fold(folder[it]) == fold(at[it]) }

    private fun fold(name: String): String = name.lowercase(Locale.getDefault())
}

/** Names in the reader's language, ignoring case and not accents, as the iPhone's case-insensitive compare. */
internal fun collator(): Collator = Collator.getInstance().apply { strength = Collator.SECONDARY }

/** One row of the playlists view. Matches the iPhone's `PlaylistRow`, for the kinds this app has. */
sealed interface PlaylistRow {
    /** Unique among the rows of one view: a folder's whole path, or the list's own id. */
    val id: String

    /** The queue: what is playing and what comes after it. Always at the top of the tree, empty or not, because
     * it is where the songs anybody sent up next went. */
    data object Queue : PlaylistRow {
        override val id get() = "\u0001queue"
    }

    /** The songs the person marked as favourites ([MarkedSongs]), under the queue on their own shelf. */
    data object Marked : PlaylistRow {
        override val id get() = "\u0001favorites"
    }

    /** A folder; a press steps into it rather than opening anything. */
    data class Folder(val folder: PlaylistFolderRow) : PlaylistRow {
        override val id get() = "\u0001folder/" + folder.path.joinToString("/")
    }

    /**
     * A list, under the name it is shown by, in the folder it is shown in. [folder] is the list's own, except for
     * a mood's own list, which is shown one level down, in the folder named after it ([PlaylistBrowse.placed]).
     */
    data class Playlist(val list: TrackList, val name: String, val folder: List<String>) : PlaylistRow {
        override val id get() = list.id
    }
}

/**
 * What the playlists view shows standing in one folder. Matches the iPhone's `PlaylistBrowse`. [at] is where the
 * reader actually stands, which is not always where they asked to be: if a folder empties while they are in it,
 * they are put in the folder that held it.
 */
data class PlaylistBrowse(val at: List<String>, val rows: List<PlaylistRow>) {

    /** The folders above this one, for the line under its name; empty at the top and one step in, where the
     * name alone is the whole answer. */
    val path: List<String> get() = if (at.size > 1) at.dropLast(1) else emptyList()

    /** Where the back key goes; null at the top of the tree, where there is no back key. */
    val back: List<String>? get() = if (at.isEmpty()) null else at.dropLast(1)

    companion object {
        /**
         * At the top of the tree the queue and the marked songs, then the folders directly in [at], then the lists directly in it. A
         * generated list named after the folder it is in is that folder's own list (`GeneratedLists.isPooled`) and
         * comes first; the rest are by name.
         *
         * @param nameOf the name each list is shown by, which is also what the lists are put in order by.
         *
         * @param marked whether the marked songs have a row: on the person's own shelf, and not on a borrowed one,
         * whose songs this account cannot mark.
         */
        fun of(at: List<String>, lists: List<TrackList>, nameOf: (TrackList) -> String, marked: Boolean): PlaylistBrowse {
            val shown = placed(lists.map { PlaylistRow.Playlist(it, nameOf(it), it.folder) })
            val folders = shown.map { it.folder }
            val here = PlaylistFolderTree.stillOpen(at, folders)
            val rows = ArrayList<PlaylistRow>()
            if (here.isEmpty()) {
                rows += PlaylistRow.Queue
                if (marked) rows += PlaylistRow.Marked
            }
            PlaylistFolderTree.foldersIn(folders, here).mapTo(rows) { PlaylistRow.Folder(it) }
            val byName = collator()
            shown.filter { PlaylistFolderTree.isIn(it.folder, here) }
                .sortedWith(compareByDescending<PlaylistRow.Playlist> { isPooled(it) }.thenBy(byName) { it.name })
                .let(rows::addAll)
            return PlaylistBrowse(here, rows)
        }

        /**
         * The one departure from the arrangement the computer sent (`GeneratedLists.sunk`): a generated list whose
         * name is also a folder of generated lists in the same place moves into that folder. A mood with cuts under
         * it is a place, and a row cannot both open a list and step into a folder, so the mood's own list moves into
         * the place named after it. A mood with no cuts stays a plain row.
         */
        private fun placed(rows: List<PlaylistRow.Playlist>): List<PlaylistRow.Playlist> {
            val places = rows.filter { it.list.generated && it.folder.isNotEmpty() }.map { it.folder }
            return rows.map { row ->
                val below = row.folder + row.name
                if (row.list.generated && places.any { same(it, below) }) row.copy(folder = below) else row
            }
        }

        private fun same(a: List<String>, b: List<String>): Boolean =
            a.size == b.size && a.indices.all { a[it].lowercase(Locale.getDefault()) == b[it].lowercase(Locale.getDefault()) }

        /** A generated list whose name is the name of the folder it is shown in: the row somebody opened that folder
         * looking for, so it sorts to the top. A stored playlist named after its folder is not moved. */
        private fun isPooled(row: PlaylistRow.Playlist): Boolean =
            row.list.generated && row.folder.lastOrNull()?.lowercase(Locale.getDefault()) == row.name.lowercase(Locale.getDefault())
    }
}
