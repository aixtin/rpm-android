package com.rpm.player.phone.stage

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.rpm.player.core.R
import com.rpm.player.core.art.ArtScene
import com.rpm.player.core.playback.AudioTap
import com.rpm.player.core.playback.NowPlaying
import com.rpm.player.core.playback.rememberPosition
import com.rpm.player.core.radio.Icy
import com.rpm.player.phone.PhoneApplication
import com.rpm.player.phone.design.GlassKey
import com.rpm.player.phone.design.Glyph
import com.rpm.player.phone.design.Ink
import com.rpm.player.phone.design.backdropSource
import com.rpm.player.phone.design.display
import com.rpm.player.phone.design.displayBold
import com.rpm.player.phone.design.icon
import com.rpm.player.phone.design.rememberBackdrop
import com.rpm.player.phone.library.SleeveSquare
import com.rpm.player.phone.ui.RecordDisc
import com.rpm.player.phone.ui.VinylGround
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The stage: the landscape now-playing screen, shown over the whole app while the phone is turned and something is
 * loaded. The iPhone's `StageView` (RPMUI/StageView.swift), in all its figures: the record is 1.08 times the frame
 * height and its centre is on the right edge, so it runs off the top, the bottom and the right like a record on a
 * turntable; the column is 24 from the left and 20 up from the foot; the title is 34 points; the chrome hides after
 * six seconds.
 *
 * **It is meant to be viewed from further away than a phone usually is**, propped on an amp or a desk. That is why
 * the type and the keys are large, and why the chrome hides by itself and comes back on a tap.
 *
 * Behind the words is the artist's photograph, cut so the face stays clear of the record and the column, or one of
 * the visualisers ([StageLook]) in its place.
 *
 * @param onPortrait the portrait key: back to the upright app without turning the phone.
 */
@Composable
fun StageView(now: NowPlaying, player: Player, images: ImageLoader, tap: AudioTap, onPortrait: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { StageSettings.of(context) }
    val look = settings.look.takeIf { it.isDrawn } ?: StageLook.Artwork
    val pinned = settings.pinned
    val scope = rememberCoroutineScope()
    val backdrop = rememberBackdrop()

    var chromeUp by remember { mutableStateOf(true) }
    var showingLooks by remember { mutableStateOf(false) }
    var idle by remember { mutableStateOf<Job?>(null) }
    var columnHeight by remember { mutableIntStateOf(0) }
    var picture by remember { mutableStateOf<Bitmap?>(null) }

    HideSystemBars()
    // Back takes the phone back to portrait, as the portrait key does. A stage is not a page that Back pops.
    BackHandler(onBack = onPortrait)

    // The countdown restarts on every call: each tap gives the chrome another six seconds. Pinned, there is none.
    fun hideChrome(asked: Boolean) {
        if (pinned || showingLooks) return
        // The countdown hides the chrome only when there is something worth seeing behind it: a running look or the
        // artist's photograph. A tap is different: a person who taps the text away has decided to see the ground.
        if (!asked && look == StageLook.Artwork && picture == null) return
        chromeUp = false
        idle?.cancel()
        idle = null
    }

    fun showChrome() {
        chromeUp = true
        idle?.cancel()
        idle = if (pinned) null else scope.launch {
            delay(IDLE_MS)
            hideChrome(asked = false)
        }
    }

    // A tap shows or hides the chrome. In the Uno app a tap only shows it; here, as on the iPhone, it toggles, because
    // a tap is all a person across the room can reach.
    fun tapped() {
        if (chromeUp && !pinned) hideChrome(asked = true) else showChrome()
    }

    LaunchedEffect(Unit) { showChrome() }

    val chrome by animateFloatAsState(if (chromeUp) 1f else 0f, tween(300, easing = LinearOutSlowInEasing), label = "chrome")

    BoxWithConstraints(Modifier.fillMaxSize().background(Ink.ground)) {
        val width = maxWidth
        val height = maxHeight
        val density = LocalDensity.current

        // What is behind the glass keys: the ground and the photograph. A visualiser draws on its own surface,
        // which a recording of the composition cannot see, so over a look the keys frost the ground.
        Box(Modifier.fillMaxSize().backdropSource(backdrop)) {
            VinylGround()
            if (look == StageLook.Artwork) {
                ArtistPicture(now, width.value.toDouble(), height.value.toDouble(), columnHeight / density.density.toDouble()) { picture = it }
            }
        }
        if (look != StageLook.Artwork) StageSurface(look, tap, Modifier.fillMaxSize())

        Deck(now, images, chromeUp, height.value)

        // A tap anywhere on the frame brings the chrome and the record back. It is a sibling under the chrome, not a
        // gesture around it, so the keys above it take their own touches first.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        val up = liftedAsTap()
                        if (up) tapped()
                    }
                },
        )

        // The scrim runs from left to right: the column is on the left and needs the dark, the record on the right
        // stays clear. It hides with the chrome, because it only exists to make the text readable.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = chrome }
                .background(
                    Brush.horizontalGradient(
                        0f to Ink.ground.copy(alpha = 0xCC / 255f),
                        0.45f to Ink.ground.copy(alpha = 0x73 / 255f),
                        1f to Ink.ground.copy(alpha = 0x1A / 255f),
                    ),
                ),
        )

        if (chrome > 0f) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = chrome }.windowInsetsPadding(WindowInsets.displayCutout)) {
                Keys(
                    backdrop,
                    look,
                    pinned,
                    onLooks = { showingLooks = true },
                    onPin = {
                        settings.pin(!pinned)
                        showChrome()
                    },
                    onPortrait = onPortrait,
                    modifier = Modifier.align(Alignment.TopStart),
                )
                StageColumn(
                    now,
                    player,
                    images,
                    onPressed = ::showChrome,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .onSizeChanged { columnHeight = it.height },
                )
            }
        }

        AnimatedVisibility(showingLooks, enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
            LookPicker(
                backdrop,
                showing = look,
                onPick = { picked ->
                    settings.choose(picked)
                    showingLooks = false
                    showChrome()
                },
                onClose = {
                    showingLooks = false
                    showChrome()
                },
            )
        }
    }
}

