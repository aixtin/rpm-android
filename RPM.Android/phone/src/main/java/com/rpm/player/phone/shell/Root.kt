package com.rpm.player.phone.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.rpm.player.core.R
import com.rpm.player.core.Services
import com.rpm.player.core.library.LibraryState
import com.rpm.player.core.cloud.CloudTrack
import com.rpm.player.core.cloud.Shelf
import com.rpm.player.core.library.ArtistWallItem
import com.rpm.player.core.library.MarkedSongs
import com.rpm.player.core.library.CloudAlbum
import com.rpm.player.core.library.GenreWallItem
import com.rpm.player.core.library.TrackList
import com.rpm.player.core.library.listName
import com.rpm.player.core.playback.Queued
import com.rpm.player.core.playback.TrackRef
import com.rpm.player.core.playback.tune
import com.rpm.player.core.playback.playQueue
import com.rpm.player.core.playback.startRadio
import com.rpm.player.core.playback.queueOf
import com.rpm.player.core.playback.rememberNowPlaying
import com.rpm.player.phone.PhoneSettings
import com.rpm.player.phone.design.GlassButton
import com.rpm.player.phone.design.Ink
import com.rpm.player.phone.design.backdropSource
import com.rpm.player.phone.design.rememberBackdrop
import com.rpm.player.phone.design.system
import com.rpm.player.phone.home.HomeActions
import com.rpm.player.phone.home.HomePage
import com.rpm.player.phone.library.AlbumPage
import com.rpm.player.phone.library.ArtistPage
import com.rpm.player.phone.library.GenrePage
import com.rpm.player.phone.radio.RadioPage
import com.rpm.player.phone.player.NowPlayingSheet
import com.rpm.player.phone.library.LibraryPage
import com.rpm.player.phone.library.PlaylistPage
import com.rpm.player.phone.library.QueuePage
import com.rpm.player.phone.player.rememberQueue
import com.rpm.player.phone.signin.SignIn
import com.rpm.player.phone.signin.SignInScreen
import com.rpm.player.phone.ui.RecordSpinner
import com.rpm.player.phone.ui.VinylGround

/**
 * The first screen of the app, as the iPhone's `RootView`. When signed out, the sign-in gate is shown. When
 * signed in, a wait screen is shown while the library is read. Then the app is shown.
 *
 * The session and the library are read each time either one changes. The state is never stored. So a sign-out
 * from anywhere shows the gate, and a library that finishes loading shows the app. Whichever happens first,
 * the screen follows it.
 */
@Composable
fun Root(services: Services, settings: PhoneSettings, signIn: SignIn, controller: Player?, sittingFrom: Int) {
    val session by services.auth.session.collectAsStateWithLifecycle()
    val library by services.library.state.collectAsStateWithLifecycle()

    val shown = library
    when {
        session == null -> SignInScreen(signIn)
        shown is LibraryState.Ready -> Shell(services, settings, shown, controller, sittingFrom)
        shown is LibraryState.Failed -> Failed(onRetry = services.library::retry)
        else -> Waiting(stringResource(R.string.Rpm_Library_Reading))
    }
}

/**
 * The app, once there is something to show: a page, with the chrome floating over its bottom edge. The
 * chrome's glass blurs the page under it, so the page is the backdrop. Each page is told how tall the chrome
 * is, so its last row can scroll clear of it.
 *
 * The library and home each keep their own stack of opened records and lists ([Step]). A step stores a name,
 * not the data, so the page is drawn from the collection on every pass. When the collection is read again
 * under an open page, the page redraws with the new rows. This is the iPhone's `LibraryStep`. A page that is
 * opened slides in from the right, as a push does on iPhone. Each page keeps its own saved state. So the wall
 * stays where the user left it when the record is put away.
 *
 * The app opens on home, as on iPhone.
 */
