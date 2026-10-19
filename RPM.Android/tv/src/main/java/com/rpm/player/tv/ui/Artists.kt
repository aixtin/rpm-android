package com.rpm.player.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil3.ImageLoader
import coil3.request.ImageRequest
import com.rpm.player.core.R
import com.rpm.player.core.library.ArtistCredit
import com.rpm.player.core.library.ArtistOrdering
import com.rpm.player.core.library.ArtistSort
import com.rpm.player.core.library.ArtistWallItem
import com.rpm.player.core.library.Artwork
import com.rpm.player.core.library.Collection
import com.rpm.player.core.library.Walls

/**
 * The artists list (the desktop Pegasus's PegasusChrome.Artists): every act the collection files a record
 * under, [ARTIST_COLUMNS] to a row, each a round photograph ([FaceCard]) with the name and the number of
 * records under it. The acts and their order come from [com.rpm.player.core.library.ArtistWall], the TV's port
 * of the wall every head builds. The order key in the top right opens the four orders ([ArtistOrdering]), as
 * the album wall's opens its own. OK opens the artist's page. No back key: the remote's Back goes home, as on
 * the wall.
 *
 * Left out against the desktop: the search field. The album wall's search on the TV needs the keyboard's
 * own handling ([AlbumsScreen]); the artists list can take the same field once that is lifted out of the wall.
 *
 * In name order an alphabet stands down the right edge ([AlphabetLadder]). Right from the last
 * column enters it and it grows; Up and Down step through the letters and scroll the list so the row of the letter's
 * first act is at the top; Left, OK and Back go to that act.
 */
