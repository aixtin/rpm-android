package com.rpm.player.phone.home

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.rpm.player.core.R
import com.rpm.player.core.cloud.CloudTrack
import com.rpm.player.core.art.ArtScene
import android.graphics.Bitmap
import com.rpm.player.phone.PhoneApplication
import com.rpm.player.core.library.BannerCard
import com.rpm.player.core.library.CloudAlbum
import com.rpm.player.core.library.Collection
import com.rpm.player.core.library.HomeShelf
import com.rpm.player.core.library.Kicker
import com.rpm.player.core.library.LibraryState
import com.rpm.player.core.library.QuickPick
import com.rpm.player.core.library.TrackList
import com.rpm.player.core.library.listName
import com.rpm.player.core.radio.RadioStation
import com.rpm.player.core.radio.StationLogo
import com.rpm.player.phone.design.Glyph
import com.rpm.player.phone.design.Ink
import com.rpm.player.phone.design.PageTitle
import com.rpm.player.phone.design.display
import com.rpm.player.phone.design.displayBold
import com.rpm.player.phone.design.dissolvesAtTop
import com.rpm.player.phone.design.icon
import com.rpm.player.phone.design.pt
import com.rpm.player.phone.design.radiusFor
import com.rpm.player.phone.design.system
import com.rpm.player.phone.library.AlbumCaption
import com.rpm.player.phone.library.AlbumTile
import com.rpm.player.phone.library.FOOT
import com.rpm.player.phone.ui.ArtGround

/**
 * The height of the banner's plate, before it reaches up behind the title and the status bar (`HomeBannerMetrics` on
 * iPhone).
 */
private val BANNER = 352.dp
private val BANNER_SLEEVE = 124.dp
private val BANNER_MARGIN = 22.dp

/** The cover size in a row (`HomeRowView.art` on iPhone). */
private const val ROW_ART = 132.0

/**
 * The quick selection: sixteen picks, four lines to a panel, with the edge of the next panel showing (`QuickPicks` on
 * iPhone).
 */
private const val QUICK_PICKS = 16
private const val QUICK_TALL = 4
private val QUICK_LINE = 60.dp
private val QUICK_ART = 44.dp
private val PEEK = 32.dp
private val PANELS_APART = 16.dp

/** The space between the sections of the page. */
private val SECTIONS_APART = 26.dp

/** What home's taps do: open a record or a list, play songs, tune a station. */
class HomeActions(
    val onOpen: (CloudAlbum) -> Unit,
    val onOpenList: (TrackList) -> Unit,
    val onPlay: (List<CloudTrack>) -> Unit,
    val onTune: (RadioStation) -> Unit,
    /** Start radio on a song: it plays now and the queue keeps filling with songs like it. */
    val onRadio: (CloudTrack) -> Unit = {},
)

/**
 * [stations] are the stations heard most, as the rules from the server show them. They feed the banner's radio
 * card and the quick selection's stations.
 *
 * Home: what has been played, what to play next, and what was added last. This matches the iPhone's `HomePage`
 * (RPMUI/HomePage.swift). It is drawn from the home data that `:core` builds for the TV app, using the desktop's
 * rules. From the top: the banner, the quick selection directly under it, the rows of records, and the computer's
 * lists.
 *
 * The page uses the colours of the banner's front card, the one card with a picture. The title stands over the
 * scroll. The banner runs up behind the title and behind the status bar.
 *
 * Not yet implemented: the banner's copy in the page, its rooms and its automatic turning, the lists' framed
 * covers, the collection switcher, and the lock that a library too small for home gets.
 */
