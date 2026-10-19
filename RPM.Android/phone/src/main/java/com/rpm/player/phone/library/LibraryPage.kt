package com.rpm.player.phone.library

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import com.rpm.player.core.library.SongSearch
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import com.rpm.player.core.R
import com.rpm.player.core.library.AlbumOrdering
import com.rpm.player.core.cloud.CloudTrack
import com.rpm.player.core.library.AlbumSort
import com.rpm.player.core.library.ArtistWallItem
import com.rpm.player.core.library.Artwork
import com.rpm.player.core.library.CloudAlbum
import com.rpm.player.core.library.GenreWallItem
import com.rpm.player.core.library.LibraryState
import com.rpm.player.core.library.TrackList
import com.rpm.player.phone.PhoneSettings
import com.rpm.player.phone.player.QueueRead
import com.rpm.player.phone.design.Backdrop
import com.rpm.player.phone.design.GlassCard
import com.rpm.player.phone.design.Glyph
import com.rpm.player.phone.design.Ink
import com.rpm.player.phone.design.PageHeading
import com.rpm.player.phone.design.backdropSource
import com.rpm.player.phone.design.chromeGlass
import com.rpm.player.phone.design.display
import com.rpm.player.phone.design.icon
import com.rpm.player.phone.design.rememberBackdrop
import com.rpm.player.phone.design.system
import com.rpm.player.phone.ui.ArtistMark
import com.rpm.player.phone.ui.MarkView
import com.rpm.player.phone.ui.VinylGround
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * How the library tab is shown. It matches the iPhone's `LibraryMode` (RPMUI/LibraryMode.swift), in the same order. The
 * names are the values that are stored.
 */
enum class LibraryMode(val stored: String, @StringRes val label: Int) {
    Albums("albums", R.string.Artists_Albums),
    Artists("artists", R.string.Artists_Title),
    Playlists("playlists", R.string.Playlists_Title),
    Genres("genres", R.string.Genres_Title);

    companion object {
        fun of(stored: String?): LibraryMode? = entries.firstOrNull { it.stored == stored }
    }
}

/** How long typing must pause before the library is searched. The iPhone app uses 140 ms too. */
private const val SEARCH_PAUSE_MS = 140L

/**
 * The orders that the wall's menu offers, as on iPhone. The "plays" order is on the TV and the desktop, not on the
 * phone, so it is left out.
 */
private val OFFERED = listOf(
    AlbumOrdering.Artist to R.string.AlbumSort_Artist,
    AlbumOrdering.Album to R.string.AlbumSort_Album,
    AlbumOrdering.Added to R.string.AlbumSort_Added,
    AlbumOrdering.Year to R.string.AlbumSort_Year,
)

/**
 * The library tab. It matches the iPhone's `LibraryPage` (RPMUI/LibraryPage.swift). From top to bottom: the
 * title, the counts, and the keys on the title's line. Then the row of views, and then the wall.
 *
 * The keys and the row of views are glass over the page's background only. Before iOS 26 the header sits
 * above the wall, not over it, so nothing else is behind them. The sort menu is glass over the whole page.
 *
 * The playlists, artists and genres views are pages of their own, with their own titles, as on iPhone.
 *
 * The search key opens a box under the header. The search results replace the wall until the box is closed.
 * The results are the records that the words name, which open, and the songs that they find, which play their
 * record from that song.
 *
 * Not yet implemented: pinch to resize the wall, pull to refresh, and a song's menu on the results.
 *
 * @param bottomRoom how much of the bottom of the page the chrome covers.
 */
