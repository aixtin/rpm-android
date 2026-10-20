package com.rpm.player.tv.ui

import kotlin.math.roundToInt
import androidx.compose.ui.layout.layout
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutQuad
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.transformations
import com.rpm.player.core.R
import com.rpm.player.core.cloud.CloudTrack
import com.rpm.player.core.library.ArtistArtwork
import com.rpm.player.core.library.ArtistCredit
import com.rpm.player.core.library.ArtistPopular
import com.rpm.player.core.library.ArtistShelves
import com.rpm.player.core.library.ArtistWallItem
import com.rpm.player.core.library.Artwork
import com.rpm.player.core.library.CloudAlbum
import com.rpm.player.core.library.HomeShelf
import com.rpm.player.core.library.Collection
import com.rpm.player.core.library.ArtistEdit
import com.rpm.player.core.library.ArtistEdits
import com.rpm.player.core.library.RelatedArtists
import com.rpm.player.core.library.TrackList
import com.rpm.player.core.library.Walls
import com.rpm.player.core.papers.ArtistSubject
import com.rpm.player.core.papers.TrackPlace
import com.rpm.player.core.papers.Dossiers
import com.rpm.player.core.papers.PlaysRecord
import com.rpm.player.core.papers.WorldPlays
import com.rpm.player.core.playback.Queued
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * One artist's page: their photograph across the top right, dissolving into the
 * page toward its left and its foot, as the desktop's artist band does; left of it their logo (or their name) and
 * the first lines of their encyclopedia article; then their popular songs, three columns of four; then their
 * albums, singles, live albums and compilations (and the records they made with others, their soundtracks and
 * what they appear on), the playlists and the mood playlists holding their songs, the acts related to them in this
 * collection ([RelatedArtists]). There is no list of every song: the search finds any of them.
 *
 * The band stays where it is; everything under it scrolls, and fades out under the band's foot.
 *
 * The page is drawn on the photograph's own colour, the photograph heavily blurred over it and a dark weight over
 * both, as the desktop's artist page is (PageGround, PageWash at 0.55, PageWeight `#6B000000`), so the writing is
 * white on every artist. Until the photograph is in, the page is the weight over the screens' background.
 *
 * OK on a record opens its page, and Back comes back here with the focus on that record ([BrowseState.openedAlbum]).
 * OK on a popular song plays the popular songs from it; a held OK or the Menu key opens the track's menu, the album
 * page's.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistPage(
    artist: ArtistWallItem,
    walls: Walls,
    collection: Collection,
    images: ImageLoader,
    artwork: Artwork,
    dossiers: Dossiers,
    edits: ArtistEdits,
    worldPlays: WorldPlays,
    state: BrowseState,
    actions: TrackActions,
    onOpenAlbum: (CloudAlbum) -> Unit,
    onOpenArtist: (ArtistWallItem) -> Unit,
    onOpenList: (TrackList) -> Unit,
    onPicture: (Any?) -> Unit,
    onBack: () -> Unit,
) {
    val byKey = remember(collection) { collection.albums.associateBy { it.key } }
    val shelves = remember(artist, collection) { ArtistShelves.of(artist, byKey) }
    val art = rememberArtwork(artist, collection, artwork)
    // The wide photograph for the band; the square one where there is no wide one.
    val photo = art?.backgrounds?.firstOrNull() ?: art?.thumbs?.firstOrNull()
    SideEffect { onPicture(photo ?: shelves.records.firstOrNull()?.let(collection::cover)) }
    val reading = rememberCoverReading(photo, images)

    // Identified through their own records, never by the name alone (ArtistSubject.of).
    val subject = remember(artist.name, shelves) { ArtistSubject.of(artist.name, shelves) }
    var article by remember(subject) { mutableStateOf(dossiers.cached(subject)?.article) }
    LaunchedEffect(subject) { dossiers.read(subject) {}?.let { article = it.article } }
    // The admin's edits. Until they have answered no text is shown, so an admin's description
    // never replaces Wikipedia's on screen; a failed ask answers "no edits".
    var edit by remember(artist.name) { mutableStateOf(edits.peek(artist.name)) }
    LaunchedEffect(artist.name) { edit = edits.of(artist.name) }
    val bio = edit?.let { it.description ?: article?.extract }

    val popular = rememberPopular(shelves, ArtistPopular.records(shelves.songs), worldPlays) { plays -> ArtistPopular.ranked(POPULAR_MOST, shelves.songs, plays) }
    // Whether the page opened before the popular songs were known: only then are they brought in when they come.
    val popularLate = remember(artist) { popular.isEmpty() }
    val computed = remember(artist, walls, collection) { RelatedArtists.of(artist, walls, byKey, collection.lists) }
    val related = remember(computed, edit) { (edit ?: ArtistEdit.NONE).relate(computed, artist, walls) }
    val theirs = remember(shelves) { shelves.songs.mapTo(HashSet()) { it.first.rpmId } }
    val playlists = remember(collection, theirs) { collection.lists.filter { !it.generated && it.tracks.any { t -> t.rpmId in theirs } } }
    val moods = remember(collection, theirs) { collection.lists.filter { it.generated && it.tracks.any { t -> t.rpmId in theirs } } }

    // The rows of records, in the design's order, then the kinds the design does not name.
    val records = remember(shelves) {
        listOf(
            R.string.Artists_Albums to shelves.records,
            R.string.Artists_Eps to shelves.eps,
            R.string.Artists_Singles to shelves.singles,
            R.string.Artists_LiveAlbums to shelves.live,
            R.string.Artists_Mixtapes to shelves.mixtapes,
            R.string.Artists_Compilations to shelves.compilations,
            R.string.Artists_Collaborations to shelves.together,
            R.string.Artists_Soundtracks to shelves.soundtracks,
            R.string.Artists_AppearsOn to shelves.appearances,
        ).filter { it.second.isNotEmpty() }
    }

    val first = remember { FocusRequester() }
    val backKey = remember { FocusRequester() }
    val returnTo = remember { FocusRequester() }
    val menu = rememberRowMenu()
    // Every row is kept composed once it has been, as on home: composing a row as it scrolls into view holds the
    // main thread for too long on a TV box.
    val list = rememberLazyListState(cacheWindow = LazyLayoutCacheWindow(aheadFraction = PAGE_KEPT, behindFraction = PAGE_KEPT))
    val opened = state.openedAlbum
    // The first thing the remote lands on: the first popular song, else the first record.
    val focusPopular = popular.isNotEmpty()
    // Whether the first focus is decided: Back to a record, a key pressed, or the popular songs focused.
    var settled by remember(artist) { mutableStateOf(false) }

    // A key pressed on the page before the popular songs arrive keeps the focus where the remote put it.
    BoxWithConstraints(Modifier.fillMaxSize().onPreviewKeyEvent { settled = true; false }) {
        // The band stays at the top; the rest scrolls under its foot,
        // fading out there over the page's blurred wash, so nothing is drawn over the name and the article.
        val bandHigh = maxWidth * BAND_HEIGHT * BAND_FOOT
        val under = bandHigh * LIST_UNDER
        PageWash(photo, reading?.dominant?.let { Color(it.r, it.g, it.b) }, images)
        CompositionLocalProvider(LocalBringIntoViewSpec provides CentreRows) {
            LazyColumn(
                state = list,
                contentPadding = PaddingValues(start = 34.px, end = 36.px, top = under, bottom = 44.px),
                modifier = Modifier.padding(top = bandHigh - under).fillMaxSize().fadesAtTop(under),
            ) {
                if (popular.isNotEmpty()) {
                    item(key = "popular") {
                        ArrivesLate(popularLate) { PopularSongs(popular, collection, images, first, actions, menu) }
                    }
                }
                records.forEachIndexed { i, (heading, albums) ->
                    recordShelf(heading, albums, collection, images, opened, returnTo, if (i == 0 && !focusPopular) first else null) { album ->
                        state.openedAlbum = album.key
                        onOpenAlbum(album)
                    }
                }
                listShelf(R.string.Artists_InPlaylists, playlists, collection, images, onOpenList)
                listShelf(R.string.Genres_MoodPlaylists, moods, collection, images, onOpenList)
                if (related.isNotEmpty()) {
                    item(key = "related") {
                        Column(Modifier.padding(top = 24.s)) {
                            Heading(stringResource(R.string.Artists_Related))
                            CardRow(related, key = { it.name }, ahead = null) { other ->
                                Box(Modifier.padding(horizontal = CARD_GAP / 2, vertical = 8.px).width(TILE.s)) {
                                    FaceCard(other, collection, images, artwork, onClick = { onOpenArtist(other) }, modifier = Modifier)
                                }
                            }
                        }
                    }
                }
            }
        }
        PageBand(photo, bio, albumCount(artist.own.size) + "  ·  " + songCount(artist.tracks), images, bandHigh) { ArtistTitle(artist, art?.logo, images) }
        BackKey(onBack, Modifier.focusRequester(backKey).focusProperties { down = first })
    }

    RowMenu(menu)

    // Back from a record's page: that record's card, its row scrolled to. Otherwise the first popular song, or the
    // first record while there are none, with the page kept at its top so the band is what is seen first. The
    // popular songs can arrive a moment after the page opens: they then take the focus from the first record, unless
    // the remote has moved it since ([settled]).
    LaunchedEffect(artist, focusPopular) {
        if (settled) return@LaunchedEffect
        val row = records.indexOfFirst { (_, albums) -> albums.any { it.key == opened } }
        if (row >= 0) {
            settled = true
            // After the popular songs, where they are drawn.
            list.scrollToItem(row + if (focusPopular) 1 else 0)
            withFrameNanos { }
            if (runCatching { returnTo.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
        repeat(PAGE_FOCUS_TRIES) {
            if (runCatching { first.requestFocus() }.getOrDefault(false)) {
                withFrameNanos { }
                list.scrollToItem(0)
                if (focusPopular) settled = true
                return@LaunchedEffect
            }
            withFrameNanos { }
        }
    }

}

/** How many frames the page asks for its first focus before giving up. */
internal const val PAGE_FOCUS_TRIES = 30

/** The artist's photograph and colours as the app knows them: what is already held at once, then the answer. */
@Composable
private fun rememberArtwork(artist: ArtistWallItem, collection: Collection, artwork: Artwork): ArtistArtwork? {
    val known = remember(artist.name) { artwork.peek(artist.name) }
    val art by produceState(known, artist.name) {
        if (ArtistCredit.isVariousArtists(artist.name)) return@produceState
        val album = artist.own.firstNotNullOfOrNull { key -> collection.album(key)?.album?.takeIf { it.isNotBlank() } }.orEmpty()
        artwork.of(artist.name, album)?.let { value = it }
    }
    return art
}

/**
 * The page's ground, as the desktop's artist page lays it: [colour] (the photograph's main colour), the
 * photograph heavily blurred over it at 0.55 ([Soft]), and the weight `#6B000000` over both. Before the photograph
 * and its colour are known, only the weight, over whatever the screens are drawn on.
 */
@Composable
internal fun PageWash(photo: Any?, colour: Color?, images: ImageLoader) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        if (colour != null) Box(Modifier.fillMaxSize().background(colour))
        if (photo != null && colour != null) {
            AsyncImage(
                model = remember(photo) { ImageRequest.Builder(context).data(photo).size(WASH_EDGE).transformations(Soft()).allowHardware(false).crossfade(PICTURE_FADE_MS).build() },
                imageLoader = images,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = WASH_ALPHA,
                modifier = Modifier.fillMaxSize().dithered(),
            )
        }
        Box(Modifier.fillMaxSize().background(PAGE_WEIGHT))
    }
}

/** The act's logo, cut to its lettering ([Trim]), or their name until it is in or where there is none. */
@Composable
private fun ArtistTitle(artist: ArtistWallItem, logo: String?, images: ImageLoader) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        var logoIn by remember(logo) { mutableStateOf(false) }
        if (logo != null) {
            AsyncImage(
                model = remember(logo) { ImageRequest.Builder(context).data(logo).size(coil3.size.Size.ORIGINAL).transformations(Trim()).allowHardware(false).build() },
                imageLoader = images,
                contentDescription = artist.name,
                contentScale = ContentScale.Fit,
                onSuccess = { logoIn = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!logoIn) {
            Text(artist.name, style = ui(44.ts, FontWeight.Bold), color = Ink.Words, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

/**
 * The top of an artist's or a genre's page, from the screen's top and across its width: the photograph in the right
 * [PHOTO_SHARE] of it, [BAND_HEIGHT] of the width high, its own pixels taken away over the left [DISSOLVE_LEFT] of it
 * and the bottom [DISSOLVE_FOOT], so the page shows through rather than a line or a bar of colour; and in the left
 * column the [title] (the act's logo or name, the genre's wordmark), under it the first whole sentences of the
 * article ([bio]), and under that [counts] (how many albums and songs), at the places the design gives them.
 *
 * An article that arrives after the page has opened fades in over [BIO_IN_MS] while it slides up [BIO_SLIDE] into
 * place, and the counts glide down to make room, rather than the text appearing at once. An article that the
 * page opened with (kept from an earlier visit) is drawn whole at once, as a cover from memory is.
 */
@Composable
internal fun PageBand(photo: Any?, bio: String?, counts: String, images: ImageLoader, high: androidx.compose.ui.unit.Dp, title: @Composable () -> Unit) {
    val context = LocalContext.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth
        Box(Modifier.fillMaxWidth().height(high)) {
            if (photo != null) {
                AsyncImage(
                    model = remember(photo) { ImageRequest.Builder(context).data(photo).crossfade(PICTURE_FADE_MS).build() },
                    imageLoader = images,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .width(wide * PHOTO_SHARE)
                        .fillMaxHeight()
                        .dissolves(),
                )
            }
            Column(Modifier.padding(start = 34.px).width(wide * TEXT_SHARE).fillMaxHeight()) {
                val drawn = high / BAND_FOOT
                Spacer(Modifier.height(drawn * NAME_TOP))
                Box(Modifier.fillMaxWidth().height(drawn * NAME_HEIGHT), contentAlignment = Alignment.Center) { title() }
                Spacer(Modifier.height(drawn * BIO_GAP))
                val paragraph = remember(bio) { bio?.split("\n\n")?.map { it.trim() }?.firstOrNull { it.isNotEmpty() } }
                val style = ui(PAGE_TEXT)
                val measurer = rememberTextMeasurer()
                // Whether the page opened without the article: only then is it brought in when it comes.
                val late = remember { paragraph == null }
                val arrival = remember { Animatable(if (paragraph == null) 0f else 1f) }
                LaunchedEffect(paragraph != null) {
                    if (paragraph != null && late) arrival.animateTo(1f, tween(BIO_IN_MS, easing = EaseOutQuad))
                }
                val slide = with(LocalDensity.current) { BIO_SLIDE.toPx() }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val width = constraints.maxWidth
                    // As many whole sentences as fit in [BIO_LINES] lines at this width, centred; never a cut word.
                    val shown = remember(paragraph, width, style) {
                        paragraph?.let { wholeSentences(it, BIO_LINES) { text -> measurer.measure(text, style, constraints = Constraints(maxWidth = width)).lineCount } }
                    }
                    // The counts' line moves down by the article's height over the same time, rather than jumping.
                    Column(
                        Modifier.fillMaxWidth().then(if (late) Modifier.animateContentSize(tween(BIO_IN_MS, easing = EaseOutQuad)) else Modifier),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (shown != null) {
                            Text(
                                shown,
                                style = style,
                                color = Color(0xD9FFFFFF),
                                textAlign = TextAlign.Center,
                                maxLines = BIO_LINES,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.s)
                                    .graphicsLayer {
                                        alpha = arrival.value
                                        translationY = (1f - arrival.value) * slide
                                    },
                            )
                        }
                        Text(counts, style = style, color = COUNTS_INK, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/**
 * The longest run of [paragraph]'s first sentences that [lines] says fits in [most] lines; the first sentence alone
 * when even that does not (it is then cut where the lines end). A sentence ends at a full stop, an exclamation or a
 * question mark before a space and a capital, a digit or an opening quote, so "Jr. and" stays one sentence.
 */
private fun wholeSentences(paragraph: String, most: Int, lines: (String) -> Int): String {
    val sentences = paragraph.split(SENTENCE_END).map { it.trim() }.filter { it.isNotEmpty() }
    var taken = sentences.firstOrNull() ?: return paragraph
    for (next in sentences.drop(1)) {
        val longer = "$taken $next"
        if (lines(longer) > most) break
        taken = longer
    }
    return taken
}

private val SENTENCE_END = Regex("(?<=[.!?])\\s+(?=[A-Z0-9\"“‘(])")

/** The list's top [depth] fading in from nothing, so a row scrolled up goes out under the band's foot rather than
 * being cut at a line. */
internal fun Modifier.fadesAtTop(depth: androidx.compose.ui.unit.Dp): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = depth.toPx()), blendMode = BlendMode.DstIn)
        }

/** The photograph's own pixels taken away toward its left and its foot (the desktop's band, which dissolves
 * rather than darkening into a line), smoothed so the dissolve has no corner where it starts. */
private fun Modifier.dissolves(): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(Brush.horizontalGradient(*ramp(DISSOLVE_LEFT, rising = true)), blendMode = BlendMode.DstIn)
            drawRect(Brush.verticalGradient(*ramp(DISSOLVE_FOOT, rising = false)), blendMode = BlendMode.DstIn)
        }

/** A mask over [share] of one side: from nothing to whole ([rising]) or whole to nothing, on t²(3−2t). */
private fun ramp(share: Float, rising: Boolean): Array<Pair<Float, Color>> = buildList {
    for (i in 0..RAMP_STEPS) {
        val t = i / RAMP_STEPS.toFloat()
        val eased = t * t * (3 - 2 * t)
        if (rising) add(share * t to Color.Black.copy(alpha = eased)) else add((1 - share + share * t) to Color.Black.copy(alpha = 1 - eased))
    }
    if (rising) add(1f to Color.Black) else add(0, 0f to Color.Black)
}.toTypedArray()

/**
 * The popular songs (Artists_PopularTracks): three columns of four across the page, as Quick selection is on
 * home, scrolling sideways for more, with Play all tracks beside the heading. The first song carries [first], the page's first focus. OK plays the
 * popular songs from that one; a held OK or the Menu key opens that song's menu in [menu], the album page's track menu
 * ([trackChoices]), whose Play also plays the popular songs from it.
 */
@Composable
internal fun PopularSongs(songs: List<Pair<CloudTrack, CloudAlbum>>, collection: Collection, images: ImageLoader, first: FocusRequester, actions: TrackActions, menu: RowMenuState) {
    val queue = remember(songs, collection) {
        songs.map { (track, album) -> Queued(collection.shelf, track, album.album, album.artist, collection.cover(album), collection.disc(album)) }
    }
    fun onPlay(at: Int) = actions.play(queue, at)
    Column(Modifier.padding(top = 18.s)) {
        // The heading with Play all tracks at its right, as Quick selection's on home: the popular songs in order.
        Row(Modifier.fillMaxWidth().padding(bottom = 12.s), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.Artists_PopularTracks), style = ui(HEADING_TEXT, FontWeight.SemiBold), color = Ink.Card, modifier = Modifier.weight(1f))
            val shape = RoundedCornerShape(20.px)
            DeskButton(
                onClick = { onPlay(0) },
                corner = 20.px,
                modifier = Modifier.background(Color(0x14FFFFFF), shape).border(1.px, Color(0x26FFFFFF), shape).padding(horizontal = 20.px, vertical = 8.px),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.px)) {
                    Glyph(Fa.PLAY, 14.ts, Ink.Card)
                    Text(stringResource(R.string.Home_PlayAllTracks), style = ui(16.ts, FontWeight.SemiBold), color = Ink.Card)
                }
            }
        }
        val strip = remember { LazyListState() }
        BoxWithConstraints(Modifier.fillMaxWidth().outdent(8.px)) {
            val panel = (maxWidth - 20.px) / 3
            val columns = (songs.size + POPULAR_DOWN - 1) / POPULAR_DOWN
            QuickStrip(columns, strip) { column ->
                var holds by remember { mutableStateOf(false) }
                Column(Modifier.zIndex(if (holds) 1f else 0f).onFocusChanged { holds = it.hasFocus }.width(panel)) {
                    for (row in 0 until POPULAR_DOWN) {
                        val i = column * POPULAR_DOWN + row
                        val (track, album) = songs.getOrNull(i) ?: continue
                        SongEntry(
                            track, album, collection, images, if (i == 0) Modifier.focusRequester(first) else Modifier,
                            onMenu = { self ->
                                menu.open(title = track.title.orEmpty(), choices = trackChoices(queue[i], collection, actions, play = { onPlay(i) }), returnTo = self)
                            },
                        ) { onPlay(i) }
                    }
                }
            }
        }
    }
}

/** One popular song: its record's cover (64·s, corner 6), the title and the record, as Quick selection's entries. A held
 * OK or the Menu key asks for its menu ([onMenu], with the entry's own focus to give back). */
@Composable
private fun SongEntry(track: CloudTrack, album: CloudAlbum, collection: Collection, images: ImageLoader, modifier: Modifier, onMenu: (FocusRequester) -> Unit, onClick: () -> Unit) {
    val self = remember { FocusRequester() }
    CardButton(
        onClick = onClick,
        contentAlignment = Alignment.CenterStart,
        modifier = modifier.focusRequester(self).heldOkMenu { onMenu(self) }.fillMaxWidth().padding(horizontal = 8.px, vertical = 6.px),
    ) { focused ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.s).clip(RoundedCornerShape(6.px)).background(Color(0x33000000))) {
                collection.cover(album)?.let { CoverImage(it, images, ContentScale.Crop, Modifier.matchParentSize(), onShown = {}) }
            }
            Spacer(Modifier.width(14.px))
            Column(verticalArrangement = Arrangement.spacedBy(3.px)) {
                Text(track.title.orEmpty(), style = ui(CARD_TITLE_TEXT, FontWeight.SemiBold), color = Ink.Card, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(albumTitle(album), style = ui(CARD_LINE_TEXT), color = Ink.Dim, maxLines = 1, overflow = if (focused) TextOverflow.Clip else TextOverflow.Ellipsis, modifier = Modifier.readAcross(focused))
            }
        }
    }
}