@Composable
fun ArtistsScreen(
    walls: Walls,
    collection: Collection,
    images: ImageLoader,
    artwork: Artwork,
    state: BrowseState,
    mini: MiniPlayerState,
    onOpenArtist: (ArtistWallItem) -> Unit,
) {
    val byKey = remember(collection) { collection.albums.associateBy { it.key } }
    val order = state.artistOrder
    val shown = remember(walls, order) { ArtistSort.by(walls.artists, order, byKey) }
    val grid = rememberLazyGridState()
    val underKeys = rememberUnderKeys(grid, ARTIST_COLUMNS)
    val atTop = remember { derivedStateOf { grid.firstVisibleItemIndex == 0 && grid.firstVisibleItemScrollOffset == 0 } }
    // The screen in front hands the mini player its scroll answer, which it reads live; one kept under it does not
    // (LocalOnTop).
    val onTop = LocalOnTop.current
    SideEffect { if (onTop) mini.follow(atTop) }
    // A new requester for each order. One kept across orders would stay attached to the card that was first in the
    // last order until the grid has laid the new order out, so the request after a new order would land on that
    // card in its new place (for example Various Artists, mid-list). A fresh one is attached only to the card that
    // is first now, once it is composed, and a request before then is refused and retried.
    val first = remember(order) { FocusRequester() }
    val returnTo = remember { FocusRequester() }
    // Whether the card the list asks the focus for holds it now, told by the card's own focus: the top card after
    // a new order, or the card opened last. A request is not proof (see the effect at the end).
    var topHolds by remember(order) { mutableStateOf(false) }
    var openedHolds by remember(order) { mutableStateOf(false) }
    val orderKey = remember { FocusRequester() }
    val menu = rememberRowMenu()
    val opened = state.openedArtist

    // The alphabet, in name order only: in the other orders a letter's acts are not together. Each letter and the
    // place in the list of its first act, in the order the list meets them.
    val ladder = remember(shown, order) { if (order == ArtistOrdering.Name) LadderLetters.of(shown) else LadderLetters.NONE }
    val ladderKey = remember { FocusRequester() }
    // The first act of the letter in hand, which Left, OK and Back from the ladder go to.
    val letterHead = remember(order) { FocusRequester() }
    var letter by remember(ladder) { mutableIntStateOf(0) }
    var onLadder by remember { mutableStateOf(false) }
    // The card that had the focus last, so the ladder opens on its letter.
    var focusedIndex by remember(order) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    fun toLetter(to: Int) {
        if (ladder.letters.isEmpty()) return
        letter = to.coerceIn(0, ladder.letters.lastIndex)
        scope.launch { grid.scrollToItem(ladder.starts[letter]) }
    }
    BackHandler(enabled = onLadder && LocalOnTop.current) { runCatching { letterHead.requestFocus() } }

    Box(Modifier.fillMaxSize()) {
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Message(stringResource(R.string.Artists_Empty)) }
        } else {
            LazyVerticalGrid(
                state = grid,
                columns = GridCells.Fixed(ARTIST_COLUMNS),
                contentPadding = PaddingValues(start = 34.px, end = 50.px + if (ladder.letters.isEmpty()) 0.px else LADDER_NARROW, top = WALL_TOP, bottom = 28.px),
                horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
                verticalArrangement = Arrangement.spacedBy(CARD_GAP),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(shown, key = { _, artist -> artist.name }) { index, artist ->
                    FaceCard(
                        artist = artist,
                        collection = collection,
                        images = images,
                        artwork = artwork,
                        onClick = {
                            state.openedArtist = artist.name
                            onOpenArtist(artist)
                        },
                        modifier = when {
                            artist.name == opened -> Modifier.focusRequester(returnTo).onFocusChanged { openedHolds = it.isFocused }
                            artist === shown.first() -> Modifier.focusRequester(first).onFocusChanged { topHolds = it.isFocused }
                            else -> Modifier
                        }
                            .then(if (ladder.letters.isNotEmpty() && index == ladder.starts.getOrNull(letter)) Modifier.focusRequester(letterHead) else Modifier)
                            .onFocusChanged { if (it.isFocused) focusedIndex = index }
                            // Up from any card of the first row reaches the order key in the top right corner,
                            // as on the album wall; the search by layout finds it only from the column under it.
                            // Right from the last column, and from the last card, enters the alphabet.
                            .focusProperties {
                                if (index < ARTIST_COLUMNS) up = orderKey
                                if (ladder.letters.isNotEmpty() && (index % ARTIST_COLUMNS == ARTIST_COLUMNS - 1 || index == shown.lastIndex)) right = ladderKey
                            }
                            .then(underKeys.mark(index)),
                    )
                }
            }
        }

        if (ladder.letters.isNotEmpty()) {
            AlphabetLadder(
                letters = ladder.letters,
                current = letter,
                active = onLadder,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = WALL_TOP, bottom = 28.px, end = 12.px)
                    .focusRequester(ladderKey)
                    .onFocusChanged {
                        // Entering, the ladder opens on the letter of the card the remote came from.
                        if (it.isFocused && !onLadder) letter = ladder.letterAt(focusedIndex)
                        onLadder = it.isFocused
                    }
                    .focusProperties {
                        left = letterHead
                        right = FocusRequester.Cancel
                        up = FocusRequester.Cancel
                        down = FocusRequester.Cancel
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionUp -> { toLetter(letter - 1); true }
                            Key.DirectionDown -> { toLetter(letter + 1); true }
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { runCatching { letterHead.requestFocus() }; true }
                            else -> false
                        }
                    }
                    .focusable(),
            )
        }

        val sortName = stringResource(R.string.Viz_ArtistsSort)
        // The orders' names, read here because the menu is built in the key's click.
        val orderNames = ArtistOrdering.entries.associateWith { stringResource(orderLabel(it)) }
        Box(Modifier.align(Alignment.TopEnd).padding(top = 14.px, end = 50.px)) {
            ToolBox {
                ToolKey(
                    onClick = {
                        menu.open(
                            title = "",
                            // The order in force wears the tick, as on the desktop (ArtistsSort_Click).
                            choices = ArtistOrdering.entries.map { o ->
                                MenuTick(orderNames.getValue(o), ticked = o == state.artistOrder) {
                                    // A new order forgets the artist last opened, so the list goes back to its
                                    // top rather than to that artist's new place.
                                    if (state.artistOrder != o) state.openedArtist = null
                                    state.artistOrder = o
                                }
                            },
                            returnTo = orderKey,
                        )
                    },
                    modifier = Modifier.focusRequester(orderKey).focusProperties { down = if (underKeys.index >= 0) underKeys.requester else FocusRequester.Default }.semantics { contentDescription = sortName },
                ) { Glyph(Fa.SORT, TOOL_GLYPH, Ink.Key) }
            }
        }
    }

    RowMenu(menu)

    // Back from an artist's page: that artist's card, scrolled to. Otherwise the first card. Asked again when
    // the order changes; the order menu forgets the opened artist first, so that is the top.
    LaunchedEffect(order) {
        val at = shown.indexOfFirst { it.name == opened }
        val target = if (at >= 0) returnTo else first
        grid.scrollToItem(if (at >= 0) at else 0)
        // Asked on each of the next frames until the card takes it: after a new order the card now at the top is
        // not composed until the scroll has been laid out, and a request before then is refused ([first] is new
        // for the order, so it cannot be held by the card that was first before).
        //
        // Asked until the card itself says it holds the focus, not until a request returns true. The screens give
        // the focus back to the item they last had when it enters them from outside (RpmApp's focusRestorer, so
        // that closing the side menu returns to where the remote was). The sort menu's closing leaves the focus
        // outside, so the first request here enters the screens and is handed to the card focused before the
        // order key, wherever it now stands (for example Various Artists, mid-list). A request made once the focus
        // is inside the screens moves it within them and lands.
        repeat(FOCUS_TRIES) {
            withFrameNanos { }
            if (if (at >= 0) openedHolds else topHolds) return@LaunchedEffect
            runCatching { target.requestFocus() }
        }
    }
}