@Composable
fun LibraryPage(
    ready: LibraryState.Ready,
    images: ImageLoader,
    settings: PhoneSettings,
    bottomRoom: Dp,
    artwork: Artwork,
    onOpen: (CloudAlbum) -> Unit,
    onOpenList: (TrackList) -> Unit,
    onOpenArtist: (ArtistWallItem) -> Unit,
    onOpenGenre: (GenreWallItem) -> Unit,
    queue: QueueRead,
    onOpenQueue: () -> Unit,
    marked: List<CloudTrack>?,
    onOpenMarked: () -> Unit,
    playing: String?,
    onPlaySong: (SongSearch.SongHit) -> Unit,
) {
    when (settings.libraryMode) {
        LibraryMode.Playlists -> {
            PlaylistsPage(ready, images, onMode = settings::changeLibraryMode, bottomRoom = bottomRoom, queue = queue, onOpenQueue = onOpenQueue, marked = marked, onOpenMarked = onOpenMarked, onOpen = onOpenList)
            return
        }
        LibraryMode.Artists -> {
            ArtistsPage(ready, images, artwork, settings, bottomRoom = bottomRoom, onOpen = onOpenArtist)
            return
        }
        LibraryMode.Genres -> {
            GenresPage(ready, images, settings, bottomRoom = bottomRoom, onOpen = onOpenGenre)
            return
        }
        LibraryMode.Albums -> Unit
    }
    val ground = rememberBackdrop()
    val page = rememberBackdrop()
    var sorting by remember { mutableStateOf(false) }
    val order = settings.libraryOrder
    val mode = settings.libraryMode
    // Hidden until the search key is pressed, as on iPhone. The words are cleared when the search is closed.
    // Opening it again starts a new search.
    var searching by rememberSaveable { mutableStateOf(false) }
    // The search key sets this. The search box clears it once it has the keyboard. Without that, the box would
    // raise the keyboard again when it is drawn again, for example on the way back from a page opened from the results.
    var raise by remember { mutableStateOf(false) }
    var asked by rememberSaveable { mutableStateOf("") }
    var answer by remember { mutableStateOf(SearchAnswer.NONE) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    fun closeSearch() {
        asked = ""
        answer = SearchAnswer.NONE
        focusManager.clearFocus()
        searching = false
    }
    BackHandler(enabled = searching) { closeSearch() }

    // Sort off the main thread. Sorting the whole collection takes too long for one frame.
    val ordered by produceState<List<CloudAlbum>?>(null, ready.collection, order) {
        value = withContext(Dispatchers.Default) { AlbumSort.by(ready.albums, order, AlbumSort::factsOf) }
    }

    // A keystroke alone is not a question to answer. The search waits for the typing to pause, then searches the
    // library off the main thread. It is keyed on the wall as well as the words, so a library that arrives during
    // a search is searched again. A new keystroke cancels the search under way. It stops at the next record. So
    // only the answer to the current words is shown.
    LaunchedEffect(asked, ordered) {
        val wall = ordered ?: return@LaunchedEffect
        if (asked.isBlank()) {
            answer = SearchAnswer(asked, emptyList(), emptyList())
            return@LaunchedEffect
        }
        delay(SEARCH_PAUSE_MS)
        val found = withContext(Dispatchers.Default) { SearchAnswer(asked, SongSearch.records(wall, asked), SongSearch.through(wall, asked)) }
        answer = found
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().backdropSource(page)) {
            VinylGround(Modifier.backdropSource(ground))
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Box(Modifier.padding(start = 20.dp, end = 16.dp, bottom = 10.dp)) {
                    PageHeading(stringResource(R.string.Settings_Library), counts(ready.albums)) {
                        KeyGroup(ground) {
                            HeaderKey(Glyph.search) { if (searching) closeSearch() else { searching = true; raise = true } }
                            HeaderKey(Glyph.sort) { sorting = true }
                        }
                    }
                }
                AnimatedVisibility(searching, enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
                    SearchRow(asked, { asked = it }, stringResource(R.string.Lookup_SearchButton), focus, raise, onRaised = { raise = false }, ground, onClose = ::closeSearch)
                }
                ModeTabs(mode, onMode = settings::changeLibraryMode, backdrop = ground)
                // Kept above the keyboard while it is open, so the answers stay in view.
                Box(Modifier.weight(1f).imePadding()) {
                    when {
                        mode != LibraryMode.Albums -> Unit
                        searching && asked.isNotEmpty() -> SearchResults(asked, answer, ready.collection, images, playing, bottomRoom, onOpen, onPlaySong)
                        ready.albums.isEmpty() -> Empty()
                        else -> ordered?.let { wall ->
                            AlbumWall(wall, { ready.collection.cover(it) }, images, targetStride = 194.0, bottomRoom = bottomRoom, onOpen = onOpen)
                        }
                    }
                }
            }
        }
        if (sorting) {
            SortMenu(page, OFFERED, order, onPick = { settings.changeLibraryOrder(it); sorting = false }, onClose = { sorting = false })
        }
    }
}

