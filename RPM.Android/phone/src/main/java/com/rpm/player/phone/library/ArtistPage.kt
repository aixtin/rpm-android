package com.rpm.player.phone.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import com.rpm.player.phone.PhoneApplication
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.rpm.player.core.R
import com.rpm.player.core.cloud.CloudTrack
import com.rpm.player.core.library.ArtistShelves
import com.rpm.player.core.library.ArtistSongListing
import com.rpm.player.core.library.ArtistTrackGrouping
import com.rpm.player.core.library.ArtistWallItem
import com.rpm.player.core.library.ArtistPopular
import com.rpm.player.core.art.ArtScene
import com.rpm.player.core.library.Artwork
import com.rpm.player.core.library.QuickPick
import com.rpm.player.core.papers.ArtistSubject
import com.rpm.player.core.papers.Dossiers
import com.rpm.player.core.papers.PlaysRecord
import com.rpm.player.core.papers.WorldPlays
import com.rpm.player.phone.home.HomeActions
import com.rpm.player.phone.home.PicksRow
import com.rpm.player.phone.home.SongLine
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.rpm.player.core.library.CloudAlbum
import com.rpm.player.core.library.Collection
import com.rpm.player.phone.design.BackKey
import com.rpm.player.phone.design.GlassButton
import com.rpm.player.phone.design.Glyph
import com.rpm.player.phone.design.Ink
import com.rpm.player.phone.design.Weight
import com.rpm.player.phone.design.backdropSource
import com.rpm.player.phone.design.display
import com.rpm.player.phone.design.displayBold
import com.rpm.player.phone.design.dissolvesAtTop
import com.rpm.player.phone.design.icon
import com.rpm.player.phone.design.kicker
import com.rpm.player.phone.design.rememberBackdrop
import com.rpm.player.phone.design.scrolled
import com.rpm.player.phone.design.system
import com.rpm.player.phone.ui.ArtGround

/** The space between the name and the foot of the photograph. */
private val WRITING_FOOT = 14.dp

/**
 * The space at the band's right edge, and the height of the writing along its foot. The photograph's crop keeps
 * faces out of these areas (`HomeBannerMetrics.margin` and `ArtistPage.writing` on iPhone).
 */
private const val BANNER_MARGIN = 22.0
private const val WRITING = 96.0

/** The darkening over the photograph. Home's banner uses the same value (`HomeFeatureCard.held` on iPhone). */
private const val HELD = 0.2f

/**
 * One artist's page. It matches the iPhone's `ArtistPage` (RPMUI/ArtistPage.swift). From the top: the artist's
 * photograph, a page wide and as tall as that. Their name is at its foot. Then the encyclopedia text, Play all,
 * and shuffle. Then their popular songs. Then their records on shelves by kind: their own records, records made
 * with others, live albums, soundtracks, compilations, singles, and the records they appear on. Last, every song
 * by the artist, under All tracks. The songs are grouped by letter, or by year, once there are more than twenty.
 * The page uses the photograph as its blurred background.
 *
 * The popular songs are ranked by how often each song has been played by all listeners ([ArtistPopular]). The
 * plays are asked for up to four records at a time. A page opened again starts from the plays that are already known.
 *
 * The photograph is cropped so that the artist's face stays in view ([Portraits]).
 *
 * The title is the artist's logo, where a catalogue has one, cut to its lettering ([Logos]). Otherwise the title
 * is the artist's name.
 *
 * Not yet implemented: the background that the page shows before a photograph arrives, the stretch on an
 * over-pull. A song's long press, among the popular songs or under All tracks, opens the song menu ([LocalSongMenu]).
 *
 * @param playing the id of the track playing, which the mark in the margin follows.
 * @param onPlay plays the songs from the one at that index: a tap on a song.
 * @param onPlayAll the Play and Shuffle keys: all of these songs, in this order. See [AlbumPage] for why a key and a
 * row differ.
 */