/** The letters of the list in name order and where each begins. [starts] holds, for each of [letters], the place in
 * the list of the first act filed under it. */
private class LadderLetters(val letters: List<Char>, val starts: List<Int>) {
    /** The letter the act at [index] files under, as a place in [letters]: the last letter that begins at or before it. */
    fun letterAt(index: Int): Int = starts.indexOfLast { it <= index }.coerceAtLeast(0)

    companion object {
        val NONE = LadderLetters(emptyList(), emptyList())

        /** Each letter once, where the list first meets it ([ArtistWallItem.initial]); '#' comes last in name order. */
        fun of(shown: List<ArtistWallItem>): LadderLetters {
            val letters = ArrayList<Char>()
            val starts = ArrayList<Int>()
            val seen = HashSet<Char>()
            shown.forEachIndexed { i, artist ->
                if (seen.add(artist.initial)) {
                    letters += artist.initial
                    starts += i
                }
            }
            return LadderLetters(letters, starts)
        }
    }
}

/**
 * The alphabet down the right edge: a dark pill holding
 * [letters], [LADDER_NARROW] wide with letters at [LADDER_TEXT]; while [active] it grows to [LADDER_WIDE], its letters
 * to [LADDER_TEXT_LIT], and the letter in hand ([current]) is lit on a white disc. It scrolls itself so the letter in
 * hand stays in view, which it needs once grown. The keys are the caller's; this only draws.
 */
@Composable
private fun AlphabetLadder(letters: List<Char>, current: Int, active: Boolean, modifier: Modifier) {
    val wide by animateDpAsState(if (active) LADDER_WIDE else LADDER_NARROW, tween(GROW_MS), label = "ladder")
    val grow by animateFloatAsState(if (active) 1f else 0f, tween(GROW_MS), label = "ladder letters")
    val list = rememberLazyListState()
    val density = LocalDensity.current
    LaunchedEffect(current, active) {
        // The letter in hand, about the middle of the ladder: scrolled to with a third of the ladder above it.
        val above = with(density) { (list.layoutInfo.viewportSize.height / 3f).toInt() }
        list.animateScrollToItem(current.coerceIn(0, (letters.size - 1).coerceAtLeast(0)), -above)
    }
    val shape = RoundedCornerShape(wide / 2)
    Box(modifier.width(wide).background(if (active) LADDER_FILL_LIT else LADDER_FILL, shape)) {
        LazyColumn(
            state = list,
            userScrollEnabled = false,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = 8.s),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(letters.size) { i ->
                val c = letters[i]
                val lit = active && i == current
                val size = LADDER_TEXT.value + (LADDER_TEXT_LIT.value - LADDER_TEXT.value) * grow
                Box(
                    Modifier
                        .padding(vertical = (2f + 4f * grow).dp)
                        .size((size * 1.7f).dp)
                        .background(if (lit) Color.White else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        c.toString(),
                        style = ui(size.sp, if (lit || active) FontWeight.SemiBold else FontWeight.Normal),
                        color = when {
                            lit -> LADDER_LIT_INK
                            active -> Ink.Words
                            else -> Ink.Dim
                        },
                    )
                }
            }
        }
    }
}

