package com.rpm.player.tv.ui

import com.rpm.player.core.playback.Queued
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.max
import coil3.ImageLoader
import coil3.request.ImageRequest
import com.rpm.player.core.R
import com.rpm.player.core.library.ArtistCredit
import com.rpm.player.core.library.ArtistWallItem
import com.rpm.player.core.library.Artwork
import com.rpm.player.core.library.CloudAlbum
import com.rpm.player.core.library.Collection
import com.rpm.player.core.library.LibraryHits
import com.rpm.player.core.library.LibrarySearch
import com.rpm.player.core.library.TrackHit
import com.rpm.player.core.library.Walls
import com.rpm.player.core.radio.RadioQuery
import com.rpm.player.core.radio.RadioRepository
import com.rpm.player.core.radio.RadioResults
import com.rpm.player.core.radio.RadioStation
import com.rpm.player.core.radio.StationLogo
import com.rpm.player.core.radio.StationRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

/** What the radio directory answered for the query: nothing asked yet, a search under way, the stations, or a failure. */
private sealed interface StationHits {
    data object Idle : StationHits
    data object Searching : StationHits
    data class Found(val stations: List<RadioStation>) : StationHits
    data object Failed : StationHits
}

/** What the collection answered, and which search it came from. An answer from the index of another collection is not drawn. */
private class LibraryFound(val index: LibrarySearch, val hits: LibraryHits)

/**
 * The search that the side menu opens, in the look of the radio station search (`StationSearch` in Radio.kt): a
 * `#33000000` shade over the room, a column `max(560, w / 2)` wide in the middle, the search field (`64·s` high,
 * corner 12·s, the light glass, 26·s type) with the go key beside it, and under it a card of what matches. The
 * card is a list with a heading for each kind: artists, albums, tracks, then radio stations. The field stands
 * in the middle of the screen until there is something to show, then moves to the top.
 *
 * The collection is searched on the TV ([LibrarySearch], built off the main thread when the screen opens) 150 ms
 * after typing stops. The stations are asked of the radio directory 450 ms after typing stops, as the station
 * search does. Each search is an effect keyed on the query, so the next keystroke cancels the search before it,
 * and the two answers can arrive in either order. While a new answer is on its way, the last one stays up.
 *
 * OK on an artist or an album opens its page, OK on a track plays the track, and OK on a station tunes it. A
 * held OK on a track opens the track menu (Play, Play next, Add to Now playing, a favourite, a playlist), and
 * on a station it opens the station menu (Play, a favourite). Left and Right stay in the list, so Left there
 * opens the side menu.
 *
 * The panels have no blurred picture under them, unlike the station search. This is a screen of its own, and
 * a picture captured as it opens would show whichever screen was open before, not home, where Back goes.
 */