/**
 * Waits for the finger to lift, and says whether this was a tap: lifted within the system's tap time without having
 * moved more than the touch slop. A drag across the stage is not a tap, so paging or a slip of the finger does not
 * bring the chrome back.
 */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.liftedAsTap(): Boolean {
    val slop = viewConfiguration.touchSlop
    var start: androidx.compose.ui.geometry.Offset? = null
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull() ?: return false
        if (start == null) start = change.previousPosition
        if ((change.position - start).getDistance() > slop) return false
        if (!change.pressed) return true
    }
}

/** How long the chrome stays up when nothing is touched. */
private const val IDLE_MS = 6_000L

/** The record's diameter as a multiple of the frame's height. It runs off the top and the bottom on purpose. */
private const val DISC_DIAMETER = 1.08f

/** How far the record moves off when the chrome hides, as a multiple of its diameter: half clears the screen, the
 * rest covers the halo. */
private const val DISC_AWAY = 0.62f

/** The scale of the record when it has flown off, and the flight's length. The desktop's `Turntable.ExitScale` and
 * `ArtDisplay.TransitionSeconds`. */
private const val EXIT_SCALE = 0.24f
private const val FLIGHT_MS = 420

/** The text column: its distance from the left, from the foot, and the widest it may grow. */
private val COLUMN_INSET = 24.dp
private val COLUMN_FOOT = 20.dp
private val COLUMN_WIDEST = 520.dp

/** The side of the small album cover at the top of the column. */
private val COVER_EDGE = 64.dp

/**
 * The artist's photograph, filling the frame, cut so the face sits in the clear upper left: the record covers the
 * right from half a diameter in, and the column covers the foot. The record is counted even though it moves away
 * after a few seconds, because while it is shown a face behind it would be hidden. A station has no artist, so its
 * name is asked for, and the catalogues return what they have for it.
 *
 * The iPhone pages through up to eight of the artist's photographs with a swipe (`BackdropDeck`); this shows the
 * one the crop chose.
 */