/** The ladder at rest and in hand: its width and its letters' size. */
private val LADDER_NARROW = 30.s
private val LADDER_WIDE = 56.s
private val LADDER_TEXT = 14.ts
private val LADDER_TEXT_LIT = 22.ts

/** The ladder's pill: a dark smoke at rest, darker in hand so its letters read over the cards it grows across. */
private val LADDER_FILL = Color(0x33000000)
private val LADDER_FILL_LIT = Color(0x99000000)

/** The letter in hand, dark on its white disc. */
private val LADDER_LIT_INK = Color(0xFF111111)

/** How many artists stand in a row: the album wall's [WALL_COLUMNS]. */
const val ARTIST_COLUMNS = WALL_COLUMNS

/** How many frames the list asks for its card's focus before it leaves it. */
private const val FOCUS_TRIES = 20

private fun orderLabel(order: ArtistOrdering): Int = when (order) {
    ArtistOrdering.Name -> R.string.ArtistSort_Name
    ArtistOrdering.Albums -> R.string.ArtistSort_Albums
    ArtistOrdering.Tracks -> R.string.ArtistSort_Tracks
    ArtistOrdering.Added -> R.string.ArtistSort_Added
}

/**
 * One act as a card: their photograph in a circle on [Ink.Well], their first letter there until it arrives
 * (the people glyph for the compilations shelf, which has no photograph), then the name ([CARD_TITLE_TEXT]
 * SemiBold) centred under it, with no count. The photograph is [artistPhoto].
 */
@Composable
fun FaceCard(
    artist: ArtistWallItem,
    collection: Collection,
    images: ImageLoader,
    artwork: Artwork,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val photo = artistPhoto(artist, collection, artwork)
    val context = LocalContext.current
    val density = LocalDensity.current
    CardButton(onClick = onClick, modifier = modifier.semantics { contentDescription = artist.name }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape).background(Ink.Well), contentAlignment = Alignment.Center) {
                val stand = with(density) { (maxWidth * 0.36f).toSp() }
                if (ArtistCredit.isVariousArtists(artist.name)) {
                    Glyph(Fa.USERS, stand, STAND_IN)
                } else {
                    Text(artist.initial.toString(), style = ui(stand, FontWeight.SemiBold), color = STAND_IN)
                }
                if (photo != null) {
                    val edge = with(density) { maxWidth.roundToPx() }
                    CoverImage(remember(photo, edge) { ImageRequest.Builder(context).data(photo).size(edge).build() }, images, ContentScale.Crop, Modifier.fillMaxSize(), onShown = {})
                }
            }
            Text(artist.name, style = ui(CARD_TITLE_TEXT, FontWeight.SemiBold), color = Ink.Card, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 7.px).heightIn(min = 22.s))
        }
    }
}

/** The stand-in letter and glyph: `#8CFFFFFF`, as the desktop's. */
private val STAND_IN = Color(0x8CFFFFFF)

/**
 * An act's photograph: the first square picture the rpm API has of them, else their first wide one, asked
 * through [Artwork] (one ask per artist for the life of the app, a failed ask asked again next time). Null
 * until it answers, for the compilations shelf, and where there is none. The album named with the ask is
 * one of their own records, which tells two acts of one name apart.
 *
 * An answer [Artwork] already holds is the first value ([Artwork.peek]), so a card composed again shows the
 * photograph from its first frame rather than the first letter for a frame.
 */
@Composable
fun artistPhoto(artist: ArtistWallItem, collection: Collection, artwork: Artwork): String? {
    val known = remember(artist.name) { artwork.peek(artist.name)?.let { it.thumbs.firstOrNull() ?: it.backgrounds.firstOrNull() } }
    val photo by produceState(known, artist.name) {
        if (ArtistCredit.isVariousArtists(artist.name)) return@produceState
        val album = artist.own.firstNotNullOfOrNull { key -> collection.album(key)?.album?.takeIf { it.isNotBlank() } }.orEmpty()
        val art = artwork.of(artist.name, album)
        value = art?.thumbs?.firstOrNull() ?: art?.backgrounds?.firstOrNull()
    }
    return photo
}