@Composable
fun SearchScreen(
    collection: Collection,
    walls: Walls,
    radio: RadioRepository,
    rules: StationRules,
    images: ImageLoader,
    artwork: Artwork,
    actions: TrackActions,
    onOpenArtist: (ArtistWallItem) -> Unit,
    onOpenAlbum: (CloudAlbum) -> Unit,
    onTune: (RadioStation) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val field = remember { FocusRequester() }
    val firstFound = remember { FocusRequester() }
    val menu = rememberRowMenu()
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }

    // Folds every name in the collection, so it is built off the main thread, once per collection.
    val index by produceState<LibrarySearch?>(null, collection, walls) {
        value = null
        value = withContext(Dispatchers.Default) { LibrarySearch.of(collection.albums, walls.artists) }
    }
    var library by remember { mutableStateOf<LibraryFound?>(null) }
    LaunchedEffect(query, index) {
        val text = query.trim()
        val searched = index ?: return@LaunchedEffect
        if (text.isEmpty()) return@LaunchedEffect
        delay(LIBRARY_PAUSE_MS)
        library = LibraryFound(searched, withContext(Dispatchers.Default) { searched.find(text) })
    }
    var stations by remember { mutableStateOf<StationHits>(StationHits.Idle) }
    LaunchedEffect(query) {
        val text = query.trim()
        if (text.isEmpty()) {
            stations = StationHits.Idle
            return@LaunchedEffect
        }
        delay(STATIONS_PAUSE_MS)
        stations = StationHits.Searching
        stations = try {
            StationHits.Found(RadioResults.onePerStation(radio.browser.search(RadioQuery(name = text, tag = null, countryCode = null, limit = STATIONS_ASKED))))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            StationHits.Failed
        }
    }

    val typed = query.isNotBlank()
    // Read on every draw: an answer for another collection, or for a query that has since been emptied, is not shown.
    val hits = library?.takeIf { typed && it.index === index }?.hits
    val shownStations = if (typed) stations else StationHits.Idle
    val showing = typed && (hits != null || shownStations !is StationHits.Idle)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().background(Color(0x33000000)))
        val column = max(560.px, maxWidth * 0.5f)
        val fieldH = 64.s
        val top = if (showing) 64.px else max(64.px, maxHeight / 2 - fieldH / 2)
        Column(Modifier.align(Alignment.TopCenter).padding(top = top, bottom = 34.s).width(column)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.s)) {
                GlassPanel(null, 12.s, Modifier.weight(1f).height(fieldH)) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = ui(26.ts).copy(color = Ink.Words),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { runCatching { firstFound.requestFocus() } }),
                        modifier = Modifier
                            .focusRequester(field)
                            // A single-line field takes Down itself (it moves to the end of the line), so the way into
                            // the list is made here, as on the album wall's search. With no list yet, the field keeps the key.
                            .onPreviewKeyEvent { event ->
                                event.key == Key.DirectionDown && event.type == KeyEventType.KeyDown &&
                                    runCatching { firstFound.requestFocus() }.isSuccess
                            }
                            .deskFocus(12.s)
                            .align(Alignment.CenterStart)
                            .fillMaxWidth()
                            .padding(horizontal = 22.s),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) Text(stringResource(R.string.Tv_Search_Placeholder), style = ui(26.ts), color = Ink.Artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                inner()
                            }
                        },
                    )
                }
                val shape = RoundedCornerShape(12.s)
                DeskButton(
                    onClick = { runCatching { firstFound.requestFocus() } },
                    corner = 12.s,
                    modifier = Modifier.size(fieldH).background(Color(0x14FFFFFF), shape).border(1.px, Color(0xB3FFFFFF), shape),
                ) { Glyph(Fa.SEARCH, 22.ts, Ink.Words) }
            }
            if (showing) {
                GlassPanel(null, 12.s, Modifier.padding(top = 12.s).fillMaxWidth().weight(1f)) {
                    Results(
                        hits = hits,
                        stations = shownStations,
                        collection = collection,
                        radio = radio,
                        rules = rules,
                        images = images,
                        artwork = artwork,
                        actions = actions,
                        menu = menu,
                        firstFound = firstFound,
                        onOpenArtist = onOpenArtist,
                        onOpenAlbum = onOpenAlbum,
                        onTune = onTune,
                    )
                }
            }
        }
        RowMenu(menu)
    }
}

/**
 * The card's contents. It shows the turning record while no answer has arrived, "Nothing matches your search."
 * when the collection and the directory both found nothing, and otherwise the list: a heading and the rows for
 * each kind that found something. The stations' heading is shown while they are being asked or the request
 * failed, with the turning record or the failure message under it, so the collection's rows are not held back
 * for the directory.
 */