/** Example: "1,610 albums, 23,329 tracks". This is the counts line under the title. */
@Composable
private fun counts(albums: List<CloudAlbum>): String {
    val records = if (albums.size == 1) stringResource(R.string.Stats_AlbumsOne) else stringResource(R.string.Stats_Albums, albums.size)
    val songs = albums.sumOf { it.tracks.size }
    val tracks = if (songs == 1) stringResource(R.string.Rpm_Profile_TracksOne) else stringResource(R.string.Rpm_Queue_Count, songs)
    return stringResource(R.string.Rpm_Library_CountsJoin, records, tracks)
}

/** Keys on one glass capsule. The iPhone's `rpmGlassGroup` groups a header's keys in the same way. */
@Composable
internal fun KeyGroup(backdrop: Backdrop, keys: @Composable () -> Unit) {
    Row(Modifier.chromeGlass(backdrop, RoundedCornerShape(percent = 50)).padding(horizontal = 2.dp)) { keys() }
}

/** One plain key in a [KeyGroup]. */
@Composable
internal fun HeaderKey(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(glyph, style = icon(16f).copy(color = Ink.white(0xB8)))
    }
}

/**
 * The row of views under the header. It matches the iPhone's `LibraryModeTabs`. It is the tab capsule at a
 * smaller size. Only the chosen view shows its name beside its icon, so that all four views fit in every
 * language. The pill slides between the views with the same spring as the tab bar.
 */
@Composable
internal fun ModeTabs(mode: LibraryMode, onMode: (LibraryMode) -> Unit, backdrop: Backdrop) {
    val capsule = RoundedCornerShape(percent = 50)
    val unlit = 52.dp
    BoxWithConstraints(
        Modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp)
            .fillMaxWidth()
            .chromeGlass(backdrop, capsule)
            .padding(3.dp),
    ) {
        val lit = maxWidth - unlit * (LibraryMode.entries.size - 1)
        val pill by animateDpAsState(unlit * mode.ordinal, spring(dampingRatio = 0.8f, stiffness = 322f), label = "pill")
        Box(Modifier.offset(x = pill).width(lit).height(32.dp).clip(capsule).background(Ink.white(0x24)))
        Row {
            for (choice in LibraryMode.entries) {
                val chosen = choice == mode
                val width by animateDpAsState(if (chosen) lit else unlit, spring(dampingRatio = 0.8f, stiffness = 322f), label = "tab")
                Row(
                    Modifier
                        .width(width)
                        .height(32.dp)
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { onMode(choice) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val ink = if (chosen) Color.White else Ink.white(0x99)
                    when (choice) {
                        LibraryMode.Albums -> BasicText(Glyph.record, style = icon(14f).copy(color = ink))
                        LibraryMode.Artists -> MarkView(ArtistMark, ink, Modifier.size(14.dp))
                        LibraryMode.Playlists -> BasicText(Glyph.playlists, style = icon(14f).copy(color = ink))
                        LibraryMode.Genres -> BasicText(Glyph.genres, style = icon(14f).copy(color = ink))
                    }
                    if (chosen) {
                        Spacer(Modifier.width(6.dp))
                        BasicText(stringResource(choice.label), maxLines = 1, style = system(12f, FontWeight.SemiBold).copy(color = Color.White))
                    }
                }
            }
        }
    }
}

/** A collection that is known to be empty. It is not a collection that has not arrived yet. */
@Composable
private fun Empty() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(Glyph.record, style = icon(44f).copy(color = Ink.white(0x3A)))
        BasicText(
            stringResource(R.string.Rpm_Library_Empty),
            Modifier.widthIn(max = 300.dp),
            style = display(14f).copy(color = Ink.white(0x88), textAlign = TextAlign.Center),
        )
    }
}

/**
 * The menu that picks the order of a wall, as on iPhone. It lists the orders, and ticks the one in use. It is
 * glass over the page, under the keys. A tap anywhere else closes it.
 *
 * @param choices each order with the name the menu gives it.
 */
@Composable
internal fun <T> SortMenu(backdrop: Backdrop, choices: List<Pair<T, Int>>, order: T, onPick: (T) -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose)) {
        GlassCard(
            backdrop,
            Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 48.dp, end = 16.dp)
                .width(230.dp),
            radius = 14.dp,
        ) {
            Column(Modifier.padding(vertical = 6.dp)) {
                for ((choice, label) in choices) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton) { onPick(choice) }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(stringResource(label), Modifier.weight(1f), style = system(15f).copy(color = Color.White))
                        if (choice == order) BasicText(Glyph.check, style = icon(13f).copy(color = Color.White))
                    }
                }
            }
        }
    }
}