@Composable
fun ArtistPage(
    artist: ArtistWallItem,
    collection: Collection,
    images: ImageLoader,
    dossiers: Dossiers,
    worldPlays: WorldPlays,
    grouping: ArtistTrackGrouping,
    onGrouping: (ArtistTrackGrouping) -> Unit,
    playing: String?,
    bottomRoom: Dp,
    onPlay: (List<CloudTrack>, Int) -> Unit,
    onOpen: (CloudAlbum) -> Unit,
    onBack: () -> Unit,
    onPlayAll: (List<CloudTrack>) -> Unit = { onPlay(it, 0) },
) {
    BackHandler(onBack = onBack)
    val page = rememberBackdrop()
    val rows = rememberLazyListState()
    val density = LocalDensity.current
    val statusBar = WindowInsets.statusBars.asPaddingValues(density).calculateTopPadding()
    val portraits = (LocalContext.current.applicationContext as PhoneApplication).portraits
    val shelves = remember(artist, collection) { ArtistShelves.of(artist, collection.albums.associateBy { it.key }) }
    val noYear = stringResource(R.string.AlbumGroup_NoYear)
    val listing = remember(shelves, grouping, noYear) { ArtistSongListing(shelves.songs, grouping, noYear) }
    val popular = rememberPopular(shelves, worldPlays)
    // Identified through their own records, never by the name alone (ArtistSubject.of).
    val subject = remember(artist.name, shelves) { ArtistSubject.of(artist.name, shelves) }
    var article by remember(subject) { mutableStateOf(dossiers.cached(subject)?.article) }
    LaunchedEffect(subject) { dossiers.read(subject) {}?.let { article = it.article } }
    var reading by remember(subject) { mutableStateOf(false) }
    BackHandler(enabled = reading) { reading = false }
    val picks = HomeActions(
        onOpen = onOpen,
        onOpenList = {},
        onPlay = { tracks -> onPlay(tracks, 0) },
        onTune = {},
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val plate = maxWidth
        // The frame of the band, as the iPhone cuts it: a square as wide as the page, with the status bar above it,
        // space for the keys at its right, and space for the name along its foot.
        val scene = remember(plate, statusBar) {
            val edge = plate.value.toDouble()
            ArtScene.forClearLeft(edge, edge, clearTop = statusBar.value / edge, clearRight = (edge - BANNER_MARGIN) / edge, clearBottom = (edge - WRITING) / edge)
        }
        // The artist's logo cut for the band, or their name where there is no logo. Nothing is drawn until the
        // lookup has answered. So the name is not drawn and then replaced a moment later.
        val logos = (LocalContext.current.applicationContext as PhoneApplication).logos
        val cap = with(density) { LOGO_CAP.dp.toPx() }.toInt() + 1
        val room = with(density) { (plate * LOGO_SHARE).toPx() }.toInt()
        val title by produceState<Title?>(null, artist.name, cap, room) {
            val album = artist.own.firstNotNullOfOrNull { key -> collection.album(key)?.album?.takeIf { it.isNotBlank() } }.orEmpty()
            value = logos.logo(artist.name, album, cap, room)?.let { Title.Logo(artist.name, it) } ?: Title.Name(artist.name)
        }
        val photo by produceState<Bitmap?>(null, artist.name, scene) {
            val album = artist.own.firstNotNullOfOrNull { key -> collection.album(key)?.album?.takeIf { it.isNotBlank() } }.orEmpty()
            value = portraits.portrait(artist.name, album, scene)
        }
        val sleeve = (plate.value * 0.38f).coerceIn(120f, 200f).toDouble()
        Box(Modifier.fillMaxSize().backdropSource(page)) {
            ArtGround(photo, images)
            LazyColumn(state = rows, modifier = Modifier.fillMaxSize().dissolvesAtTop(statusBar + BACK_ROW) { rows.scrolled() }) {
                item(key = "band") {
                    Band(title?.takeIf { it.artist == artist.name }, photo, plate, images)
                    article?.let { AboutText(it, onOpen = { reading = true }, Modifier.padding(horizontal = 20.dp).padding(top = 10.dp)) }
                    if (shelves.songs.isNotEmpty()) {
                        Keys(
                            onPlay = { onPlayAll(shelves.songs.map { it.first }) },
                            onShuffle = { onPlayAll(shelves.songs.map { it.first }.shuffled()) },
                        )
                    }
                }
                if (popular.isNotEmpty()) {
                    item(key = "popular") {
                        Box(Modifier.padding(top = 18.dp)) {
                            PicksRow(
                                stringResource(R.string.Artists_PopularTracks),
                                popular.map { (track, album) -> QuickPick.Song(track, album) },
                                collection,
                                images,
                                logoStamp = "",
                                actions = picks,
                                songLine = SongLine.Record,
                            )
                        }
                    }
                }
                shelf(R.string.Artists_Albums, shelves.records, collection, images, sleeve, onOpen)
                shelf(R.string.Artists_Collaborations, shelves.together, collection, images, sleeve, onOpen)
                shelf(R.string.Artists_Eps, shelves.eps, collection, images, sleeve, onOpen)
                shelf(R.string.Artists_LiveAlbums, shelves.live, collection, images, sleeve, onOpen)
                shelf(R.string.Artists_Mixtapes, shelves.mixtapes, collection, images, sleeve, onOpen)
                shelf(R.string.Artists_Soundtracks, shelves.soundtracks, collection, images, sleeve, onOpen)
                shelf(R.string.Artists_Compilations, shelves.compilations, collection, images, sleeve, onOpen)
                shelf(R.string.Artists_Singles, shelves.singles, collection, images, sleeve, onOpen)
                shelf(R.string.Artists_AppearsOn, shelves.appearances, collection, images, sleeve, onOpen)
                songs(listing, playing, onGrouping) { at -> onPlay(listing.songs.map { it.first }, at) }
                item(key = "foot") { Spacer(Modifier.height(40.dp + bottomRoom)) }
            }
        }
        Box(Modifier.padding(top = statusBar).height(BACK_ROW).padding(start = 20.dp), contentAlignment = Alignment.Center) {
            BackKey(page, onBack)
        }
        article?.takeIf { reading }?.let {
            AboutSheet(stringResource(R.string.TrackInfo_AboutArtist), artist.name, it, page, bottomRoom, onClose = { reading = false })
        }
    }
}