@Composable
private fun Results(
    hits: LibraryHits?,
    stations: StationHits,
    collection: Collection,
    radio: RadioRepository,
    rules: StationRules,
    images: ImageLoader,
    artwork: Artwork,
    actions: TrackActions,
    menu: RowMenuState,
    firstFound: FocusRequester,
    onOpenArtist: (ArtistWallItem) -> Unit,
    onOpenAlbum: (CloudAlbum) -> Unit,
    onTune: (RadioStation) -> Unit,
) {
    val library = hits ?: LibraryHits.NONE
    val found = (stations as? StationHits.Found)?.let { rules.shown(it.stations).take(STATIONS_MOST) }.orEmpty()
    if (library.isEmpty && (hits == null || stations == StationHits.Idle || stations == StationHits.Searching)) {
        // At the top of the card, above the TV's keyboard, as in the station search.
        Box(Modifier.fillMaxSize().padding(top = NOTHING_YET_TOP), contentAlignment = Alignment.TopCenter) {
            RecordSpinner(Modifier.size(46.s))
        }
        return
    }
    if (library.isEmpty && found.isEmpty() && stations is StationHits.Found) {
        Box(Modifier.fillMaxSize().padding(top = NOTHING_YET_TOP), contentAlignment = Alignment.TopCenter) {
            Text(stringResource(R.string.Tv_Search_NoResults), style = ui(18.ts), color = Color(0xCCFFFFFF), textAlign = TextAlign.Center)
        }
        return
    }
    // The first row of the list, whatever its kind, is where OK on the keyboard and the go key move the focus.
    val first: Any? = library.artists.firstOrNull() ?: library.albums.firstOrNull() ?: library.tracks.firstOrNull() ?: found.firstOrNull()
    val firstOf: (Any) -> Modifier = { item -> if (item === first) Modifier.focusRequester(firstFound) else Modifier }
    // The section at the top. Its heading has less space above it than the headings between sections.
    val top = when {
        library.artists.isNotEmpty() -> R.string.Tv_Search_Artists
        library.albums.isNotEmpty() -> R.string.Tv_Search_Albums
        library.tracks.isNotEmpty() -> R.string.Tv_Search_Tracks
        else -> R.string.Tv_Search_Stations
    }
    LazyColumn(contentPadding = PaddingValues(12.s), verticalArrangement = Arrangement.spacedBy(2.s), modifier = Modifier.fillMaxSize()) {
        if (library.artists.isNotEmpty()) {
            heading(R.string.Tv_Search_Artists, top)
            items(library.artists, key = { "artist:" + it.name }) { artist ->
                ResultRow(
                    title = artist.name,
                    line = albumCount(artist.own.size),
                    onClick = { onOpenArtist(artist) },
                    onMenu = null,
                    modifier = firstOf(artist),
                ) { ArtistPicture(artist, collection, images, artwork) }
            }
        }
        if (library.albums.isNotEmpty()) {
            heading(R.string.Tv_Search_Albums, top)
            items(library.albums, key = { "album:" + it.key }) { album ->
                ResultRow(
                    title = albumTitle(album),
                    line = albumArtist(album),
                    onClick = { onOpenAlbum(album) },
                    onMenu = null,
                    modifier = firstOf(album),
                ) { AlbumPicture(album, collection, images) }
            }
        }
        if (library.tracks.isNotEmpty()) {
            heading(R.string.Tv_Search_Tracks, top)
            items(library.tracks, key = { "track:" + it.track.rpmId }) { hit ->
                TrackRow(hit, collection, images, actions, menu, firstOf(hit))
            }
        }
        if (found.isNotEmpty() || stations == StationHits.Searching || stations == StationHits.Failed) {
            heading(R.string.Tv_Search_Stations, top)
            when (stations) {
                StationHits.Searching -> item(key = "stations:waiting") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.s), contentAlignment = Alignment.Center) { RecordSpinner(Modifier.size(30.s)) }
                }
                StationHits.Failed -> item(key = "stations:failed") {
                    Text(stringResource(R.string.Tv_Search_StationsFailed), style = ui(CARD_LINE_TEXT), color = Ink.Dim, modifier = Modifier.padding(horizontal = 10.s, vertical = 8.s))
                }
                else -> items(found, key = { "station:" + it.uuid }) { station ->
                    ResultRow(
                        title = station.display,
                        line = station.detail(Locale.getDefault()),
                        onClick = { onTune(station) },
                        onMenu = { self ->
                            val kept = radio.favorites.holds(station.uuid)
                            menu.open(
                                title = station.display,
                                choices = listOf(
                                    MenuChoice(R.string.Radio_PlayStation) { onTune(station) },
                                    MenuChoice(if (kept) R.string.Tv_Radio_RemoveFavorite else R.string.Tv_Radio_AddFavorite) { radio.favorites.toggle(station) },
                                ),
                                returnTo = self,
                            )
                        },
                        modifier = firstOf(station),
                    ) { LogoWell(StationLogo.of(station, rules.logoStamp), images, ROW_PICTURE, Modifier) }
                }
            }
        }
    }
}

/** A section's heading: [HEADING_TEXT] SemiBold in [Ink.Card], as on the radio screen. It has more space above
 * it unless it is the heading of the [top] section. */
private fun LazyListScope.heading(text: Int, top: Int) {
    item(key = "heading:$text") {
        Text(
            stringResource(text),
            style = ui(HEADING_TEXT, FontWeight.SemiBold),
            color = Ink.Card,
            modifier = Modifier.padding(start = 10.s, top = if (text == top) 4.s else 22.s, bottom = 6.s),
        )
    }
}