@Composable
fun HomePage(
    ready: LibraryState.Ready,
    stations: List<RadioStation>,
    logoStamp: String,
    images: ImageLoader,
    bottomRoom: Dp,
    actions: HomeActions,
) {
    val collection = ready.collection
    val home = ready.home
    val density = LocalDensity.current
    val banner = remember(home.banner, stations) { HomeShelf.withStation(home.banner, stations.firstOrNull()) }
    val quick = remember(home.quick, stations) { HomeShelf.withStations(home.quick, stations).take(QUICK_PICKS) }
    val statusBar = WindowInsets.statusBars.asPaddingValues(density).calculateTopPadding()
    var heading by remember { mutableStateOf(0.dp) }
    var dress by remember { mutableStateOf<Any?>(null) }
    val scroll = rememberScrollState()
    // How far the banner reaches up: the title and the status bar. This is known once the title has been measured.
    val overhang = if (banner.isNotEmpty() && heading > 0.dp) heading + statusBar else 0.dp

    Box(Modifier.fillMaxSize()) {
        ArtGround(dress, images)
        Column(
            Modifier
                .fillMaxSize()
                .dissolvesAtTop(statusBar + heading) { scroll.value.toFloat() }
                .verticalScroll(scroll)
                .padding(bottom = FOOT + bottomRoom),
            verticalArrangement = Arrangement.spacedBy(SECTIONS_APART),
        ) {
            if (banner.isNotEmpty()) {
                Banner(banner, collection, images, logoStamp, overhang, statusBar, onDress = { dress = it }, actions)
            } else {
                Spacer(Modifier.height(heading + statusBar))
            }
            if (quick.isNotEmpty()) PicksRow(stringResource(R.string.Home_QuickPicks), quick, collection, images, logoStamp, actions, SongLine.Artist)
            for ((label, albums) in listOf(
                R.string.Home_Listened to home.listened,
                R.string.Home_Suggested to home.suggested,
                R.string.Home_Taste to home.taste,
                R.string.Home_Played to home.played,
                R.string.Home_Added to home.added,
            )) {
                if (albums.isNotEmpty()) AlbumRow(stringResource(label), albums, collection, images, actions.onOpen)
            }
            if (home.madeForYou.isNotEmpty()) ListsRow(stringResource(R.string.Home_MadeForYou), home.madeForYou, collection, images, actions.onOpenList)
        }
        // The title is drawn over the scroll. It does not move with the scroll. It sits on the same line as the title
        // of every other page.
        PageTitle(
            stringResource(R.string.Home_Heading),
            Modifier
                .statusBarsPadding()
                .padding(start = 20.dp, end = 16.dp, bottom = 10.dp)
                .onSizeChanged { heading = with(density) { it.height.toDp() } + 10.dp },
        )
    }
}

// ── the banner ──

/**
 * Cards across the top of the page, one screen wide. They are swiped one card at a time, with a dot under them
 * for each card. The card in front sets the page's colours.
 */
@Composable
private fun Banner(
    cards: List<BannerCard>,
    collection: Collection,
    images: ImageLoader,
    logoStamp: String,
    overhang: Dp,
    statusBar: Dp,
    onDress: (Any?) -> Unit,
    actions: HomeActions,
) {
    val pager = rememberPagerState { cards.size }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // The photograph is cut for the height the card is drawn at, including the overhang. If it were cut for
            // the plate alone and then stretched to the taller card, a face in the clear area would be pushed up behind
            // the status bar.
            val scene = remember(maxWidth, overhang, statusBar) { bannerScene(maxWidth, overhang, statusBar) }
            HorizontalPager(pager, Modifier.fillMaxWidth().height(BANNER + overhang)) { page ->
                // No cut is made until the heading has been measured, because the overhang it gives changes the frame.
                BannerCardView(cards[page], live = page == pager.currentPage, collection, images, scene.takeIf { overhang > 0.dp }, logoStamp, onDress, actions)
            }
        }
        if (cards.size > 1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                for (i in cards.indices) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(if (i == pager.currentPage) Color.White else Ink.white(0x44)))
                }
            }
        }
    }
}

/**
 * The frame that a banner photograph is cut for. It matches the iPhone's `HomeFeatureCard.scene` and
 * `HomeBannerMetrics`. The frame is the card's width and drawn height, with the status bar over its top, the
 * right margin, and the space for the cover and the writing along its foot.
 */