/** How many popular songs stand in a column, and the most there are: eight columns, as Quick selection on home. */
private const val POPULAR_DOWN = HomeShelf.QUICK_DOWN
internal const val POPULAR_MOST = HomeShelf.QUICK_COLUMNS * HomeShelf.QUICK_DOWN

/** A row of records under a heading; the card of [opened] carries [returnTo], and the first card [first] where
 * it is the page's first focus. */
internal fun LazyListScope.recordShelf(
    heading: Int,
    albums: List<CloudAlbum>,
    collection: Collection,
    images: ImageLoader,
    opened: String?,
    returnTo: FocusRequester,
    first: FocusRequester?,
    onOpen: (CloudAlbum) -> Unit,
) {
    item(key = "shelf:$heading") {
        Column(Modifier.padding(top = 24.s)) {
            Heading(stringResource(heading))
            CardRow(albums, key = { it.key }, ahead = coversAhead(collection, images)) { album ->
                val marks = when {
                    album.key == opened -> Modifier.focusRequester(returnTo)
                    first != null && album === albums.first() -> Modifier.focusRequester(first)
                    else -> Modifier
                }
                Box(marks) { AlbumCard(album, collection.cover(album), images) { onOpen(album) } }
            }
        }
    }
}

/** A row of lists under a heading, if there are any. */
internal fun LazyListScope.listShelf(heading: Int, lists: List<TrackList>, collection: Collection, images: ImageLoader, onOpen: (TrackList) -> Unit) {
    if (lists.isEmpty()) return
    item(key = "lists:$heading") {
        Column(Modifier.padding(top = 24.s)) {
            Heading(stringResource(heading))
            CardRow(lists, key = { it.id }, ahead = null) { made -> ListCard(made, collection, images) { onOpen(made) } }
        }
    }
}