@Composable
private fun Shell(services: Services, settings: PhoneSettings, ready: LibraryState.Ready, controller: Player?, sittingFrom: Int) {
    val backdrop = rememberBackdrop()
    val density = LocalDensity.current
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var opened by rememberSaveable(stateSaver = Step.stack) { mutableStateOf(listOf<Step>()) }
    var homeOpened by rememberSaveable(stateSaver = Step.stack) { mutableStateOf(listOf<Step>()) }
    var playerUp by rememberSaveable { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(0.dp) }
    val pages = rememberSaveableStateHolder()
    val now = rememberNowPlaying(controller)
    val playing = now?.mediaId?.let { TrackRef.parse(it)?.rpmId }
    val collection = ready.collection
    val images = services.images
    val queue = rememberQueue(controller, collection)
    // The marked songs exist only on the person's own shelf. Another account's songs cannot be marked by this one.
    val marked = remember(collection) { if (collection.shelf == Shelf.Own) MarkedSongs.of(collection.albums) else null }
    val heard by services.radio.listens.top.collectAsStateWithLifecycle()
    val rules by services.radio.rules.collectAsStateWithLifecycle()
    // The station rules are read once at start, and again at most once an hour. Home's stations are filtered through
    // them.
    LaunchedEffect(Unit) { services.radio.refreshRules() }
    val stations = remember(heard, rules) { rules.shown(heard.map { it.station }).take(HOME_STATIONS) }

    // One record page. It is used for whichever tab's stack the record was opened on.
    @Composable
    fun record(album: CloudAlbum, onBack: () -> Unit) {
        val queue = { queueOf(collection.shelf, album, collection.cover(album), collection.disc(album)) }
        AlbumPage(
            album,
            collection.cover(album),
            images,
            playing,
            bottomRoom = chrome,
            onPlay = { at -> controller?.playQueue(queue(), at) },
            onShuffle = { controller?.playQueue(queue().shuffled(), 0) },
            onBack = onBack,
        )
    }

    // The marked songs' page. This list has no picture, and its order is the order of the marks.
    @Composable
    fun songs(name: String, tracks: List<CloudTrack>, onBack: () -> Unit) {
        val queue = { tracks.map { queued(collection, it) } }
        PlaylistPage(
            name,
            tracks,
            missing = 0,
            blurb = null,
            picture = null,
            coverOf = { track -> collection.albumOf[track.rpmId]?.let { collection.cover(it) } },
            images = images,
            playing = playing,
            bottomRoom = chrome,
            onPlay = { at -> controller?.playQueue(queue(), at) },
            onShuffle = { controller?.playQueue(queue().shuffled(), 0) },
            onBack = onBack,
        )
    }

    // One list's page. It is the same for either stack.
    @Composable
    fun playlist(list: TrackList, onBack: () -> Unit) {
        val queue = { list.tracks.map { queued(collection, it) } }
        PlaylistPage(
            listName(list.name),
            list.tracks,
            list.missing,
            list.blurb,
            collection.cover(list),
            coverOf = { track -> collection.albumOf[track.rpmId]?.let { collection.cover(it) } },
            images,
            playing,
            bottomRoom = chrome,
            onPlay = { at -> controller?.playQueue(queue(), at) },
            onShuffle = { controller?.playQueue(queue().shuffled(), 0) },
            onBack = onBack,
        )
    }

    // One artist's page. It is the same for either stack. A record opened from it goes on the same stack.
    @Composable
    fun artist(artist: ArtistWallItem, push: (Step) -> Unit, onBack: () -> Unit) {
        ArtistPage(
            artist,
            collection,
            images,
            services.dossiers,
            services.worldPlays,
            settings.trackGrouping,
            onGrouping = settings::changeTrackGrouping,
            playing = playing,
            bottomRoom = chrome,
            onPlay = { tracks, at -> controller?.playQueue(tracks.map { queued(collection, it) }, at) },
            onOpen = { push(Step.Record(it.key)) },
            onBack = onBack,
        )
    }

    // One genre's page; what is opened from it goes on the same stack.
    @Composable
    fun genre(genre: GenreWallItem, push: (Step) -> Unit, onBack: () -> Unit) {
        GenrePage(
            genre,
            ready,
            images,
            services.artwork,
            bottomRoom = chrome,
            onOpen = { push(Step.Record(it.key)) },
            onPlay = { tracks -> controller?.playQueue(tracks.map { queued(collection, it) }, 0) },
            onArtist = { push(Step.Artist(it.name)) },
            onList = { push(Step.Playlist(it.id)) },
            onGenre = { push(Step.Genre(it)) },
            onBack = onBack,
        )
    }

    // The page on top of a stack, or [root] when nothing is pushed. If a step's record, list or artist is no
    // longer in the collection after a re-read, [root] is shown instead of an empty page.
    @Composable
    fun page(step: Step?, push: (Step) -> Unit, onBack: () -> Unit, root: @Composable () -> Unit) {
        val album = (step as? Step.Record)?.let { collection.album(it.key) }
        val list = (step as? Step.Playlist)?.let { collection.list(it.id) }
        val act = (step as? Step.Artist)?.let { ready.walls.artist(it.name) }
        val kind = (step as? Step.Genre)?.let { ready.walls.genre(it.name) }
        when {
            album != null -> record(album, onBack)
            list != null -> playlist(list, onBack)
            act != null -> artist(act, push, onBack)
            kind != null -> genre(kind, push, onBack)
            step == Step.Queue -> QueuePage(queue, sittingFrom, controller, images, bottomRoom = chrome, onBack = onBack)
            step == Step.Marked && marked != null -> songs(stringResource(R.string.Playlists_FavoriteTracks), marked, onBack)
            else -> root()
        }
    }

    val homeActions = HomeActions(
        onOpen = { homeOpened = homeOpened + Step.Record(it.key) },
        onOpenList = { homeOpened = homeOpened + Step.Playlist(it.id) },
        onPlay = { tracks -> controller?.playQueue(tracks.map { queued(collection, it) }, 0) },
        onRadio = { track -> controller?.startRadio(queued(collection, track)) },
        onTune = { station ->
            controller?.let {
                it.tune(station, rules.logoStamp, station.detail(java.util.Locale.getDefault()))
                services.scope.launch { services.radio.browser.click(station.uuid) }
            }
        },
    )

    // The listening ended, because the queue was stopped or played out. The player comes down. This reads the
    // player's state, rather than being told by whoever pressed stop, because there is more than one way to reach that
    // state.
    LaunchedEffect(now == null) { if (now == null) playerUp = false }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().backdropSource(backdrop)) {
            when (tab) {
                Tab.Library -> AnimatedContent(
                    targetState = opened,
                    transitionSpec = { pushOrPop() },
                    label = "library stack",
                ) { stack ->
                    val step = stack.lastOrNull()
                    pages.SaveableStateProvider(step?.saved ?: "library") {
                        page(step, push = { opened = opened + it }, onBack = { opened = opened.dropLast(1) }) {
                            LibraryPage(
                                ready,
                                images,
                                settings,
                                bottomRoom = chrome,
                                artwork = services.artwork,
                                onOpen = { opened = opened + Step.Record(it.key) },
                                onOpenList = { opened = opened + Step.Playlist(it.id) },
                                onOpenArtist = { opened = opened + Step.Artist(it.name) },
                                onOpenGenre = { opened = opened + Step.Genre(it.name) },
                                queue = queue,
                                onOpenQueue = { opened = opened + Step.Queue },
                                marked = marked,
                                onOpenMarked = { opened = opened + Step.Marked },
                                playing = playing,
                                // A song found in search plays its record from that song. A single song would be a
                                // queue of one, and playback would stop after it. Nobody who searches for a song
                                // wants that. The iPhone app's `play(track)` does the same.
                                onPlaySong = { hit ->
                                    val album = hit.album
                                    val at = album.tracks.indexOfFirst { it.rpmId == hit.track.rpmId }
                                    if (at >= 0) controller?.playQueue(queueOf(collection.shelf, album, collection.cover(album), collection.disc(album)), at)
                                },
                            )
                        }
                    }
                }
                // Home has its own stack. A record opened here slides in over home and back out to it. The library's
                // stack is not changed by a trip that began on home.
                Tab.Home -> AnimatedContent(targetState = homeOpened, transitionSpec = { pushOrPop() }, label = "home stack") { stack ->
                    val step = stack.lastOrNull()
                    pages.SaveableStateProvider("home:" + (step?.saved ?: "")) {
                        page(step, push = { homeOpened = homeOpened + it }, onBack = { homeOpened = homeOpened.dropLast(1) }) {
                            HomePage(ready, stations, rules.logoStamp, images, bottomRoom = chrome, actions = homeActions)
                        }
                    }
                }
                Tab.Radio -> RadioPage(
                    services.radio,
                    images,
                    tuned = now?.station?.logo?.uuid,
                    said = now?.station?.said,
                    bottomRoom = chrome,
                    onTune = homeActions.onTune,
                )
                else -> VinylGround()
            }
        }
        Chrome(
            backdrop,
            showing = tab,
            onShow = { tab = it },
            miniPlayer = if (now != null && controller != null) {
                { MiniPlayer(now, controller, images, backdrop, onOpen = { playerUp = true }) }
            } else {
                null
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { chrome = with(density) { it.height.toDp() } },
        )
        // Over everything, the tabs and the mini player included.
        if (playerUp && now != null && controller != null) {
            val album = playing?.let { collection.albumOf[it] }
            NowPlayingSheet(
                now,
                controller,
                images,
                // The records are on the library's stack, so the page is pushed there.
                onAlbum = album?.let { { tab = Tab.Library; opened = listOf(Step.Record(it.key)) } },
                onClosed = { playerUp = false },
            )
        }
    }
}

