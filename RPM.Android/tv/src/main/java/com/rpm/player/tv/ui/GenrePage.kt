package com.rpm.player.tv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.min
import coil3.ImageLoader
import com.rpm.player.core.R
import com.rpm.player.core.library.ArtistWallItem
import com.rpm.player.core.library.Artwork
import com.rpm.player.core.library.CloudAlbum
import com.rpm.player.core.library.Collection
import com.rpm.player.core.library.GenreMarks
import com.rpm.player.core.library.GenrePopular
import com.rpm.player.core.library.GenreShelves
import com.rpm.player.core.library.GenreWallItem
import com.rpm.player.core.library.Genres
import com.rpm.player.core.library.TrackList
import com.rpm.player.core.library.Walls
import com.rpm.player.core.papers.Dossiers
import com.rpm.player.core.papers.GenreSubject
import com.rpm.player.core.papers.WorldPlays

/**
 * One genre's page, laid out as an artist's page is ([ArtistPage]), fitted
 * for genres: the desktop's photograph for the genre across the top right ([genrePhoto]), dissolving into the
 * page; left of it the genre's wordmark and the first sentences of its encyclopedia article ([GenreSubject], the
 * desktop's English article), or how much of it the collection holds where there is none; then the genre's popular
 * songs, drawn from across the genre ([GenrePopular]); then its notable artists, its generated playlists, the
 * records added most recently, the genres beside it, and its records decade by decade ([GenreShelves]).
 *
 * The band stays where it is and the rest scrolls under its foot, fading out there. The page is drawn on the
 * genre's colour with the photograph heavily blurred over it and the desktop's weight over both ([PageWash]).
 *
 * OK on an artist opens their page, on a playlist its page, on a record its page, on a genre that genre's page;
 * Back comes back here, to the record it left from ([BrowseState.openedAlbum]). OK on a popular song plays the
 * popular songs from it; Play all tracks plays them all.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GenrePage(
    genre: GenreWallItem,
    walls: Walls,
    collection: Collection,
    images: ImageLoader,
    artwork: Artwork,
    dossiers: Dossiers,
    worldPlays: WorldPlays,
    state: BrowseState,
    actions: TrackActions,
    onOpenAlbum: (CloudAlbum) -> Unit,
    onOpenArtist: (ArtistWallItem) -> Unit,
    onOpenList: (TrackList) -> Unit,
    onOpenGenre: (GenreWallItem) -> Unit,
    onBack: () -> Unit,
) {
    val byKey = remember(collection) { collection.albums.associateBy { it.key } }
    val shelves = remember(genre, walls, collection) { GenreShelves.of(genre, walls.genres, byKey, walls.artists) }
    val lists = remember(genre, collection) { collection.lists.filter { it.generated && it.genre == genre.name && it.tracks.isNotEmpty() } }
    val mark = remember(genre.name) { GenreMarks.of(genre.name) }
    val photo = genrePhoto(genre.name)
    // The genre's own colour under the blurred photograph: the colour its card and its wordmark are in.
    val colour = remember(mark) { genreRoom(mark).glow.let { Color(it.r.coerceIn(0f, 1f), it.g.coerceIn(0f, 1f), it.b.coerceIn(0f, 1f)) } }

    val subject = remember(genre.name) { GenreSubject(genre.name) }
    var article by remember(subject) { mutableStateOf(dossiers.cached(subject)?.article) }
    LaunchedEffect(subject) { dossiers.read(subject) {}?.let { article = it.article } }

    val asked = remember(genre, collection) { GenrePopular.records(genre.albums.mapNotNull { byKey[it] }, collection::playsOf) }
    val songs = remember(asked) { GenrePopular.songs(asked) }
    val popular = rememberPopular(genre to collection, asked, worldPlays) { plays -> GenrePopular.ranked(POPULAR_MOST, songs, plays) }
    // Whether the page opened before the popular songs were known: only then are they brought in when they come.
    val popularLate = remember(genre) { popular.isEmpty() }

    val spoken = Genres.spoken(genre.name)
    val added = remember(shelves) { shelves.added.mapNotNull { byKey[it] } }
    val eras = remember(shelves) { shelves.eras.map { (decade, keys) -> decade to keys.mapNotNull { byKey[it] } }.filter { it.second.isNotEmpty() } }

    val first = remember { FocusRequester() }
    val menu = rememberRowMenu()
    val backKey = remember { FocusRequester() }
    val returnTo = remember { FocusRequester() }
    val list = rememberLazyListState(cacheWindow = LazyLayoutCacheWindow(aheadFraction = PAGE_KEPT, behindFraction = PAGE_KEPT))
    val opened = state.openedAlbum
    val focusPopular = popular.isNotEmpty()
    // Whether the first focus is decided: Back to a record, a key pressed, or the popular songs focused.
    var settled by remember(genre) { mutableStateOf(false) }

    // Every item on the page by its key, in order, with the records on it: what Back from a record's page scrolls to.
    val rowKeys = buildList {
        if (focusPopular) add(emptyList<CloudAlbum>())
        if (shelves.acts.isNotEmpty()) add(emptyList())
        if (lists.isNotEmpty()) add(emptyList())
        if (added.isNotEmpty()) add(added)
        if (shelves.related.isNotEmpty()) add(emptyList())
        eras.forEach { (_, albums) -> add(albums) }
    }

    BoxWithConstraints(Modifier.fillMaxSize().onPreviewKeyEvent { settled = true; false }) {
        val bandHigh = maxWidth * BAND_HEIGHT * BAND_FOOT
        val under = bandHigh * LIST_UNDER
        PageWash(photo, colour, images)
        CompositionLocalProvider(LocalBringIntoViewSpec provides CentreRows) {
            LazyColumn(
                state = list,
                contentPadding = PaddingValues(start = 34.px, end = 36.px, top = under, bottom = 44.px),
                modifier = Modifier.padding(top = bandHigh - under).fillMaxSize().fadesAtTop(under),
            ) {
                if (focusPopular) {
                    item(key = "popular") {
                        ArrivesLate(popularLate) { PopularSongs(popular, collection, images, first, actions, menu) }
                    }
                }
                if (shelves.acts.isNotEmpty()) {
                    item(key = "acts") {
                        Column(Modifier.padding(top = 24.s)) {
                            Heading(stringResource(R.string.Genres_NotableArtists, spoken))
                            CardRow(shelves.acts, key = { it.name }, ahead = null) { artist ->
                                val marks = if (!focusPopular && artist === shelves.acts.first()) Modifier.focusRequester(first) else Modifier
                                Box(marks.padding(horizontal = CARD_GAP / 2, vertical = 8.px).width(TILE.s)) {
                                    FaceCard(artist, collection, images, artwork, onClick = { onOpenArtist(artist) }, modifier = Modifier)
                                }
                            }
                        }
                    }
                }
                listShelf(R.string.Home_MadeForYou, lists, collection, images, onOpenList)
                if (added.isNotEmpty()) {
                    genreRecords("added", { stringResource(R.string.Genres_RecentlyAdded, spoken) }, added, collection, images, opened, returnTo, first.takeIf { !focusPopular && shelves.acts.isEmpty() }) { album ->
                        state.openedAlbum = album.key
                        onOpenAlbum(album)
                    }
                }
                if (shelves.related.isNotEmpty()) {
                    item(key = "related") {
                        Column(Modifier.padding(top = 24.s)) {
                            Heading(stringResource(R.string.Genres_Subgenres))
                            CardRow(shelves.related, key = { it.name }, ahead = null) { other ->
                                Box(Modifier.padding(horizontal = CARD_GAP / 2, vertical = 8.px).width((TILE * 1.4f).s)) {
                                    GenreCard(other, images, onClick = { onOpenGenre(other) }, modifier = Modifier)
                                }
                            }
                        }
                    }
                }
                eras.forEach { (decade, albums) ->
                    genreRecords("era:$decade", { stringResource(R.string.Genres_Decade, decade.toString()) }, albums, collection, images, opened, returnTo, null) { album ->
                        state.openedAlbum = album.key
                        onOpenAlbum(album)
                    }
                }
            }
        }
        val fallback = stringResource(R.string.Genres_InLibrary, genre.albums.size.toString(), shelves.actCount.toString())
        PageBand(photo, article?.extract, fallback, images, bandHigh) { GenreTitle(genre, mark) }
        BackKey(onBack, Modifier.focusRequester(backKey).focusProperties { down = first })
    }

    RowMenu(menu)

    // Back from a record's page: that record's card, its row scrolled to. Otherwise the first popular song, or the
    // first artist (or record) while there are none, with the page kept at its top. The popular songs can arrive a
    // moment after the page opens: they then take the focus, unless the remote has moved it since ([settled]).
    LaunchedEffect(genre, focusPopular) {
        if (settled) return@LaunchedEffect
        val row = rowKeys.indexOfFirst { albums -> albums.any { it.key == opened } }
        if (opened != null && row >= 0) {
            settled = true
            list.scrollToItem(row)
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

/** A row of records under a heading; the card of [opened] carries [returnTo], and the first card [first] where it
 * is the page's first focus. */