/**
 * The popular songs of [records] as [rank] orders them: what is already known of the records' plays at once, then
 * the whole answer once every record has been asked, up to [POPULAR_ASKING] at a time (the phone's
 * `rememberPopular`). Empty until there are enough to show. Asked again when [key] changes (the act or the genre).
 */
@Composable
internal fun rememberPopular(
    key: Any,
    records: List<CloudAlbum>,
    worldPlays: WorldPlays,
    rank: (Map<String, Map<TrackPlace, Long>>) -> List<Pair<CloudTrack, CloudAlbum>>,
): List<Pair<CloudTrack, CloudAlbum>> {
    val known = remember(key) {
        val plays = records.associate { it.key to worldPlays.cached(PlaysRecord.of(it)) }
        if (plays.values.all { it != null }) rank(plays.mapValues { it.value!! }) else emptyList()
    }
    val ranked by produceState(known, key) {
        val gate = Semaphore(POPULAR_ASKING)
        val plays = coroutineScope {
            records.map { record ->
                async { gate.withPermit { record.key to worldPlays.plays(PlaysRecord.of(record)) } }
            }.awaitAll()
        }.mapNotNull { (k, found) -> found?.let { k to it } }.toMap()
        value = rank(plays)
    }
    return ranked
}

/** How many records' plays are asked for at once. */
private const val POPULAR_ASKING = 4