@Composable
private fun ArtistPicture(now: NowPlaying, width: Double, height: Double, columnHeight: Double, onPicture: (Bitmap?) -> Unit) {
    val portraits = (LocalContext.current.applicationContext as PhoneApplication).portraits
    val artist = now.station?.name ?: now.artist
    // The column is measured including its foot gap. Until it is measured, nothing is fetched: a crop for the wrong
    // frame would still be shown as if it were right.
    val scene = remember(width, height, (columnHeight / 8).toInt()) {
        if (columnHeight <= 0 || width <= 0 || height <= 0) null else {
            val clearRight = (width - height * DISC_DIAMETER / 2) / width
            ArtScene.forClearLeft(width, height, clearTop = 0.0, clearRight = clearRight, clearBottom = (height - columnHeight) / height)
        }
    }
    // Keyed on the artist, not the track: every record of one artist shows the same photograph.
    val photo by produceState<ImageBitmap?>(null, artist.lowercase(), scene) {
        val cut = scene?.let { portraits.portrait(artist, now.album, it) }
        value = cut?.asImageBitmap()
        onPicture(cut)
    }
    Crossfade(photo, animationSpec = tween(350), label = "stage picture") { shown ->
        if (shown != null) Image(shown, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/**
 * The record on the deck. It turns only while it is on screen and sound is coming out, so it stops when playback
 * stalls. Its label is the album's sleeve.
 *
 * **A record's face changes only while it is out of sight**, the iPhone's `RecordChange` rule: a new album lifts the
 * disc off the deck (it shrinks into the corner and fades), the face is changed while it is away, and the disc drops
 * back. The record is decided by the album, not the track, so the next track of an album is the same record.
 */
@Composable
private fun Deck(now: NowPlaying, images: ImageLoader, chromeUp: Boolean, heightDp: Float) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val diameter = (heightDp * DISC_DIAMETER).dp
    var worn by remember { mutableStateOf<ImageBitmap?>(null) }
    val aloft = remember { Animatable(1f) }
    val away by animateFloatAsState(if (chromeUp) 0f else 1f, tween(300, easing = LinearOutSlowInEasing), label = "disc away")
    val wanted = now.album to now.cover?.keys?.firstOrNull()

    LaunchedEffect(wanted) {
        val edge = with(density) { (diameter * 0.33f).roundToPx() }.coerceAtLeast(64)
        val label = now.cover?.let { cover ->
            val request = ImageRequest.Builder(context).data(cover).size(edge).build()
            (images.execute(request) as? SuccessResult)?.image?.toBitmap()?.asImageBitmap()
        }
        if (aloft.value < 1f) aloft.animateTo(1f, tween(FLIGHT_MS, easing = FastOutSlowInEasing))
        worn = label
        aloft.animateTo(0f, tween(FLIGHT_MS, easing = FastOutSlowInEasing))
    }

    Box(Modifier.fillMaxSize()) {
        RecordDisc(
            diameter,
            Modifier
                // Placed by its centre, on the right edge and halfway down, and measured at its own size, which is
                // larger than the screen.
                .layout { measurable, constraints ->
                    val placed = measurable.measure(Constraints())
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        val x = constraints.maxWidth + (away * diameter.toPx() * DISC_AWAY)
                        placed.place((x - placed.width / 2f).toInt(), (constraints.maxHeight - placed.height) / 2)
                    }
                }
                .graphicsLayer {
                    val scale = 1f - (1f - EXIT_SCALE) * aloft.value
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - aloft.value
                },
            spinning = chromeUp && now.advancing,
            label = worn,
        )
    }
}

/** The keys for the screen and not for the music: the visualiser, the pin and the way back to portrait. */
@Composable
private fun Keys(
    backdrop: com.rpm.player.phone.design.Backdrop,
    look: StageLook,
    pinned: Boolean,
    onLooks: () -> Unit,
    onPin: () -> Unit,
    onPortrait: () -> Unit,
    modifier: Modifier,
) {
    val dim = Ink.white(0xB8)
    Row(modifier.padding(start = COLUMN_INSET, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val visualiser = stringResource(R.string.Rpm_Stage_Visualiser)
        GlassKey(backdrop, onLooks, Modifier.semantics { contentDescription = visualiser }) {
            BasicText(StageLook.Ribbon.glyph, style = icon(15f).copy(color = if (look == StageLook.Artwork) dim else Color.White))
        }
        // Lit while pinned, like the visualiser key beside it.
        val pin = stringResource(if (pinned) R.string.Rpm_Stage_PinOff else R.string.Rpm_Stage_PinOn)
        GlassKey(backdrop, onPin, Modifier.semantics { contentDescription = pin }) {
            BasicText(Glyph.pin, style = icon(15f).copy(color = if (pinned) Color.White else dim))
        }
        val portrait = stringResource(R.string.Rpm_Stage_Portrait)
        GlassKey(backdrop, onPortrait, Modifier.semantics { contentDescription = portrait }) {
            BasicText(Glyph.compress, style = icon(15f).copy(color = dim))
        }
    }
}

/** The column of album cover, track details and transport keys, down the left and along the foot, away from the record. */
@Composable
private fun StageColumn(now: NowPlaying, player: Player, images: ImageLoader, onPressed: () -> Unit, modifier: Modifier) {
    val station = now.station
    val song = station?.said as? Icy.Said.Song
    // A station announces what it is playing itself: the song over the station's name. The player sheet reads it the
    // same way, and the two screens must agree.
    val title = when {
        station != null -> song?.title ?: station.name
        else -> now.title.ifEmpty { stringResource(R.string.Rpm_NowPlaying_Nothing) }
    }
    val said = if (station != null) song?.artist ?: station.name else now.artist
    Column(
        modifier
            .widthIn(max = COLUMN_WIDEST)
            .padding(start = COLUMN_INSET, end = COLUMN_INSET, bottom = COLUMN_FOOT),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Only identifies the album: the turning record on the right is this screen's picture.
        Box(Modifier.padding(bottom = 12.dp)) { SleeveSquare(now.cover, COVER_EDGE, images, radius = 8.dp, glyphSize = 22f) }
        // The one screen whose text is sized to be read from a distance.
        BasicText(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = displayBold(34f).copy(color = Color.White))
        BasicText(said, maxLines = 1, overflow = TextOverflow.Ellipsis, style = display(18f).copy(color = Ink.white(0xB3)))
        BasicText(now.album, Modifier.padding(top = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = display(13f).copy(color = Ink.white(0x77)))
        Box(Modifier.padding(top = 14.dp)) { Needle(now, player) }
        Transport(now, player, onPressed)
    }
}

/**
 * The seek bar and its clocks, as the iPhone's `PlayerNeedle` draws them, with the live line aligned to the column
 * and not centred: the stage's column touches the left edge, and a centred line would sit alone in its middle. While
 * the bar is dragged it follows the finger, and the player seeks once, where the finger lifts.
 */
@Composable
private fun Needle(now: NowPlaying, player: Player) {
    val position by rememberPosition(now)
    var scrubbing by remember { mutableStateOf<Long?>(null) }
    val shown = scrubbing ?: position
    val clocks = display(11f).copy(color = Ink.white(0x88))
    // What is being waited for sits over the right end of the bar, outside the column, so it never moves the keys.
    val busy = now.playing && !now.advancing && !now.failed

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(20.dp)) {
            if (now.station == null) {
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(now.durationMs) {
                            if (now.durationMs <= 0) return@pointerInput
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                fun at(x: Float) = ((x / size.width).coerceIn(0f, 1f) * now.durationMs).toLong()
                                scrubbing = at(down.position.x)
                                do {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    scrubbing = at(change.position.x)
                                    change.consume()
                                } while (true)
                                scrubbing?.let(player::seekTo)
                                scrubbing = null
                            }
                        },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    val through = if (now.durationMs > 0) (shown.toFloat() / now.durationMs).coerceIn(0f, 1f) else 0f
                    val played = maxWidth * through
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(percent = 50)).background(Ink.white(0x2E)))
                    Box(Modifier.size(played, 3.dp).clip(RoundedCornerShape(percent = 50)).background(Color.White))
                    Box(Modifier.offset(x = (played - 1.dp).coerceAtLeast(0.dp)).size(2.dp, 14.dp).clip(RoundedCornerShape(percent = 50)).background(Color.White))
                }
            }
            if (busy) {
                BasicText(
                    stringResource(R.string.Rpm_Playback_Buffering),
                    Modifier.align(Alignment.TopEnd).offset(y = (-8).dp),
                    maxLines = 1,
                    style = clocks,
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            if (now.station != null) {
                BasicText(stringResource(R.string.Radio_OnAir), style = clocks)
            } else {
                BasicText(clock(shown), style = clocks)
                Spacer(Modifier.weight(1f))
                if (now.durationMs > 0) BasicText("-" + clock(now.durationMs - shown), style = clocks)
            }
        }
    }
}