private fun androidx.compose.foundation.lazy.LazyListScope.genreRecords(
    key: String,
    heading: @Composable () -> String,
    albums: List<CloudAlbum>,
    collection: Collection,
    images: ImageLoader,
    opened: String?,
    returnTo: FocusRequester,
    first: FocusRequester?,
    onOpen: (CloudAlbum) -> Unit,
) {
    item(key = key) {
        Column(Modifier.padding(top = 24.s)) {
            Heading(heading())
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

/** The genre's wordmark, as large as the name's box takes it ([GenreLettering]); its name where it has none. */
@Composable
private fun GenreTitle(genre: GenreWallItem, mark: com.rpm.player.core.library.GenreWordmark?) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (mark != null) {
            // The wordmark's full height is its letters over its base; as tall as the box, or as wide.
            val high = min(maxHeight * TITLE_FILL, maxWidth * TITLE_FILL / mark.aspect.toFloat())
            GenreLettering(mark, high * mark.base.toFloat(), Modifier)
        } else {
            Text(genre.name, style = ui(44.ts, FontWeight.Bold), color = Ink.Words, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

/** How much of the name's box the wordmark fills, across or down. */
private const val TITLE_FILL = 0.9f

/**
 * The desktop's photograph for [genre], cut to the TV's photograph box (scripts/gen_tv_genre_photos.py), as an
 * asset address Coil reads; null for a genre with none, whose page is drawn on its colour alone.
 */
@Composable
internal fun genrePhoto(genre: String): String? {
    val context = LocalContext.current
    val shipped = remember { runCatching { context.assets.list(PHOTOS)?.toSet() }.getOrNull().orEmpty() }
    val file = photoSlug(genre) + ".jpg"
    return if (file in shipped) "file:///android_asset/$PHOTOS/$file" else null
}

private const val PHOTOS = "genre-photos"

/** The desktop's file name for a genre (gen_genre_photos.py's `slug`): lower case, every run of anything but a letter
 * or a digit one hyphen, none at the ends. "Hip-Hop / Rap" is hip-hop-rap, "R&B" is r-b. */
internal fun photoSlug(genre: String): String =
    genre.lowercase().replace(Regex("[^a-z0-9]+"), "-").replace(Regex("-+"), "-").trim('-')