/** "140 songs", or "1 song". */
/**
 * Content that arrives after the page has opened, brought in the way a late article is ([PageBand]): it fades in over
 * [BIO_IN_MS] while it rises [BIO_SLIDE] into place, and what is under it glides down to make room, rather than the
 * row popping in and shoving the records down at once. Content the page opened with is drawn at once.
 */
@Composable
internal fun ArrivesLate(late: Boolean, content: @Composable () -> Unit) {
    val arrival = remember { Animatable(if (late) 0f else 1f) }
    LaunchedEffect(Unit) { if (late) arrival.animateTo(1f, tween(BIO_IN_MS, easing = EaseOutQuad)) }
    val slide = with(LocalDensity.current) { BIO_SLIDE.toPx() }
    Box(
        Modifier
            // The room it takes grows with it, so the rows under it move down over the same time.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, (placeable.height * arrival.value).roundToInt()) { placeable.place(0, 0) }
            }
            .graphicsLayer {
                alpha = arrival.value
                translationY = (1f - arrival.value) * slide
                // Fading a row of covers through an offscreen layer is more than the Amiko's GPU draws in a frame.
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
    ) { content() }
}

@Composable
fun songCount(songs: Int): String =
    if (songs == 1) stringResource(R.string.Stats_SongsOne) else stringResource(R.string.Stats_Songs, songs.toString())