/** A track found: its album's cover, its title, and its artist and album. OK plays it alone. A held OK opens its menu. */
@Composable
private fun TrackRow(hit: TrackHit, collection: Collection, images: ImageLoader, actions: TrackActions, menu: RowMenuState, modifier: Modifier) {
    val q = remember(hit, collection) {
        Queued(collection.shelf, hit.track, hit.album.album, hit.album.artist, collection.cover(hit.album), collection.disc(hit.album))
    }
    ResultRow(
        title = hit.track.title.orEmpty(),
        line = listOfNotNull(hit.track.artist?.takeIf { it.isNotBlank() }, hit.album.album.takeIf { it.isNotBlank() }).joinToString(" · ").ifEmpty { null },
        onClick = { actions.play(listOf(q), 0) },
        onMenu = { self ->
            menu.open(title = hit.track.title.orEmpty(), choices = trackChoices(q, collection, actions, play = { actions.play(listOf(q), 0) }), returnTo = self)
        },
        modifier = modifier,
    ) { AlbumPicture(hit.album, collection, images) }
}

/**
 * One row of the list, drawn as the rows of the album wall's search are: lit `#33FFFFFF` while the remote is on
 * it, a square picture of size [ROW_PICTURE], then [title] ([CARD_TITLE_TEXT] SemiBold) over [line]
 * ([CARD_LINE_TEXT], dim). With [onMenu], a held OK opens a menu, which gives the focus back to this row when
 * it closes.
 */
@Composable
private fun ResultRow(
    title: String,
    line: String?,
    onClick: () -> Unit,
    onMenu: ((FocusRequester) -> Unit)?,
    modifier: Modifier,
    picture: @Composable () -> Unit,
) {
    var lit by remember { mutableStateOf(false) }
    val self = remember { FocusRequester() }
    val shape = RoundedCornerShape(8.px)
    DeskButton(
        onClick = onClick,
        corner = 8.px,
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .focusRequester(self)
            .then(if (onMenu != null) Modifier.heldOkMenu { onMenu(self) } else Modifier)
            // Left and Right have nowhere to go in a list. Left then opens the side menu, as at the edge of any screen.
            .focusProperties {
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
            }
            .onFocusChanged { lit = it.isFocused }
            .fillMaxWidth()
            .background(if (lit) Color(0x33FFFFFF) else Color.Transparent, shape)
            .padding(horizontal = 10.s, vertical = 8.s),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(ROW_PICTURE)) { picture() }
            Spacer(Modifier.width(16.s))
            Column(verticalArrangement = Arrangement.spacedBy(2.s)) {
                Text(title, style = ui(CARD_TITLE_TEXT, FontWeight.SemiBold), color = Ink.Card, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (line != null) Text(line, style = ui(CARD_LINE_TEXT), color = Ink.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** An album's cover in a row, on [Ink.Well] with corner 6 until it arrives. */
@Composable
private fun AlbumPicture(album: CloudAlbum, collection: Collection, images: ImageLoader) {
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(6.px)).background(Ink.Well)) {
        collection.cover(album)?.let { CoverImage(it, images, ContentScale.Crop, Modifier.fillMaxSize(), onShown = {}) }
    }
}

/** An act's photograph in a circle ([artistPhoto]). Until the photograph arrives, their first letter is shown.
 * The compilations shelf shows the people glyph instead, as on the artists list ([FaceCard]). */
@Composable
private fun ArtistPicture(artist: ArtistWallItem, collection: Collection, images: ImageLoader, artwork: Artwork) {
    val photo = artistPhoto(artist, collection, artwork)
    val context = LocalContext.current
    val edge = with(LocalDensity.current) { ROW_PICTURE.roundToPx() }
    Box(Modifier.fillMaxSize().clip(CircleShape).background(Ink.Well), contentAlignment = Alignment.Center) {
        if (ArtistCredit.isVariousArtists(artist.name)) {
            Glyph(Fa.USERS, 20.ts, STAND_IN)
        } else {
            Text(artist.initial.toString(), style = ui(22.ts, FontWeight.SemiBold), color = STAND_IN)
        }
        if (photo != null) {
            CoverImage(remember(photo, edge) { ImageRequest.Builder(context).data(photo).size(edge).build() }, images, ContentScale.Crop, Modifier.fillMaxSize(), onShown = {})
        }
    }
}

/** The side of a row's picture. */
private val ROW_PICTURE = 64.s

/** The stand-in letter and glyph, as the artists list draws them. */
private val STAND_IN = Color(0x8CFFFFFF)

/** How far down the card the turning record and the no-results line are placed: above the TV's keyboard. */
private val NOTHING_YET_TOP = 48.s

/** How long after the last keystroke the collection is searched and the directory is asked. */
private const val LIBRARY_PAUSE_MS = 150L
private const val STATIONS_PAUSE_MS = 450L

/** How many stations are requested from the directory, and how many of them are listed. */
private const val STATIONS_ASKED = 60
private const val STATIONS_MOST = 20