private fun bannerScene(width: Dp, overhang: Dp, statusBar: Dp): ArtScene {
    val wide = width.value.toDouble()
    val plate = (BANNER + overhang).value.toDouble()
    val band = (BANNER_MARGIN + BANNER_SLEEVE + BANNER_MARGIN).value.toDouble()
    val covered = minOf(statusBar.value, overhang.value).coerceAtLeast(0f).toDouble()
    return ArtScene.forClearLeft(
        wide,
        plate,
        clearTop = minOf(0.5, covered / plate),
        clearRight = if (wide > 0) ((wide - BANNER_MARGIN.value) / wide).coerceIn(0.2, 1.0) else 1.0,
        clearBottom = ((plate - band) / plate).coerceIn(0.2, 1.0),
    )
}

/**
 * One card: the artist's photograph where there is one, dimmed by a fifth and darker toward the foot where the
 * writing is. It also shows the cover, the kind of card, the title and the artist. Without a photograph, the
 * blurred cover stands in for the background that the iPhone draws there.
 */
@Composable
private fun BannerCardView(
    card: BannerCard,
    live: Boolean,
    collection: Collection,
    images: ImageLoader,
    scene: ArtScene?,
    logoStamp: String,
    onDress: (Any?) -> Unit,
    actions: HomeActions,
) {
    val album = (card as? BannerCard.OfAlbum)?.album
    val list = (card as? BannerCard.OfList)?.list
    val station = (card as? BannerCard.OfStation)?.station
    val cover: Any? = album?.let { collection.cover(it) } ?: list?.let { collection.cover(it) }
    val portraits = (LocalContext.current.applicationContext as PhoneApplication).portraits
    // The artist's square photograph, if its face is clear of the frame. Otherwise the backdrop, cropped around the
    // faces. This is what the iPhone's card asks for (`BackdropStore.portrait`).
    val photo by produceState<Bitmap?>(null, album?.key, scene) {
        val record = album?.takeIf { it.artist.isNotBlank() } ?: return@produceState
        value = scene?.let { portraits.portrait(record.artist, record.album, it) }
    }
    val wearing = photo ?: cover
    LaunchedEffect(live, wearing) { if (live && wearing != null) onDress(wearing) }

    val kicker = when (card) {
        is BannerCard.OfAlbum -> stringResource(kickerOf(card.kicker))
        is BannerCard.OfList -> stringResource(R.string.Home_MadeForYou)
        is BannerCard.OfStation -> stringResource(R.string.Rpm_Tab_Radio)
    }
    val title = album?.album ?: list?.let { listName(it.name) } ?: station?.display.orEmpty()
    val subtitle = album?.artist

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.ground)
            .clickable(remember { MutableInteractionSource() }, indication = null) {
                when {
                    album != null -> actions.onOpen(album)
                    list != null -> actions.onOpenList(list)
                    station != null -> actions.onTune(station)
                }
            },
    ) {
        when {
            photo != null -> {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(photo).build(),
                    contentDescription = null,
                    imageLoader = images,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
            }
            cover != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val density = LocalDensity.current
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(cover).build(),
                    contentDescription = null,
                    imageLoader = images,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(with(density) { radiusFor(40.dp.toPx()).toDp() }, BlurredEdgeTreatment.Rectangle),
                )
            }
        }
        // Darker towards the foot, where the writing stands.
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
        Row(
            Modifier.align(Alignment.BottomStart).padding(BANNER_MARGIN),
            horizontalArrangement = Arrangement.spacedBy(BANNER_MARGIN),
            verticalAlignment = Alignment.Bottom,
        ) {
            BannerSleeve(cover, station, logoStamp, images)
            val shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), blurRadius = 6f)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(kicker, style = display(12f).copy(color = Ink.white(0xCC), letterSpacing = pt(1.5f), shadow = shadow))
                BasicText(title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = displayBold(22f).copy(color = Color.White, shadow = shadow))
                if (!subtitle.isNullOrEmpty()) {
                    BasicText(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = system(13f).copy(color = Ink.white(0xD1), shadow = shadow))
                }
            }
        }
    }
}