/**
 * The artist's popular songs. The plays that are already known are used at once. The full answer comes once
 * every record has been asked, up to four at a time (`ArtistPage.rankPopular` on iPhone). The list is empty
 * until there is enough to show.
 */
@Composable
private fun rememberPopular(shelves: ArtistShelves, worldPlays: WorldPlays): List<Pair<CloudTrack, CloudAlbum>> {
    val known = remember(shelves) {
        val records = ArtistPopular.records(shelves.songs)
        val plays = records.associate { it.key to worldPlays.cached(PlaysRecord.of(it)) }
        if (plays.values.all { it != null }) ArtistPopular.ranked(ArtistPopular.MOST, shelves.songs, plays.mapValues { it.value!! }) else emptyList()
    }
    val ranked by produceState(known, shelves) {
        val gate = Semaphore(POPULAR_ASKING)
        val plays = coroutineScope {
            ArtistPopular.records(shelves.songs).map { record ->
                async { gate.withPermit { record.key to worldPlays.plays(PlaysRecord.of(record)) } }
            }.awaitAll()
        }.mapNotNull { (key, found) -> found?.let { key to it } }.toMap()
        value = ArtistPopular.ranked(ArtistPopular.MOST, shelves.songs, plays)
    }
    return ranked
}

/** How many records' plays are asked for at once. */
private const val POPULAR_ASKING = 4

/** The photograph, as wide as the page and as tall. It is darker towards the foot, where the name stands. */
@Composable
private fun Band(title: Title?, photo: Bitmap?, plate: Dp, images: ImageLoader) {
    val density = LocalDensity.current
    Box(Modifier.fillMaxWidth().height(plate)) {
        if (photo != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(photo).build(),
                contentDescription = null,
                imageLoader = images,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = HELD)))
        } else {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0x22 / 255f)))
        }
        // Darker towards the foot, where the name stands. Home's banner does the same.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.4f to Color.Black.copy(alpha = 0.25f),
                    0.68f to Color.Black.copy(alpha = 0.7f),
                    1f to Color.Black.copy(alpha = 0.7f),
                ),
            ),
        )
        val shadow = with(density) { Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1.dp.toPx()), blurRadius = 6.dp.toPx()) }
        when (title) {
            is Title.Logo -> {
                // Drawn at its own pixel size. The logo store has already reduced it to the pixels it is drawn at.
                val mark = remember(title.picture) { title.picture.asImageBitmap() }
                Image(
                    mark,
                    contentDescription = title.artist,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = WRITING_FOOT)
                        .size(with(density) { mark.width.toDp() }, with(density) { mark.height.toDp() }),
                    filterQuality = FilterQuality.None,
                )
            }
            is Title.Name -> BasicText(
                title.artist,
                Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp).padding(bottom = WRITING_FOOT),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = displayBold(26f).copy(color = Color.White, textAlign = TextAlign.Center, shadow = shadow),
            )
            null -> Unit
        }
    }
}

/**
 * The band's title, and whose it is: the artist's logo, or the artist's name if there is no logo or it could
 * not be read. It is held with the artist, so a title from another artist is never drawn.
 */
private sealed interface Title {
    val artist: String

    class Logo(override val artist: String, val picture: Bitmap) : Title

    class Name(override val artist: String) : Title
}

/**
 * The tallest a logo is drawn, in points, and the largest share of the band's width it may take (`ArtistPage.logoCap`
 * and `logoShare` on iPhone).
 */
private const val LOGO_CAP = 50
private const val LOGO_SHARE = 0.7f