/**
 * The number of stations that home shows: the radio card and the quick selection. This is the same as the TV app's
 * seven.
 */
private const val HOME_STATIONS = 7

/**
 * A page that is opened slides in from the right, over the page it was opened from. That page moves back by a
 * third. When the page is put away, it slides back out over that page. This matches a push and a pop on the
 * iPhone's stacks.
 */
private fun AnimatedContentTransitionScope<List<Step>>.pushOrPop(): ContentTransform =
    if (targetState.size >= initialState.size) {
        slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 3 }
    } else {
        (slideInHorizontally { -it / 3 } togetherWith slideOutHorizontally { it }).apply { targetContentZIndex = -1f }
    }

/** One song to queue, with the names, cover and disc of the record it is on. */
private fun queued(collection: com.rpm.player.core.library.Collection, track: CloudTrack): Queued {
    val album = collection.albumOf[track.rpmId]
    return Queued(
        collection.shelf,
        track,
        album?.album ?: track.album.orEmpty(),
        album?.artist ?: track.artist.orEmpty(),
        album?.let { collection.cover(it) },
        album?.let { collection.disc(it) },
    )
}

/** The wait screen: a spinning glass record, and what is being waited for. The iPhone's `waiting` view is the same. */
@Composable
private fun Waiting(what: String?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        VinylGround()
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            RecordSpinner(120.dp)
            if (what != null) BasicText(what, style = system(13f).copy(color = Ink.white(0x99)))
        }
    }
}

/** The library could not be read. It shows the same two texts as the TV app, and a key to read it again. */
@Composable
private fun Failed(onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        VinylGround()
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            BasicText(stringResource(R.string.Rpm_Library_Error), style = system(15f).copy(color = Ink.white(0xCC)))
            GlassButton(onClick = onRetry, stretches = false) { style ->
                BasicText(stringResource(R.string.Rpm_Library_Retry), style = style)
            }
        }
    }
}