@Composable
private fun BannerSleeve(cover: Any?, station: RadioStation?, logoStamp: String, images: ImageLoader) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .size(BANNER_SLEEVE)
            .shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.5f))
            .clip(shape)
            .background(Color.Black.copy(alpha = 0x22 / 255f))
            .border(1.dp, Ink.cardBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(if (station != null) Glyph.radio else Glyph.record, style = icon(34f).copy(color = Ink.white(0x3A)))
        val model: Any? = station?.let { StationLogo.of(it, logoStamp) } ?: cover
        if (model != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(model).build(),
                contentDescription = null,
                imageLoader = images,
                contentScale = if (station != null) ContentScale.Fit else ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = if (station != null) Modifier.fillMaxSize().padding(14.dp) else Modifier.fillMaxSize(),
            )
        }
    }
}

private fun kickerOf(kicker: Kicker): Int = when (kicker) {
    Kicker.CONTINUE -> R.string.Home_Continue
    Kicker.ADDED -> R.string.Home_Added
    Kicker.MADE_FOR_YOU -> R.string.Home_MadeForYou
    Kicker.SUGGESTED -> R.string.Home_Suggested
    Kicker.PLAYED -> R.string.Home_Played
    Kicker.LISTENED -> R.string.Home_Listened
}

// ── the quick selection, and any row of picks ──

/** What the line under a song says: its artist, or, on a page that is already about the artist, its record. */
enum class SongLine { Artist, Record }

/**
 * A row of picks under a heading. This matches the iPhone's `HomePicksRow`. Home's quick selection and an
 * artist's popular songs both use it. The picks are lines, not squares. They fill panels of four, from the top
 * down. The user swipes one panel at a time, and the edge of the next panel shows. The play key beside the
 * heading plays the songs, and only the songs. A record is a group of songs, but a station is not a set of
 * songs, so the key plays only songs.
 */
@Composable
fun PicksRow(
    heading: String,
    picks: List<QuickPick>,
    collection: Collection,
    images: ImageLoader,
    logoStamp: String,
    actions: HomeActions,
    songLine: SongLine,
) {
    val songs = picks.filterIsInstance<QuickPick.Song>().map { it.track }
    val panels = picks.chunked(QUICK_TALL)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.height(34.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(heading, style = display(19f).copy(color = Color.White))
            if (songs.isNotEmpty()) {
                Box(
                    Modifier.size(34.dp).clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { actions.onPlay(songs) },
                    contentAlignment = Alignment.Center,
                ) { BasicText(Glyph.play, style = icon(12f).copy(color = Ink.white(0xB8))) }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = maxWidth - 40.dp - if (panels.size > 1) PEEK else 0.dp
            val trailing = maxOf(20.dp, maxWidth - 20.dp - width)
            HorizontalPager(
                rememberPagerState { panels.size },
                pageSize = PageSize.Fixed(width),
                contentPadding = PaddingValues(start = 20.dp, end = trailing),
                pageSpacing = PANELS_APART,
                modifier = Modifier.height(QUICK_LINE * (panels.maxOfOrNull { it.size } ?: 0)),
                verticalAlignment = Alignment.Top,
            ) { page ->
                Column { for (pick in panels[page]) QuickLine(pick, collection, images, logoStamp, actions, songLine) }
            }
        }
    }
}