/** Play all, and shuffle beside it, for the same songs. */
@Composable
private fun Keys(onPlay: () -> Unit, onShuffle: () -> Unit) {
    Row(Modifier.padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassButton(onClick = onPlay, modifier = Modifier.weight(1f), weight = Weight.Primary, height = 50.dp, radius = 14.dp) { style ->
            BasicText(Glyph.play, style = icon(15f).copy(color = style.color))
            Spacer(Modifier.width(10.dp))
            BasicText(stringResource(R.string.Rpm_Artist_PlayAll), style = display(17f).copy(color = style.color))
        }
        GlassButton(onClick = onShuffle, modifier = Modifier.width(56.dp), stretches = false, height = 50.dp, radius = 14.dp, inset = 0.dp) { style ->
            BasicText(Glyph.shuffle, style = icon(17f).copy(color = style.color))
        }
    }
}

/** One shelf of records, scrolled sideways, with its heading above the covers. An empty shelf shows nothing. */
private fun LazyListScope.shelf(
    heading: Int,
    records: List<CloudAlbum>,
    collection: Collection,
    images: ImageLoader,
    sleeve: Double,
    onOpen: (CloudAlbum) -> Unit,
) {
    if (records.isEmpty()) return
    item(key = "shelf:$heading") {
        val caption = remember(sleeve) { AlbumCaption.of(sleeve) }
        Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(stringResource(heading).uppercase(), Modifier.padding(horizontal = 20.dp), style = kicker())
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(records, key = { it.key }) { album -> AlbumTile(album, collection.cover(album), caption, images) { onOpen(album) } }
            }
        }
    }
}

/** All tracks: the heading, with the letter-or-year switch once the songs are grouped, then the groups. */
private fun LazyListScope.songs(listing: ArtistSongListing, playing: String?, onGrouping: (ArtistTrackGrouping) -> Unit, onPlay: (Int) -> Unit) {
    if (listing.songs.isEmpty()) return
    item(key = "songs") {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(stringResource(R.string.Artists_AllTracks).uppercase(), Modifier.weight(1f), style = kicker())
            if (listing.isGrouped) GroupingSwitch(listing.grouping, onGrouping)
        }
    }
    var at = 0
    for (group in listing.groups) {
        group.heading?.let { heading ->
            item(key = "group:$heading") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicText(heading, style = kicker())
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.white(0x1F)))
                }
            }
        }
        for ((track, album) in group.songs) {
            val index = at++
            item(key = "song:${track.rpmId}:$index") { SongRow(track, album, playing = track.rpmId == playing) { onPlay(index) } }
        }
    }
}

/** By letter or by year: two keys on one small glass face. The chosen one is lit (`TrackGroupingSwitch` on iPhone). */
@Composable
private fun GroupingSwitch(grouping: ArtistTrackGrouping, onGrouping: (ArtistTrackGrouping) -> Unit) {
    val face = RoundedCornerShape(7.dp)
    Row(Modifier.clip(face).background(Ink.glassWash).border(1.dp, Ink.white(0x22), face).padding(1.dp)) {
        for ((choice, glyph) in listOf(ArtistTrackGrouping.Letter to Glyph.byLetter, ArtistTrackGrouping.Year to Glyph.byYear)) {
            val on = grouping == choice
            Box(
                Modifier
                    .size(40.dp, 30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (on) Ink.selectedWash else Color.Transparent)
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { if (!on) onGrouping(choice) },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(glyph, style = icon(13f).copy(color = if (on) Color.White else Ink.white(0xCC)))
            }
        }
    }
}

/**
 * One song: the playing mark in the margin, the title (with the E for explicit lyrics) over the name of its
 * record, and the length. The row is lit while a finger is on it.
 */
@Composable
private fun SongRow(track: CloudTrack, album: CloudAlbum, playing: Boolean, onClick: () -> Unit) {
    val presses = remember { MutableInteractionSource() }
    val pressed by presses.collectIsPressedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .background(if (pressed) Ink.white(0x24) else Color.Transparent)
            .songPress(track, presses, onClick)
            .padding(start = 8.dp, end = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(28.dp).clip(RoundedCornerShape(percent = 50)).background(if (playing) Color.White else Color.Transparent))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(track.title.orEmpty(), Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis, style = display(16f).copy(color = Color.White))
                if (track.tags?.advisory == EXPLICIT) ExplicitMark()
            }
            BasicText(album.album, maxLines = 1, overflow = TextOverflow.Ellipsis, style = display(13f).copy(color = Ink.white(0x77)))
        }
        BasicText(length(track.tags?.durationSeconds ?: 0.0), style = display(13f).copy(color = Ink.white(0x66)))
    }
}
