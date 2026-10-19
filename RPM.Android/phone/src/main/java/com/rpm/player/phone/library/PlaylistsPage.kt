package com.rpm.player.phone.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.rpm.player.core.R
import com.rpm.player.core.cloud.CloudTrack
import com.rpm.player.core.cloud.Shelf
import com.rpm.player.core.library.LibraryState
import com.rpm.player.core.library.PlaylistBrowse
import com.rpm.player.core.library.PlaylistRow
import com.rpm.player.core.library.TrackList
import com.rpm.player.core.library.listName
import com.rpm.player.phone.design.BackKey
import com.rpm.player.phone.design.Glyph
import com.rpm.player.phone.design.Ink
import com.rpm.player.phone.design.PageHeading
import com.rpm.player.phone.design.backdropSource
import com.rpm.player.phone.design.display
import com.rpm.player.phone.design.dissolvesAtTop
import com.rpm.player.phone.design.icon
import com.rpm.player.phone.design.rememberBackdrop
import com.rpm.player.phone.design.scrolled
import com.rpm.player.phone.player.QueueRead
import com.rpm.player.phone.ui.VinylGround

/** The gap between the back key and the title beside it (`RPMBackKey.besideTitle` on iPhone). */
private val BESIDE_TITLE = 12.dp

/**
 * The library's playlists view. It matches the iPhone's `PlaylistsPage` (RPMUI/PlaylistsPage.swift). It
 * shows the lists that the computer generated and the lists that the person made, in the folders they were
 * filed in. The folders come first, then the lists. Each row has its picture, its name and how much is in it.
 * Opening a folder shows its contents in place, with a back key beside its name. Opening a list opens its page.
 *
 * The current folder is checked against the lists on every pass. If a folder is emptied while it is open, the
 * reader is moved to the folder that holds it.
 *
 * The queue's row is at the top of the tree and opens the queue. On the person's own shelf, the marked songs'
 * row is under it.
 *
 * @param marked the marked songs, or null on a borrowed shelf, which has no row for them.
 *
 * Not yet implemented: the covers layout and its key, the key that makes a playlist, pull to refresh, and the
 * line that says whose collection a borrowed one is.
 */
@Composable
fun PlaylistsPage(
    ready: LibraryState.Ready,
    images: ImageLoader,
    onMode: (LibraryMode) -> Unit,
    bottomRoom: Dp,
    queue: QueueRead,
    onOpenQueue: () -> Unit,
    marked: List<CloudTrack>?,
    onOpenMarked: () -> Unit,
    onOpen: (TrackList) -> Unit,
) {
    val ground = rememberBackdrop()
    val collection = ready.collection
    val resources = LocalContext.current.resources
    var at by rememberSaveable { mutableStateOf(listOf<String>()) }
    val browse = remember(at, collection, resources, marked != null) {
        PlaylistBrowse.of(at, collection.lists, { listName(it.name, resources) }, marked = marked != null)
    }
    val back = browse.back
    BackHandler(enabled = back != null) { if (back != null) at = back }

    Box(Modifier.fillMaxSize()) {
        VinylGround(Modifier.backdropSource(ground))
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.padding(start = 20.dp, end = 16.dp, bottom = 10.dp)) {
                if (back != null) {
                    BackKey(ground, onBack = { at = back }, modifier = Modifier.padding(end = BESIDE_TITLE))
                }
                PageHeading(browse.at.lastOrNull() ?: stringResource(R.string.Playlists_Title), counts(browse)) {}
            }
            ModeTabs(LibraryMode.Playlists, onMode = onMode, backdrop = ground)
            // A folder is a new place, and a new place starts at the top.
            key(browse.at) {
                val list = rememberLazyListState()
                LazyColumn(
                    state = list,
                    modifier = Modifier.weight(1f).fillMaxWidth().dissolvesAtTop(30.dp) { list.scrolled() },
                    contentPadding = PaddingValues(bottom = FOOT + bottomRoom),
                ) {
                    items(browse.rows, key = { it.id }) { row ->
                        Line(row, queue, marked.orEmpty(), collection.coverModel(row, queue, marked.orEmpty()), images) {
                            when (row) {
                                PlaylistRow.Queue -> onOpenQueue()
                                PlaylistRow.Marked -> onOpenMarked()
                                is PlaylistRow.Folder -> at = row.folder.path
                                is PlaylistRow.Playlist -> onOpen(row.list)
                            }
                        }
                    }
                    item(key = "footer") { Footer(browse, empty = collection.lists.isEmpty(), own = collection.shelf == Shelf.Own) }
                }
            }
        }
    }
}

/**
 * The picture a row shows. A list uses its own picture, or else the cover of the record it opens on. The
 * queue and the marked songs use the cover of their first song. A folder has no picture.
 */
private fun com.rpm.player.core.library.Collection.coverModel(row: PlaylistRow, queue: QueueRead, marked: List<CloudTrack>): Any? = when (row) {
    PlaylistRow.Queue -> queue.entries.firstOrNull()?.cover
    PlaylistRow.Marked -> marked.firstOrNull()?.let { albumOf[it.rpmId] }?.let { cover(it) }
    is PlaylistRow.Folder -> null
    is PlaylistRow.Playlist -> cover(row.list)
}

/**
 * Example: "Mixes  ·  4 playlists". The folders above this one come first, then how many rows it shows.
 */
@Composable
private fun counts(browse: PlaylistBrowse): String {
    val parts = ArrayList<String>()
    if (browse.path.isNotEmpty()) parts += browse.path.joinToString(" / ")
    parts += playlistsCount(browse.rows.size)
    return parts.joinToString("  ·  ")
}