private fun clock(ms: Long): String {
    val whole = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(whole / 60, whole % 60)
}

/**
 * Previous, play, next and stop, left-aligned under the column. The row is moved back by 16 so the tip of the
 * rewind arrow, not its invisible circle, lines up with the start of the bar. Stop is a key of its own here, where
 * there is room; every key gives the chrome another six seconds.
 */
@Composable
private fun Transport(now: NowPlaying, player: Player, onPressed: () -> Unit) {
    val onStation = now.station != null
    Row(
        Modifier.offset(x = (-16).dp).padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Key(Glyph.backward, 16f, Ink.white(0xB8), stringResource(R.string.Transport_Previous), enabled = !onStation) {
            player.seekToPrevious()
            onPressed()
        }
        Key(if (now.playing) Glyph.pause else Glyph.play, 20f, Color.White, stringResource(R.string.Transport_PlayPause), enabled = true) {
            toggle(player)
            onPressed()
        }
        Key(Glyph.forward, 16f, Ink.white(0xB8), stringResource(R.string.Transport_Next), enabled = !onStation && player.hasNextMediaItem()) {
            player.seekToNext()
            onPressed()
        }
        Key(Glyph.stop, 16f, Ink.white(0xB8), stringResource(R.string.Transport_Stop), enabled = true) {
            // The queue is emptied, as the iPhone's stop does. Nothing is loaded then, and the phone turns back.
            player.pause()
            player.clearMediaItems()
        }
    }
}

/** Play or pause. From a stopped player, or at the end of a queue, playback starts again, as a media key does. */
@OptIn(UnstableApi::class)
private fun toggle(player: Player) {
    Util.handlePlayPauseButtonAction(player)
}

@Composable
private fun Key(glyph: String, size: Float, color: Color, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else 0.3f)
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(glyph, style = icon(size).copy(color = color))
    }
}