/** How many screens of rows are kept composed ahead of and behind the one on screen: home's figure. */
const val PAGE_KEPT = 3f

/** The band as drawn runs this much further down, so the list starts under the writing and the article does not
 * overlap the songs scrolling under it. The writing keeps its places in the drawn band. */
internal const val BAND_FOOT = 1.2f

/** The band's layout, as shares of the screen's width (the band's height) and of the band (the left column). */
internal const val BAND_HEIGHT = 0.27f
private const val PHOTO_SHARE = 0.5625f
private const val TEXT_SHARE = 0.39f
private const val NAME_TOP = 0.19f
private const val NAME_HEIGHT = 0.26f
private const val BIO_GAP = 0.05f
private const val BIO_LINES = 4

/** How long an article that arrives late takes to come in, and how far it rises as it does. */
private const val BIO_IN_MS = 520
private val BIO_SLIDE = 18.s

/** The counts under the article: dimmer than the article, as the desktop's subtitle lines are. */
private val COUNTS_INK = Color(0x99FFFFFF)

/** How much of the band's height, from its foot, the list scrolls under, fading out: the photograph's own dissolve. */
internal const val LIST_UNDER = 0.3f

/** How much of the photograph dissolves at its left and at its foot. */
private const val DISSOLVE_LEFT = 0.45f
private const val DISSOLVE_FOOT = 0.3f
private const val RAMP_STEPS = 12

/** The desktop's PageWash opacity and PageWeight. */
private const val WASH_ALPHA = 0.55f
private val PAGE_WEIGHT = Color(0x6B000000)

/** The size the blurred photograph is asked for at: it is shrunk to 64 across for the blur anyway ([Soft]). */
private const val WASH_EDGE = 256