@Composable
private fun playlistsCount(count: Int): String =
    if (count == 1) stringResource(R.string.Stats_PlaylistsOne) else stringResource(R.string.Stats_Playlists, count)

/** One row: the picture, the name over how much is in it, and the chevron on a folder. The row is lit while pressed. */
@Composable
private fun Line(row: PlaylistRow, queue: QueueRead, marked: List<CloudTrack>, picture: Any?, images: ImageLoader, onClick: () -> Unit) {
    val presses = remember { MutableInteractionSource() }
    val pressed by presses.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .background(if (pressed) Ink.white(0x24) else Color.Transparent)
            .clickable(presses, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val glyph = when (row) {
            PlaylistRow.Queue -> Glyph.queue
            PlaylistRow.Marked -> Glyph.heart
            is PlaylistRow.Folder -> Glyph.folder
            is PlaylistRow.Playlist -> Glyph.record
        }
        PlaylistSleeve(picture, glyph, edge = 44.dp, corner = 6.dp, mark = 16f, images)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val (title, detail) = when (row) {
                PlaylistRow.Queue -> stringResource(R.string.AlbumMenu_PlaylistNowPlaying) to holding(queue.entries.size, queue.seconds, 0)
                PlaylistRow.Marked -> stringResource(R.string.Playlists_FavoriteTracks) to holding(marked.size, seconds(marked), 0)
                is PlaylistRow.Folder -> row.folder.name to playlistsCount(row.folder.playlists)
                is PlaylistRow.Playlist -> row.name to holding(row.list.tracks.size, seconds(row.list.tracks), row.list.missing)
            }
            BasicText(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = display(15f).copy(color = Color.White))
            BasicText(detail, maxLines = 1, overflow = TextOverflow.Ellipsis, style = display(11f).copy(color = Ink.white(0x88)))
        }
        if (row is PlaylistRow.Folder) BasicText(Glyph.into, style = icon(12f).copy(color = Ink.white(0x55)))
    }
}

/**
 * The message to show where there are no rows. It is shown inside an empty folder, or when the account has no lists.
 */
@Composable
private fun Footer(browse: PlaylistBrowse, empty: Boolean, own: Boolean) {
    when {
        browse.at.isNotEmpty() && browse.rows.isEmpty() -> BasicText(
            stringResource(R.string.Playlists_FolderEmpty),
            Modifier.fillMaxWidth().padding(40.dp),
            style = display(13f).copy(color = Ink.white(0x88), textAlign = TextAlign.Center),
        )
        browse.at.isEmpty() && empty && own -> BasicText(
            stringResource(R.string.Rpm_Playlists_EmptyHere),
            Modifier.padding(horizontal = 20.dp).padding(top = 18.dp),
            style = display(12f).copy(color = Ink.white(0x77)),
        )
    }
}

/**
 * The square that a list or a song shows. It matches the iPhone's `PlaylistSleeve`. It is a dark well with a
 * thin edge and a faint icon. The picture is drawn over it once it has loaded. The picture fills the square
 * and is cropped from the bottom, as a cover is.
 *
 * @param picture a list's picture ([com.rpm.player.core.library.ListArt]) or a record's cover. Null leaves only the
 * icon.
 */
@Composable
fun PlaylistSleeve(picture: Any?, glyph: String, edge: Dp, corner: Dp, mark: Float, images: ImageLoader) {
    val shape = RoundedCornerShape(corner)
    Box(
        Modifier.size(edge).clip(shape).background(Color.Black.copy(alpha = 0x22 / 255f)).border(1.dp, Ink.cardBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(glyph, style = icon(mark).copy(color = Ink.white(0x3A)))
        if (picture != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(picture).build(),
                contentDescription = null,
                imageLoader = images,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The songs' total length, in seconds. */
fun seconds(tracks: List<CloudTrack>): Int = tracks.sumOf { it.tags?.durationSeconds ?: 0.0 }.toInt()

/**
 * Example: "24 songs  ·  1 hour 32 min  ·  2 songs not uploaded yet". This matches the iPhone's
 * `PlaylistWords.holding`. Missing songs are said, so a short list does not look complete.
 */
@Composable
fun holding(songs: Int, seconds: Int, missing: Int): String {
    var said = if (songs == 1) stringResource(R.string.Stats_SongsOne) else stringResource(R.string.Stats_Songs, songs)
    if (seconds > 0) said += "  ·  " + runtime(seconds)
    if (missing > 0) said += "  ·  " + waitingOn(missing)
    return said
}

/** Example: "2 songs not uploaded yet". These are songs that the list names but the library cannot find. */
@Composable
fun waitingOn(missing: Int): String =
    if (missing == 1) stringResource(R.string.Rpm_Playlist_MissingOne) else stringResource(R.string.Rpm_Playlist_Missing, missing)

/** A list's length as one localised sentence: hours and minutes, or minutes only. */
@Composable
private fun runtime(seconds: Int): String {
    val hours = seconds / 3600
    if (hours < 1) {
        val minutes = seconds / 60
        return if (minutes == 1) stringResource(R.string.Rpm_Playlists_LenMinuteOne) else stringResource(R.string.Playlists_LenMinutes, minutes)
    }
    val rest = (seconds % 3600) / 60
    return if (hours == 1) stringResource(R.string.Rpm_Playlists_LenHourOne, rest) else stringResource(R.string.Playlists_LenHours, hours, rest)
}