@Composable
private fun QuickLine(pick: QuickPick, collection: Collection, images: ImageLoader, logoStamp: String, actions: HomeActions, songLine: SongLine) {
    val album: CloudAlbum? = when (pick) {
        is QuickPick.Song -> pick.album
        is QuickPick.Radio -> pick.album
        is QuickPick.OfAlbum -> pick.album
        else -> null
    }
    val picture: Any? = when (pick) {
        is QuickPick.OfStation -> StationLogo.of(pick.station, logoStamp)
        is QuickPick.OfList -> collection.cover(pick.list)
        else -> album?.let { collection.cover(it) }
    }
    val (title, subtitle, glyph) = when (pick) {
        is QuickPick.Song -> Triple(
            pick.track.title.orEmpty(),
            if (songLine == SongLine.Record) pick.album?.album ?: pick.track.album else pick.track.artist,
            Glyph.song,
        )
        is QuickPick.OfAlbum -> Triple(pick.album.album, kindLine(stringResource(R.string.Home_QuickAlbum), pick.album.artist), Glyph.record)
        is QuickPick.OfList -> Triple(
            listName(pick.list.name),
            kindLine(stringResource(R.string.Home_QuickPlaylist), stringResource(R.string.Home_Songs, pick.list.tracks.size)),
            Glyph.playlists,
        )
        is QuickPick.OfStation -> Triple(pick.station.display, stringResource(R.string.Home_QuickStation), Glyph.radio)
        // Named for the song it starts from, the kind first on the line under it, so it reads as a radio and not as
        // the song's own line further on.
        is QuickPick.Radio -> Triple(pick.track.title.orEmpty(), kindLine(stringResource(R.string.Home_QuickRadio), pick.track.artist), Glyph.radio)
    }
    val onClick: (() -> Unit)? = when (pick) {
        is QuickPick.Song -> { { actions.onPlay(listOf(pick.track)) } }
        is QuickPick.OfAlbum -> { { actions.onOpen(pick.album) } }
        is QuickPick.OfStation -> { { actions.onTune(pick.station) } }
        is QuickPick.OfList -> { { actions.onOpenList(pick.list) } }
        is QuickPick.Radio -> { { actions.onRadio(pick.track) } }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(QUICK_LINE)
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = onClick != null) { onClick?.invoke() },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shape = RoundedCornerShape(5.dp)
        Box(Modifier.size(QUICK_ART).clip(shape).background(Color.Black.copy(alpha = 0x22 / 255f)), contentAlignment = Alignment.Center) {
            BasicText(glyph, style = icon(15f).copy(color = Ink.white(0x3A)))
            if (picture != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(picture).build(),
                    contentDescription = null,
                    imageLoader = images,
                    contentScale = if (pick is QuickPick.OfStation) ContentScale.Fit else ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = if (pick is QuickPick.OfStation) Modifier.fillMaxSize().padding(4.dp) else Modifier.fillMaxSize(),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            BasicText(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = system(14f, FontWeight.Medium).copy(color = Color.White))
            if (!subtitle.isNullOrEmpty()) {
                BasicText(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = system(12f).copy(color = Ink.secondary))
            }
        }
    }
}

/**
 * Example: "Album • Sting", "Playlist • 24 songs". It shows the kind, then the artist, or the number of songs in the
 * list.
 */
@Composable
private fun kindLine(kind: String, rest: String?): String =
    if (rest.isNullOrBlank()) kind else stringResource(R.string.Home_QuickKindJoin, kind, rest)

// ── the rows ──

/**
 * A row of records, which scrolls sideways inside the page. Its heading is above the covers, at the row's cover size.
 */
@Composable
internal fun AlbumRow(heading: String, albums: List<CloudAlbum>, collection: Collection, images: ImageLoader, onOpen: (CloudAlbum) -> Unit) {
    val caption = remember { AlbumCaption.of(ROW_ART) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText(heading, Modifier.padding(horizontal = 20.dp), style = display(19f).copy(color = Color.White))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(albums, key = { it.key }) { album -> AlbumTile(album, collection.cover(album), caption, images) { onOpen(album) } }
        }
    }
}

/** The computer's lists, drawn as a square like a record, at the row's cover size. */
@Composable
internal fun ListsRow(heading: String, lists: List<TrackList>, collection: Collection, images: ImageLoader, onOpen: (TrackList) -> Unit) {
    val art = ROW_ART.dp
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText(heading, Modifier.padding(horizontal = 20.dp), style = display(19f).copy(color = Color.White))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(lists, key = { it.id }) { list ->
                Column(
                    Modifier.width(art).clickable(remember { MutableInteractionSource() }, indication = null) { onOpen(list) },
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    val shape = RoundedCornerShape(10.dp)
                    Box(
                        Modifier.size(art).clip(shape).background(Color.Black.copy(alpha = 0x22 / 255f)).border(1.dp, Ink.cardBorder, shape),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(Glyph.playlists, style = icon(26f).copy(color = Ink.white(0x3A)))
                        collection.cover(list)?.let { picture ->
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(picture).build(),
                                contentDescription = null,
                                imageLoader = images,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    BasicText(listName(list.name), maxLines = 1, overflow = TextOverflow.Ellipsis, style = system(12f, FontWeight.Medium).copy(color = Color.White))
                }
            }
        }
    }
}
